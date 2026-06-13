package com.rate.server.routes

import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.ingestion.handler.CatalogSeeder
import com.rate.sdk.ingestion.network.SectionBenefitInput
import com.rate.sdk.ingestion.network.SeedCatalogRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.io.File

/**
 * DEV-ONLY one-time catalog seed from the `/data` CSV exports. Reads each file from `$DATA_DIR`
 * (default `data`, relative to the server's working dir), maps it to its [SeedCatalogRequest] slot,
 * and runs the pure-KMP `CsvCatalogParser` + [CatalogSeeder] (`force = true` → CSV is authoritative).
 *
 * Mounted ONLY when `AEGIS_DEV_PROFILE=true`. POST `/api/dev/seed-catalog` → [com.rate.sdk.ingestion.model.ImportSummary].
 */
fun Route.devSeedRoutes(seeder: CatalogSeeder) {
    post("/api/dev/seed-catalog") {
        val dir = System.getenv("DATA_DIR") ?: "data"
        fun read(rel: String): String? = File(dir, rel).takeIf { it.isFile }?.readText()

        val ghi = "Product Benefit Table EE GHI - 2026 05 12"
        val paci = "PBT Employer Employee Benefit (PA CI) 2026 05 12"

        // Per-section benefit files → Covers, each linked to its PBT Section number.
        // (sectionNumber, human name, file path). Files that aren't present are dropped.
        // Section numbers follow the canonical PBT Index (Section 3 = Hospital Cash,
        // Section 4 = Heart Cover, Section 5 = Loan EMI). The D1-HC file's own header mislabels
        // itself "Section 4" but the PBT Index is authoritative, so Hospital Cash → section 3.
        val sectionBenefits = listOf(
            Triple("1", "Personal Accident", "D1-PA.csv"),
            Triple("2", "Critical Illness", "D1-CI.csv"),
            Triple("3", "Hospital Cash", "D1-HC.csv"),
            Triple("5", "EMI Cover", "D1-Loan EMI Cover.csv"),
            Triple("9", "OPD", "D2-OPD.csv"),
            // GHI OPD section (Section 9): the dedicated NEW OPD TBD benefit file.
            Triple("9", "OPD (GHI)", "$ghi/NEW OPD TBD.csv"),
            // PA-CI PBT PA benefit file stays a per-section cover source (Section 1).
            Triple("1", "PA Group", "$paci/PBT PA.csv"),
        ).mapNotNull { (num, name, rel) ->
            read(rel)?.let { SectionBenefitInput(sectionNumber = num, name = name, csv = it) }
        }

        // The GROUP EE annexure carries three structured sub-tables (sublimits / vaccinations /
        // devices); all three fields point at the same GHI Annexure.csv (the seeder parses each
        // sub-table independently), so each entity remains swappable to its own source later.
        val ghiAnnexure = read("$ghi/Annexure.csv")

        val req = SeedCatalogRequest(
            productLine = ProductLine.GROUP,
            force = true,
            pbtIndexCsv = read("PBT Index.csv"),
            groupProductCsv = read("$ghi/Employer Employee.csv") ?: read("Indemnity- NEW ADDITION.csv"),
            // Base benefit schedule = the GHI health base covers (the "PART 1- BASE COVERS" block of
            // Employer Employee.csv), NOT the PA-CI PBT PA file (which is a PA section cover source).
            baseBenefitScheduleCsv = read("$ghi/Employer Employee.csv"),
            flatCiListCsv = read("List of CI 101 and 92.csv"),
            flatCiListCode = "CI_101",
            tieredCiListCsv = read("$paci/CI List.csv") ?: read("$ghi/CI List.csv"),
            waitingPeriodsCsv = read("$ghi/Waiting Periods.csv") ?: read("$paci/Waiting Periods.csv"),
            eligibilityCsv = read("$paci/Eligibility.csv") ?: read("$ghi/Eligibility.csv"),
            ppdPtdCsv = read("$paci/PPD PTD Tables.csv"),
            dayCareCsv = read("$ghi/Day Care List.csv"),
            consumablesCsv = read("$ghi/Consumables List.csv"),
            healthCheckupCsv = read("$ghi/Health Check Up Packages.csv"),
            annexureCsv = read("Annexure.csv"),
            chronicOpdCsv = ghiAnnexure,
            vendorKycCsv = read("New_Vendor_KYC_Form/KYC.csv") ?: read("KYC.csv"),
            surgicalSublimitCsv = ghiAnnexure,
            vaccinationCatalogCsv = ghiAnnexure,
            medicalDeviceCatalogCsv = ghiAnnexure,
            sectionBenefitCsvs = sectionBenefits,
        )
        val summary = seeder.seed(req, actor = "owner-seed")
        call.respond(HttpStatusCode.OK, summary)
    }
}
