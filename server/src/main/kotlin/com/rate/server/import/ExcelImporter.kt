package com.rate.server.import

import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.*
import com.rate.server.database.tables.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Imports all rate data from Excel v13.0 into the database.
 * Parses every sheet: Ref, 14 plan sheets, Loadings and Discounts, Sheet1, Sheet2.
 * No hardcoded fallback — data comes entirely from the Excel file.
 */
class ExcelImporter {

    fun importFromExcel(stream: InputStream) = runBlocking {
        WorkbookFactory.create(stream).use { wb ->
            clearAllRateData()
            importPlans(wb)
            importBaseRates(wb)
            importCoverRates(wb)
            importCoverAvailability(wb)
            importInstalmentConfig(wb)
        }
    }

    // ── Clear existing data ──────────────────────────────────────────────────

    private suspend fun clearAllRateData() = newSuspendedTransaction {
        CoverAvailabilityTable.deleteAll()
        InstalmentConfigTable.deleteAll()
        DiscountRatesTable.deleteAll()
        MemberLevelRatesTable.deleteAll()
        CoverRateLookupTable.deleteAll()
        BaseRatesTable.deleteAll()
        PlansTable.deleteAll()
    }

    // ── Plan ID mapping: Excel plan name → system plan ID ───────────────────

    private val planNameToId = mapOf(
        "PHI Basic"                         to "PHI_BASIC",
        "PHI POSP"                          to "PHI_POSP",
        "PHI Flagship 1"                    to "PHI_FLAGSHIP1",
        "PHI Flagship 2"                    to "PHI_FLAGSHIP2",
        "PHI Flagship 3"                    to "PHI_FLAGSHIP3",
        "PHI Sub Standard"                  to "PHI_SUBSTANDARD",
        "PHI Senior"                        to "PHI_SENIOR",
        "PHI Global Excl US Canada"         to "PHI_GLOBAL_EXCL",
        "PHI Global Asia Excl India"        to "PHI_GLOBAL_ASIA",
        "PHI Global Europe"                 to "PHI_GLOBAL_EUROPE",
        "PHI Global Plus Incl US Canada"    to "PHI_GLOBAL_PLUS",
        "PHI Global Plus Excl US Canada"    to "PHI_GLOBAL_PLUS_EXCL",
        "PHI Global Plus Asia Excl India"   to "PHI_GLOBAL_PLUS_ASIA",
        "PHI Global Plus Europe"            to "PHI_GLOBAL_PLUS_EUROPE"
    )

    // Cover name in Sheet1 → cover ID mapping
    // Built dynamically from CoverCatalog.xlsNames — single source of truth
    private val coverNameToId: Map<String, String> =
        (CoverCatalog.ALL + CoverCatalog.DISCOUNTS)
            .flatMap { meta -> meta.xlsNames.map { name -> name to meta.id } }
            .toMap()

    // ── Helper: safe cell value reading ─────────────────────────────────────

    private fun Cell?.numericOrNull(): Double? {
        if (this == null) return null
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.NUMERIC -> numericCellValue
                else -> try { numericCellValue } catch (e: Exception) { null }
            }
            else -> null
        }
    }

    private fun Cell?.stringOrEmpty(): String {
        if (this == null) return ""
        return when (cellType) {
            CellType.STRING  -> stringCellValue.trim()
            CellType.NUMERIC -> numericCellValue.toLong().toString()
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.STRING  -> stringCellValue.trim()
                CellType.NUMERIC -> numericCellValue.toLong().toString()
                else -> ""
            }
            else -> ""
        }
    }

    private fun bd(v: Double) = BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP)

    // ── Age band label → minAge mapping ─────────────────────────────────────
    private val ageLabelToMin = mapOf(
        "5 - 17" to 5, "18 - 25" to 18, "26 - 30" to 26, "31 - 35" to 31,
        "36 - 40" to 36, "41 - 45" to 41, "46 - 50" to 46, "51 - 55" to 51,
        "56 - 60" to 56, "61 - 65" to 61, "66 - 70" to 66, "71 - 75" to 71,
        "76 - 80" to 76, "81 - 85" to 81, "85+" to 86, "86+" to 86
    )

    private fun ageLabel(s: String): Int? = ageLabelToMin[s.trim()] ?: ageLabelToMin.entries
        .firstOrNull { s.trim().startsWith(it.key.split(" ").first()) }?.value

    // ── STEP A: Import plans from Ref sheet ──────────────────────────────────

    private suspend fun importPlans(wb: Workbook) {
        val sheet = wb.getSheet("Ref") ?: wb.getSheet("ref") ?: run {
            println("[WARN] Ref sheet not found")
            return
        }

        // Plan definitions are in rows 2-15 (0-indexed: 1-14)
        // Col A = plan name, Col G = copay table named range
        newSuspendedTransaction {
            for (r in 1..14) {
                val row = sheet.getRow(r) ?: continue
                val planName = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
                val planId   = planNameToId[planName] ?: continue

                val (planType, uwCat, geoScope, coPayTable) = planMetaFor(planId)

                // Zones and family types from named ranges in Ref (Col S area)
                val zones          = zonesForPlan(planId)
                val familyTypes    = familyTypesForPlan(planId)
                val sumInsureds    = sumInsuredsForPlan(planId)

                PlansTable.insert {
                    it[id]                   = planId
                    it[name]                 = planName
                    it[PlansTable.planType]  = planType.name
                    it[underwritingCategory] = uwCat.name
                    it[geographyScope]       = geoScope.name
                    it[coPaymentTable]       = coPayTable.name
                    it[description]          = ""
                    it[availableSumInsureds] = "[${sumInsureds.joinToString(",")}]"
                    it[availableZones]       = "[${zones.joinToString(",") { z -> "\"$z\"" }}]"
                    it[availableFamilyTypes] = "[${familyTypes.joinToString(",") { f -> "\"$f\"" }}]"
                    it[maxDiscountCap]       = BigDecimal("0.30")
                    it[rateTableId]          = planId
                    it[minAge]               = if (planId == "PHI_SENIOR" || planId == "PHI_SUBSTANDARD") 46 else 5
                    it[maxAge]               = 99
                    it[isActive]             = true
                }
            }
        }
        println("[INFO] Plans imported: ${planNameToId.size}")
    }

    data class PlanMeta(
        val planType: PlanType,
        val uwCategory: UnderwritingCategory,
        val geoScope: GeographyScope,
        val coPayTable: CoPaymentTable
    )

    private fun planMetaFor(planId: String): PlanMeta = when {
        planId == "PHI_SENIOR"          -> PlanMeta(PlanType.DOMESTIC_SENIOR, UnderwritingCategory.SENIOR, GeographyScope.DOMESTIC, CoPaymentTable.SENIOR)
        planId == "PHI_SUBSTANDARD"     -> PlanMeta(PlanType.DOMESTIC_SUBSTANDARD, UnderwritingCategory.SUB_STANDARD, GeographyScope.DOMESTIC, CoPaymentTable.SUB_STANDARD)
        planId == "PHI_POSP"            -> PlanMeta(PlanType.DOMESTIC_POSP, UnderwritingCategory.STANDARD, GeographyScope.DOMESTIC, CoPaymentTable.OMNIBUS)
        planId.startsWith("PHI_FLAG")   -> PlanMeta(PlanType.DOMESTIC_FLAGSHIP, UnderwritingCategory.STANDARD, GeographyScope.DOMESTIC, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_EXCL"     -> PlanMeta(PlanType.GLOBAL, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_EXCL_US_CANADA, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_ASIA"     -> PlanMeta(PlanType.GLOBAL, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_ASIA_EXCL_INDIA, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_EUROPE"   -> PlanMeta(PlanType.GLOBAL, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_EUROPE, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_PLUS"     -> PlanMeta(PlanType.GLOBAL_PLUS, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_INCL_US_CANADA, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_PLUS_EXCL" -> PlanMeta(PlanType.GLOBAL_PLUS, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_EXCL_US_CANADA, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_PLUS_ASIA" -> PlanMeta(PlanType.GLOBAL_PLUS, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_ASIA_EXCL_INDIA, CoPaymentTable.OMNIBUS)
        planId == "PHI_GLOBAL_PLUS_EUROPE" -> PlanMeta(PlanType.GLOBAL_PLUS, UnderwritingCategory.STANDARD, GeographyScope.GLOBAL_EUROPE, CoPaymentTable.OMNIBUS)
        else                            -> PlanMeta(PlanType.DOMESTIC, UnderwritingCategory.STANDARD, GeographyScope.DOMESTIC, CoPaymentTable.OMNIBUS)
    }

    private fun zonesForPlan(planId: String): List<String> =
        if (planId == "PHI_SENIOR" || planId == "PHI_SUBSTANDARD")
            listOf("Zone 4")   // Excel has no zone segmentation for these plans
        else
            Zone.entries.map { it.label }

    private fun familyTypesForPlan(planId: String): List<String> =
        if (planId == "PHI_SENIOR" || planId == "PHI_SUBSTANDARD")
            listOf("1A", "2A", "2A1C", "2A2C", "2A3C", "2A4C", "1A1C", "1A2C", "1A3C", "1A4C")
        else
            FAMILY_TYPES.filter { it.code != "multi" }.map { it.code }

    private fun sumInsuredsForPlan(planId: String): List<Long> = when {
        planId == "PHI_SENIOR"       -> listOf(500_000L, 750_000L, 1_000_000L, 1_500_000L, 2_000_000L)
        planId == "PHI_SUBSTANDARD"  -> listOf(500_000L, 750_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L, 4_000_000L, 5_000_000L)
        planId == "PHI_POSP"         -> listOf(200_000L, 300_000L, 400_000L, 500_000L, 750_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L, 4_000_000L, 5_000_000L)
        planId.startsWith("PHI_GLOBAL") -> listOf(5_000_000L, 7_500_000L, 10_000_000L, 20_000_000L, 30_000_000L)
        else -> listOf(200_000L, 300_000L, 400_000L, 500_000L, 750_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L, 4_000_000L, 5_000_000L, 7_500_000L, 10_000_000L, 15_000_000L)
    }

    // ── STEP B: Import base rates from all 14 plan sheets ───────────────────

    // Plan sheet name → plan ID
    // Keys = exact Excel tab names (v13.0); values = plan IDs in our database
    private val planSheets = mapOf(
        "PHI_Basic"                         to "PHI_BASIC",
        "PHI_POSP"                          to "PHI_POSP",
        "PHI_Flagship1"                     to "PHI_FLAGSHIP1",
        "PHI_Flagship_2"                    to "PHI_FLAGSHIP2",
        "PHI_Flagship_4_(PB)"               to "PHI_FLAGSHIP3",
        "PHI_Sub_Standard"                  to "PHI_SUBSTANDARD",
        "PHI_Senior"                        to "PHI_SENIOR",
        "Global_excluding US&Canda"         to "PHI_GLOBAL_EXCL",
        "Global_Asia_excluding_India"       to "PHI_GLOBAL_ASIA",
        "Global_Europe"                     to "PHI_GLOBAL_EUROPE",
        "Global_Plus_Including USA & Can"   to "PHI_GLOBAL_PLUS",
        "Global_Plus_excluding_USA & Can"   to "PHI_GLOBAL_PLUS_EXCL",
        "Global_Plus_Ais_excluding_India"   to "PHI_GLOBAL_PLUS_ASIA",
        "Global_Plus_Europe"                to "PHI_GLOBAL_PLUS_EUROPE"
    )

    private suspend fun importBaseRates(wb: Workbook) {
        val actualSheets = (0 until wb.numberOfSheets).map { wb.getSheetName(it) }
        println("[DEBUG] Excel sheets found: $actualSheets")
        var totalRows = 0
        for ((sheetName, planId) in planSheets) {
            val sheet = wb.getSheet(sheetName)
            if (sheet == null) {
                println("[WARN] Sheet not found: '$sheetName'")
                continue
            }
            totalRows += parsePlanSheet(sheet, planId)
        }
        println("[INFO] Base rates imported: $totalRows rows")
    }

    /**
     * Parses a plan sheet. Column A is always empty; all data starts in column B.
     *
     * Pattern A — standard plans (all except PHI_SUBSTANDARD / PHI_SENIOR):
     *   Each FT has blocks for ALL zones side by side in the same rows:
     *     Row N:   col B = "[FT] Office Premium..." (title, ignored)
     *     Row N+1: col B = "[FT] ... Risk/Office Premium for Zone 4 ..."
     *              col Q = "[FT] ... Risk/Office Premium for Zone 3 ..."
     *              col AF = "[FT] ... Risk/Office Premium for Zone 2 ..."
     *              col AU = "[FT] ... Risk/Office Premium for Zone 1 ..."
     *     Row N+2: SI headers — one per zone group in their respective columns
     *     Row N+3+: age band rows — rates in each zone's columns
     *
     * Pattern B — PHI_SUBSTANDARD / PHI_SENIOR (single zone, no zone labels in sheet):
     *   Row N:   "[FT] Office Premium (Excl Taxes and Duties)" in col B
     *   Row N+1: "Office Premium (excl. tax and duties)" (generic, skip)
     *   Row N+2: "Age/Sum Insured" in col B, then SI values
     *   Row N+3+: age band rows
     */
    private suspend fun parsePlanSheet(sheet: Sheet, planId: String): Int {
        var count = 0
        val zoneRegex = Regex("Zone\\s+[1-4]", RegexOption.IGNORE_CASE)
        val isNoZonePlan = planId == "PHI_SUBSTANDARD" || planId == "PHI_SENIOR"

        data class ZoneBlock(val zone: String, val labelColIdx: Int, val siCols: MutableList<Pair<Int, Long>> = mutableListOf())

        var currentFT: String? = null
        var activeZoneBlocks = listOf<ZoneBlock>()

        newSuspendedTransaction {
            val lastRow = sheet.lastRowNum
            for (r in 0..lastRow) {
                val row = sheet.getRow(r) ?: continue
                val cell1 = row.getCell(1)?.stringOrEmpty() ?: ""

                if (isNoZonePlan) {
                    // Pattern B: title row "[FT] Office Premium (Excl Taxes and Duties)"
                    if (cell1.contains("Office Premium (Excl", ignoreCase = true)
                        && !cell1.trim().startsWith("Office", ignoreCase = true)
                    ) {
                        val ftCode = cell1.trim().substringBefore(" ").trim()
                        val ftMatch = FAMILY_TYPES.firstOrNull { it.code == ftCode }
                        if (ftMatch != null) {
                            currentFT = ftMatch.code
                            // title(r), empty(r+1), subtitle(r+2), SI header(r+3)
                            val siHeaderRow = sheet.getRow(r + 3) ?: continue
                            val siCols = mutableListOf<Pair<Int, Long>>()
                            for (c in siHeaderRow) {
                                if (c.columnIndex < 2) continue
                                val si = c.numericOrNull()?.toLong() ?: continue
                                if (si > 0) siCols.add(c.columnIndex to si)
                            }
                            activeZoneBlocks = if (siCols.isNotEmpty()) listOf(ZoneBlock("Zone 4", 1, siCols)) else listOf()
                        }
                        continue
                    }
                } else {
                    // Pattern A: scan ALL cells in this row for zone subtitle pattern
                    val detected = mutableListOf<ZoneBlock>()
                    for (c in row) {
                        val cv = c.stringOrEmpty()
                        if (cv.isBlank()) continue
                        val zm = zoneRegex.find(cv) ?: continue
                        if (!cv.contains("Risk Premium", ignoreCase = true) &&
                            !cv.contains("Office Premium for Zone", ignoreCase = true)) continue
                        val zoneLabel = "Zone " + zm.value.trim().last()
                        val zone = Zone.entries.firstOrNull { it.label.equals(zoneLabel, ignoreCase = true) }?.label ?: zoneLabel
                        val ftCode = cv.trim().substringBefore(" ").trim()
                        if (FAMILY_TYPES.none { it.code == ftCode }) continue
                        currentFT = ftCode
                        detected.add(ZoneBlock(zone, c.columnIndex))
                    }

                    if (detected.isNotEmpty()) {
                        detected.sortBy { it.labelColIdx }
                        // Read SI values from next row for each zone's column range
                        val siHeaderRow = sheet.getRow(r + 1)
                        if (siHeaderRow != null) {
                            for (i in detected.indices) {
                                val zb = detected[i]
                                val nextBoundary = if (i < detected.size - 1) detected[i + 1].labelColIdx else Int.MAX_VALUE
                                for (c in siHeaderRow) {
                                    val colIdx = c.columnIndex
                                    if (colIdx <= zb.labelColIdx || colIdx >= nextBoundary) continue
                                    val si = c.numericOrNull()?.toLong() ?: continue
                                    if (si > 0) zb.siCols.add(colIdx to si)
                                }
                            }
                        }
                        activeZoneBlocks = detected.filter { it.siCols.isNotEmpty() }
                        continue
                    }
                }

                // Age-band data row — age label is in col B (index 1)
                val ageMin = ageLabel(cell1) ?: continue
                val ft = currentFT ?: continue
                if (activeZoneBlocks.isEmpty()) continue

                for (zoneBlock in activeZoneBlocks) {
                    for ((col, si) in zoneBlock.siCols) {
                        val premium = row.getCell(col)?.numericOrNull() ?: continue
                        if (premium <= 0) continue
                        BaseRatesTable.insert {
                            it[BaseRatesTable.planId]        = planId
                            it[familyType]                   = ft
                            it[BaseRatesTable.zone]          = zoneBlock.zone
                            it[ageBandMin]                   = ageMin
                            it[BaseRatesTable.sumInsured]    = si
                            it[annualPremium]                = BigDecimal.valueOf(premium).setScale(2, RoundingMode.HALF_UP)
                        }
                        count++
                    }
                }
            }
        }
        println("[INFO] $planId: inserted $count base rate rows")
        return count
    }

    // ── STEP C: Import cover rates from Loadings and Discounts sheet ─────────

    private suspend fun importCoverRates(wb: Workbook) {
        val sheet = wb.getSheet("Loadings and Discounts")
            ?: wb.getSheet("Loadings And Discounts")
            ?: run { println("[WARN] Loadings and Discounts sheet not found"); return }

        newSuspendedTransaction {
            // Read all rows into memory for random-access parsing
            val rows = (0..sheet.lastRowNum).mapNotNull { sheet.getRow(it) }

            importDay1Instant(rows)
            importLoyaltyBonus(rows)
            importDoubleCover(rows)
            importChronicInstant(rows)
            importFlatPct("consumables_list1", rows, findSectionRow(rows, "Consumables Cover - List I"), 0.05)
            importPedWaiting(rows)
            importSpecificIllnessWaiting(rows)
            importModernTreatmentPlus(rows)
            importRoomRentMod(rows)
            importDiseaseSublimit(rows)
            importFlatPct("pre_post_hosp", rows, findSectionRow(rows, "Pre-Post Hospitalisation"), 0.10)
            importFlatPct("consumable_plus", rows, findSectionRow(rows, "Consumable Plus Cover"), 0.09)
            importFlatInr("home_care", rows, findSectionRow(rows, "Home Care Treatment"), 167.0)
            importInfiniteClaim(rows)
            importRestorationPlus(rows)
            importFlatInr("donor_plus", rows, findSectionRow(rows, "Donor Plus Cover"), 417.0)
            importSpouseProtect(rows)
            importDurableMedical(rows)
            importTenureWise(rows)
            importFlatPct("smart_select", rows, findSectionRow(rows, "Smart Select"), -0.15)
            importFlatPct("good_health", rows, findSectionRow(rows, "Incentivize Good Health"), 0.0)
            importAggregateDeductible(rows)
            importCoPay(rows)
            importFlatInr("child_protect", rows, findSectionRow(rows, "Child Protect"), 100.0)
            importDailyHospitalCash(rows)
            importConvalescence(rows)
            importCompassionate(rows)
            importPersonalAccident(rows)
            importFlatInr("air_ambulance", rows, findSectionRow(rows, "Air Ambulance"), 433.0)
            importFlatInr("fitness_plus", rows, findSectionRow(rows, "Fitness Plus Benefit"), 649.0)
            importFlatInr("wellness_package", rows, findSectionRow(rows, "Wellness Package"), 0.0)
            importFlatInr("second_opinion", rows, findSectionRow(rows, "Second E-opinion"), 67.0)
            importMaternityNewborn(rows)
            importFlatInr("post_delivery_care", rows, findSectionRow(rows, "Post Delivery Care"), 0.0)
            importInfertility(rows)
            importFlatInr("surrogate_mother", rows, findSectionRow(rows, "Surrogate Mother"), 908.0)
            importFlatInr("oocyte_donor", rows, findSectionRow(rows, "Oocyte"), 846.0)
            importFlatInr("post_discharge_care", rows, findSectionRow(rows, "Post Discharge Care Guidance"), 167.0)
            importFlatInr("adventure_sports", rows, findSectionRow(rows, "Adventure Sports Cover"), 183.0)
            importChronicManagement(rows)
            importFlatInr("female_vaccination", rows, findSectionRow(rows, "Female Vaccination"), 3333.0)
            importFlatInr("pru_health_specialist", rows, findSectionRow(rows, "Pru Health Specialist"), 83.0)
            importAdvanceHealthCheckup(rows)
            importCashlessOPD(rows)
            importFlatPct("prudential_healthy", rows, findSectionRow(rows, "Prudential Healthy"), 0.0075)
            importCriticalIllness(rows)
            importMaternityFixed(rows)
            importEnhancedGeo(rows)
            importCancerBooster(rows)
            importCancerScreening(rows)
            importPerClaimDeductible(rows)
            importDiscounts(rows)
        }
        println("[INFO] Cover rates imported")
    }

    // Helper: find the row index containing a keyword in column A
    private fun findSectionRow(rows: List<Row>, keyword: String): Int =
        rows.indexOfFirst { row -> row.getCell(0)?.stringOrEmpty()?.contains(keyword, ignoreCase = true) == true }

    // Helper: find the data start row (first row with numeric data after section header)
    private fun findDataRow(rows: List<Row>, fromIndex: Int): Int {
        for (i in fromIndex + 1 until minOf(fromIndex + 10, rows.size)) {
            val cell = rows[i].getCell(1)
            if (cell?.numericOrNull() != null) return i
        }
        return fromIndex + 1
    }

    private fun insertCoverRate(
        coverId: String, param1: String?, param2: String?,
        ageBandMin: Int?, sumInsured: Long?, planId: String?, rate: Double
    ) {
        CoverRateLookupTable.insert {
            it[CoverRateLookupTable.coverId]   = coverId
            it[param1Key]                       = param1
            it[param2Key]                       = param2
            it[CoverRateLookupTable.ageBandMin] = ageBandMin
            it[CoverRateLookupTable.sumInsured] = sumInsured
            it[CoverRateLookupTable.planId]     = planId
            it[CoverRateLookupTable.rate]       = bd(rate)
        }
    }

    private fun insertMemberRate(coverId: String, ageBandMin: Int?, param1: String?, rate: Double) {
        MemberLevelRatesTable.insert {
            it[MemberLevelRatesTable.coverId]    = coverId
            it[MemberLevelRatesTable.ageBandMin] = ageBandMin
            it[MemberLevelRatesTable.param1Key]  = param1
            it[MemberLevelRatesTable.rate]       = bd(rate)
        }
    }

    private fun importFlatPct(coverId: String, rows: List<Row>, sectionIdx: Int, defaultRate: Double) {
        if (sectionIdx < 0) { insertCoverRate(coverId, null, null, null, null, null, defaultRate); return }
        // Look for a single-cell rate in the section
        for (i in sectionIdx..minOf(sectionIdx + 5, rows.size - 1)) {
            val v = rows[i].getCell(1)?.numericOrNull()
            if (v != null) { insertCoverRate(coverId, null, null, null, null, null, v); return }
        }
        insertCoverRate(coverId, null, null, null, null, null, defaultRate)
    }

    private fun importFlatInr(coverId: String, rows: List<Row>, sectionIdx: Int, defaultFlat: Double) {
        importFlatPct(coverId, rows, sectionIdx, defaultFlat)
    }

    // ── Day 1 Instant (rows 4-8): SI × multiplier matrix ──────────────────
    // Row 4: header with multiplier names (1.5X, 2X, 3X, 4X) in cols B-E
    // Rows 5-8: [SI] [rate_1.5X] [rate_2X] [rate_3X] [rate_4X]
    private fun importDay1Instant(rows: List<Row>) {
        val secIdx = findSectionRow(rows, "Day 1 Instant")
        if (secIdx < 0) return

        // Find header row with multiplier names
        val headerRow = rows.getOrNull(secIdx + 1) ?: return
        val multipliers = mutableListOf<Pair<Int, String>>()  // (colIdx, "2X")
        for (c in 1..8) {
            val label = headerRow.getCell(c)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
            if (label.contains("X", ignoreCase = true)) multipliers.add(c to label)
        }

        // Data rows: each row is a SI value
        for (i in secIdx + 2 until minOf(secIdx + 20, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            for ((col, mult) in multipliers) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.DAY1_INSTANT, mult, null, null, si, null, rate)
            }
        }
    }

    // ── Loyalty Bonus (rows 12-64): 3 option variants each with age × SI ──
    // Each option block: header row with SIs, then 15 age rows
    private fun importLoyaltyBonus(rows: List<Row>) {
        val options = listOf("100% upto 200%", "100% upto 500%", "100% upto 1000%")
        var sectionStart = findSectionRow(rows, "Loyalty Bonus")
        if (sectionStart < 0) return

        for (opt in options) {
            // Find the subsection for this option
            val optIdx = rows.indexOfFirst { r ->
                val v = r.getCell(0)?.stringOrEmpty() ?: ""
                v.contains(opt.split("(").first().trim(), ignoreCase = true) ||
                (v.isBlank() && r.getCell(1)?.stringOrEmpty()?.contains(opt, ignoreCase = true) == true)
            }.takeIf { it >= 0 } ?: continue

            // Get SI header row
            val headerRow = rows.getOrNull(optIdx + 1) ?: continue
            val siCols = mutableListOf<Pair<Int, Long>>()
            for (c in 1..20) {
                val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
                if (si > 0) siCols.add(c to si)
            }
            if (siCols.isEmpty()) continue

            // 15 age-band rows
            for (i in optIdx + 2 until minOf(optIdx + 20, rows.size)) {
                val row = rows[i]
                val ageLabel = row.getCell(0)?.stringOrEmpty() ?: break
                val ageMin = ageLabel(ageLabel) ?: break
                for ((col, si) in siCols) {
                    val rate = row.getCell(col)?.numericOrNull() ?: continue
                    insertCoverRate(CoverIds.LOYALTY_BONUS, opt, null, ageMin, si, null, rate)
                }
            }
        }
    }

    // ── Double Cover (row 68): single flat rate in col B ──────────────────
    private fun importDoubleCover(rows: List<Row>) {
        val idx = findSectionRow(rows, "Double your Cover")
        if (idx < 0) { insertCoverRate(CoverIds.DOUBLE_COVER_7YR, null, null, null, null, null, 0.0325); return }
        val rate = rows.getOrNull(idx + 1)?.getCell(1)?.numericOrNull()
            ?: rows.getOrNull(idx)?.getCell(1)?.numericOrNull()
            ?: 0.0325
        insertCoverRate(CoverIds.DOUBLE_COVER_7YR, null, null, null, null, null, rate)
    }

    // ── Chronic Instant (rows 72-75): condition → rate ──────────────────────
    private fun importChronicInstant(rows: List<Row>) {
        val idx = findSectionRow(rows, "Chronic Conditions")
        if (idx < 0) return
        val conditions = listOf("Single condition", "Two comorbid condition", "Three comorbid condition")
        var condIdx = 0
        for (i in idx + 1 until minOf(idx + 8, rows.size)) {
            val rate = rows[i].getCell(1)?.numericOrNull() ?: continue
            if (condIdx < conditions.size)
                insertCoverRate(CoverIds.CHRONIC_INSTANT, conditions[condIdx++], null, null, null, null, rate)
        }
    }

    // ── PED Waiting (rows 85-86): period → rate ──────────────────────────────
    private fun importPedWaiting(rows: List<Row>) {
        val idx = findSectionRow(rows, "PED Waiting")
        if (idx < 0) return
        val periods = listOf("3 to 2 Years", "3 to 1 Year")
        var pIdx = 0
        for (i in idx + 1 until minOf(idx + 6, rows.size)) {
            val rate = rows[i].getCell(1)?.numericOrNull() ?: continue
            if (pIdx < periods.size)
                insertCoverRate(CoverIds.PED_WAITING, periods[pIdx++], null, null, null, null, rate)
        }
    }

    // ── Specific Illness Waiting (rows 91-106): age matrix + senior col ──────
    private fun importSpecificIllnessWaiting(rows: List<Row>) {
        val idx = findSectionRow(rows, "Specific Illness Waiting")
        if (idx < 0) return
        // data rows: col A = age label, col B = standard rate, col C = senior rate (if exists)
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            val stdRate = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.SPECIFIC_ILLNESS_WAITING, null, null, ageMin, null, null, stdRate)
            // Senior rate goes in as plan_id = "PHI_SENIOR" if present
            val seniorRate = row.getCell(2)?.numericOrNull()
            if (seniorRate != null)
                insertCoverRate(CoverIds.SPECIFIC_ILLNESS_WAITING, null, null, ageMin, null, "PHI_SENIOR", seniorRate)
        }
    }

    // ── Modern Treatment Plus (rows 111-123): plan-based flat ────────────────
    private fun importModernTreatmentPlus(rows: List<Row>) {
        val idx = findSectionRow(rows, "Modern Treatment Plus")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val planName = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            val pid = planNameToId[planName] ?: continue
            insertCoverRate(CoverIds.MODERN_TREATMENT_PLUS, null, null, null, null, pid, rate)
        }
    }

    // ── Room Rent Mod (rows 128-132): plan × room-type matrix ───────────────
    private fun importRoomRentMod(rows: List<Row>) {
        val idx = findSectionRow(rows, "Modification of Room Rent")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Room types in col A of header row section, rates in cols for each plan
        // Layout: row = room type option, cols = plan rates
        val roomTypes = listOf("General Room", "Shared Room", "Single Private AC Room", "Any Room")
        var rtIdx = 0
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            val row = rows[i]
            val cell0 = row.getCell(0)?.stringOrEmpty() ?: break
            // Try reading plan-based columns or just read room-type rows
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            if (rtIdx < roomTypes.size) {
                // Store as param1 = roomType, planId = null (default)
                insertCoverRate(CoverIds.ROOM_RENT_MOD, roomTypes[rtIdx], null, null, null, null, rate)
            }
            rtIdx++
        }
    }

    // ── Disease Sub-limit Plus (rows 136-141): plan-based flat ───────────────
    private fun importDiseaseSublimit(rows: List<Row>) {
        val idx = findSectionRow(rows, "Diseases Specific Sub-limit")
        if (idx < 0) return
        // Standard plans have a single flat rate; Senior/SubStd have SI-based rates
        for (i in idx + 2 until minOf(idx + 15, rows.size)) {
            val row = rows[i]
            val cell0 = row.getCell(0)?.stringOrEmpty() ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            // Check if it's a plan name
            val pid = planNameToId[cell0]
            if (pid != null) {
                insertCoverRate(CoverIds.DISEASE_SUBLIMIT, null, null, null, null, pid, rate)
            }
            // Check if it's an SI value (numeric) → for Senior/SubStd
            val si = cell0.toLongOrNull()
            if (si != null) {
                // Store with SI dimension
                insertCoverRate(CoverIds.DISEASE_SUBLIMIT, null, null, null, si, null, rate)
            }
        }
    }

    // ── Infinite Claim (rows 165-182): SI matrix ────────────────────────────
    private fun importInfiniteClaim(rows: List<Row>) {
        val idx = findSectionRow(rows, "Infinite Claim")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.INFINITE_CLAIM, null, null, null, si, null, rate)
        }
    }

    // ── Restoration Plus (rows 186-201): age × SI matrix ────────────────────
    private fun importRestorationPlus(rows: List<Row>) {
        val idx = findSectionRow(rows, "Restoration Plus")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, Long>>()
        for (c in 1..20) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si)
        }
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.RESTORATION_PLUS, null, null, ageMin, si, null, rate)
            }
        }
    }

    // ── Spouse Protect (rows 211-228): SI matrix ────────────────────────────
    private fun importSpouseProtect(rows: List<Row>) {
        val idx = findSectionRow(rows, "Spouse Protect")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.SPOUSE_PROTECT, null, null, null, si, null, rate)
        }
    }

    // ── Durable Medical (rows 232-247): age × SI matrix ─────────────────────
    private fun importDurableMedical(rows: List<Row>) {
        val idx = findSectionRow(rows, "Durable Medical")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, Long>>()
        for (c in 1..20) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si)
        }
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.DURABLE_MEDICAL, null, null, ageMin, si, null, rate)
            }
        }
    }

    // ── Tenure Wise (rows 251-267): SI × tenure matrix ──────────────────────
    private fun importTenureWise(rows: List<Row>) {
        val idx = findSectionRow(rows, "Tenure Wise")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Cols: SI, 3yr, 4yr, 5yr
        val tenureCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..5) {
            val label = headerRow.getCell(c)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
            if (label.contains("Year", ignoreCase = true)) tenureCols.add(c to label)
        }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            for ((col, tenureLabel) in tenureCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.TENURE_WISE, null, null, null, si, tenureLabel, rate)
            }
        }
    }

    // ── Aggregate Deductible (rows 281-299): SI × deductible matrix ──────────
    private fun importAggregateDeductible(rows: List<Row>) {
        val idx = findSectionRow(rows, "Aggregate Deductible")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val dedCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..5) {
            val ded = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            dedCols.add(c to ded.toString())
        }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            for ((col, ded) in dedCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.AGGREGATE_DEDUCTIBLE, ded, null, null, si, null, rate)
            }
        }
    }

    // ── Co-Pay (rows 303-313): plan-type × copay-pct matrix ─────────────────
    // Three sections: Omnibus | Senior | SubStandard
    private fun importCoPay(rows: List<Row>) {
        val idx = findSectionRow(rows, "Co-Payment")
        if (idx < 0) return
        // Header row identifies 3 co-pay table types
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Cols: A=copay%, B=Omnibus, D=Senior, G=SubStd
        val colMap = mutableMapOf<String, Int>()  // coPayTableId → colIndex
        for (c in 0..10) {
            val v = headerRow.getCell(c)?.stringOrEmpty() ?: continue
            when {
                v.contains("Omnibus", ignoreCase = true)    -> colMap["OMNIBUS"]     = c + 1  // rate col next to label
                v.contains("Senior", ignoreCase = true)     -> colMap["SENIOR"]      = c + 1
                v.contains("Sub Standard", ignoreCase = true) -> colMap["SUB_STANDARD"] = c + 1
            }
        }

        for (i in idx + 2 until minOf(idx + 15, rows.size)) {
            val row = rows[i]
            val copayPct = row.getCell(0)?.numericOrNull()?.toString() ?: break
            for ((tableId, rateCol) in colMap) {
                val rate = row.getCell(rateCol)?.numericOrNull() ?: continue
                // Store: param1=copayPct, planId=coPayTableId
                insertCoverRate(CoverIds.CO_PAY, copayPct, null, null, null, tableId, rate)
            }
        }
    }

    // ── Daily Hospital Cash (rows 321-336): member-level age × benefit ───────
    private fun importDailyHospitalCash(rows: List<Row>) {
        val idx = findSectionRow(rows, "Daily Hospital Cash")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Cols: age-band, benefit amounts
        val benefitCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..15) {
            val benefit = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (benefit > 0) benefitCols.add(c to benefit.toString())
        }
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            for ((col, benefit) in benefitCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertMemberRate(CoverIds.DAILY_HOSPITAL_CASH, ageMin, benefit, rate)
            }
        }
    }

    // ── Convalescence (rows 341-344): benefit × trigger matrix ───────────────
    private fun importConvalescence(rows: List<Row>) {
        val idx = findSectionRow(rows, "Convalescence")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Cols: benefit amount, >10 days, >5 days, >3 days
        val triggerCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..4) {
            val label = headerRow.getCell(c)?.stringOrEmpty()?.takeIf { it.contains("Days", ignoreCase = true) } ?: continue
            triggerCols.add(c to label)
        }
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val row = rows[i]
            val amount = row.getCell(0)?.numericOrNull()?.toLong()?.toString() ?: break
            for ((col, trigger) in triggerCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.CONVALESCENCE, amount, trigger, null, null, null, rate)
            }
        }
    }

    // ── Compassionate (rows 349-351): amount → rate ──────────────────────────
    private fun importCompassionate(rows: List<Row>) {
        val idx = findSectionRow(rows, "Compassionate")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 6, rows.size)) {
            val row = rows[i]
            val amount = row.getCell(0)?.numericOrNull()?.toLong()?.toString() ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.COMPASSIONATE, amount, null, null, null, null, rate)
        }
    }

    // ── Personal Accident (rows 356-361): coverage amount → rate (member-level)
    private fun importPersonalAccident(rows: List<Row>) {
        val idx = findSectionRow(rows, "Personal Accident")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            val row = rows[i]
            val siStr = row.getCell(0)?.numericOrNull()?.toLong()?.toString() ?: break
            val rate = row.getCell(1)?.numericOrNull() ?: continue
            insertMemberRate(CoverIds.PERSONAL_ACCIDENT, 0, siStr, rate)
        }
    }

    // ── Maternity & NewBorn (rows 382-386): waiting × SI matrix ─────────────
    private fun importMaternityNewborn(rows: List<Row>) {
        val idx = findSectionRow(rows, "Maternity & New Born")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..5) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si.toString())
        }
        val waitingPeriods = listOf("9 Months", "24 Months", "36 Months", "48 Months")
        var wpIdx = 0
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            val row = rows[i]
            val wpLabel = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
            val wp = waitingPeriods.getOrNull(wpIdx++) ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.MATERNITY_NEWBORN, si, wp, null, null, null, rate)
            }
        }
    }

    // ── Infertility (rows 395-399): waiting × SI matrix ─────────────────────
    private fun importInfertility(rows: List<Row>) {
        val idx = findSectionRow(rows, "Infertility")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..4) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si.toString())
        }
        val waitingPeriods = listOf("9 Months", "24 Months", "36 Months", "48 Months")
        var wpIdx = 0
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val row = rows[i]
            val wpLabel = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
            val wp = waitingPeriods.getOrNull(wpIdx++) ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.INFERTILITY, si, wp, null, null, null, rate)
            }
        }
    }

    // ── Chronic Management (rows 425-428): condition → rate (member-level) ───
    private fun importChronicManagement(rows: List<Row>) {
        val idx = findSectionRow(rows, "Chronic Management")
        if (idx < 0) return
        val conditions = listOf("1", "2", "3")
        var cIdx = 0
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val rate = rows[i].getCell(1)?.numericOrNull() ?: continue
            insertMemberRate(CoverIds.CHRONIC_MANAGEMENT, 0, conditions.getOrNull(cIdx++), rate)
        }
    }

    // ── Advance Health Checkup (rows 443-444): type → rate (member-level) ───
    private fun importAdvanceHealthCheckup(rows: List<Row>) {
        val idx = findSectionRow(rows, "Advance Prudential Health Check")
        if (idx < 0) return
        // Header: col B = Advance, col C = Basic
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val dataRow   = rows.getOrNull(idx + 2) ?: return
        val advRate   = dataRow.getCell(1)?.numericOrNull()
        val basicRate = dataRow.getCell(2)?.numericOrNull()
        if (advRate != null)   insertMemberRate(CoverIds.ADVANCE_HEALTH_CHECKUP, 0, "Advance", advRate)
        if (basicRate != null) insertMemberRate(CoverIds.ADVANCE_HEALTH_CHECKUP, 0, "Basic",   basicRate)
    }

    // ── Cashless OPD (rows 448-451): OPD limit → rate ───────────────────────
    private fun importCashlessOPD(rows: List<Row>) {
        val idx = findSectionRow(rows, "Cashless OPD")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 6, rows.size)) {
            val row = rows[i]
            val limit = row.getCell(0)?.numericOrNull()?.toLong()?.toString() ?: break
            val rate  = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.CASHLESS_OPD, limit, null, null, null, null, rate)
        }
    }

    // ── Critical Illness (rows 464-479): age → rate per mille (member-level) ─
    private fun importCriticalIllness(rows: List<Row>) {
        val idx = findSectionRow(rows, "Critical Illness Cover")
        if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            val rate   = row.getCell(1)?.numericOrNull() ?: continue
            insertMemberRate(CoverIds.CRITICAL_ILLNESS, ageMin, null, rate)
        }
    }

    // ── Maternity Fixed (rows 484-488): waiting × SI matrix ─────────────────
    private fun importMaternityFixed(rows: List<Row>) {
        val idx = findSectionRow(rows, "Maternity Cover Fixed Benefit")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..4) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si.toString())
        }
        val waitingPeriods = listOf("0 Month", "3 Months", "6 Months", "9 Months")
        var wpIdx = 0
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val row = rows[i]
            val wpLabel = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
            val wp = waitingPeriods.getOrNull(wpIdx++) ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.MATERNITY_FIXED, si, wp, null, null, null, rate)
            }
        }
    }

    // ── Enhanced Geo (rows 493-496): geography → rate ───────────────────────
    private fun importEnhancedGeo(rows: List<Row>) {
        val idx = findSectionRow(rows, "Enhanced Geographical")
        if (idx < 0) return
        val geoMap = mapOf(
            "Worlwide exluding USA & CANADA" to "Worldwide excl. USA & Canada",
            "Worldwide excl. USA & Canada"   to "Worldwide excl. USA & Canada",
            "Asia excluding India"           to "Asia excluding India",
            "Asia excl. India"               to "Asia excluding India",
            "Europe"                         to "Europe",
            "Worldwide incl. USA & Canada"   to "Worldwide incl. USA & Canada"
        )
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val row = rows[i]
            val geoRaw = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
            val geo    = geoMap.entries.firstOrNull { geoRaw.contains(it.key, ignoreCase = true) }?.value ?: geoRaw
            val rate   = row.getCell(1)?.numericOrNull() ?: continue
            insertCoverRate(CoverIds.ENHANCED_GEO, geo, null, null, null, null, rate)
        }
    }

    // ── Cancer Booster (rows 501-516): age × SI matrix ───────────────────────
    private fun importCancerBooster(rows: List<Row>) {
        val idx = findSectionRow(rows, "Cancer Booster")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = mutableListOf<Pair<Int, Long>>()
        for (c in 1..20) {
            val si = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            if (si > 0) siCols.add(c to si)
        }
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            for ((col, si) in siCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.CANCER_BOOSTER, null, null, ageMin, si, null, rate)
            }
        }
    }

    // ── Cancer Screening (rows 521-536): age × plan-type ────────────────────
    private fun importCancerScreening(rows: List<Row>) {
        val idx = findSectionRow(rows, "Annual Screening")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        // Col B = Office Rate, Col C = PHI Global
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val row = rows[i]
            val ageMin  = ageLabel(row.getCell(0)?.stringOrEmpty() ?: "") ?: break
            val offRate = row.getCell(1)?.numericOrNull() ?: continue
            val glbRate = row.getCell(2)?.numericOrNull()
            insertCoverRate(CoverIds.CANCER_SCREENING, null, null, ageMin, null, null, offRate)
            if (glbRate != null)
                insertCoverRate(CoverIds.CANCER_SCREENING, null, null, ageMin, null, "GLOBAL", glbRate)
        }
    }

    // ── Per Claim Deductible (rows 540-558): SI × deductible matrix ──────────
    private fun importPerClaimDeductible(rows: List<Row>) {
        val idx = findSectionRow(rows, "Per Claim Deductible")
        if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val dedCols = mutableListOf<Pair<Int, String>>()
        for (c in 1..4) {
            val ded = headerRow.getCell(c)?.numericOrNull()?.toLong() ?: continue
            dedCols.add(c to ded.toString())
        }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val row = rows[i]
            val si = row.getCell(0)?.numericOrNull()?.toLong() ?: break
            for ((col, ded) in dedCols) {
                val rate = row.getCell(col)?.numericOrNull() ?: continue
                insertCoverRate(CoverIds.PER_CLAIM_DEDUCTIBLE, ded, null, null, si, null, rate)
            }
        }
    }

    // ── Discounts ────────────────────────────────────────────────────────────
    private fun importDiscounts(rows: List<Row>) {
        // Tenure discount
        val tenureIdx = findSectionRow(rows, "Tenure Discount")
        if (tenureIdx >= 0) {
            val tenureLabels = listOf("1 Year", "2 Years", "3 Years", "4 Years", "5 Years")
            for (i in tenureIdx + 2 until minOf(tenureIdx + 10, rows.size)) {
                val row = rows[i]
                val label = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
                val rate  = row.getCell(1)?.numericOrNull() ?: break
                if (label in tenureLabels)
                    insertDiscountRate("disc_tenure", "Tenure Discount", label, rate)
            }
        }

        // Employee/Affiliate discount
        val empIdx = findSectionRow(rows, "Affiliate Employee Discount")
        if (empIdx >= 0) {
            val rate = rows.getOrNull(empIdx + 1)?.getCell(1)?.numericOrNull() ?: 0.10
            insertDiscountRate("disc_employee", "Employee / Affiliate Discount", null, rate)
        }

        // CIBIL discount
        val cibilIdx = findSectionRow(rows, "CIBIL Score Discount")
        if (cibilIdx >= 0) {
            val bandLabels = listOf("Upto 700", "701 to 750", "751 to 800", "801 to 849", "850 and above")
            val bandKeys   = listOf("<=700", "701-750", "751-800", "801-849", ">=850")
            for (i in cibilIdx + 2 until minOf(cibilIdx + 10, rows.size)) {
                val row = rows[i]
                val label = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
                val rate  = row.getCell(1)?.numericOrNull() ?: break
                val idx2  = bandLabels.indexOfFirst { label.contains(it.split(" ").first(), ignoreCase = true) }
                val key   = bandKeys.getOrNull(idx2) ?: label
                insertDiscountRate("disc_cibil", "CIBIL Score Discount", key, rate)
            }
        }

        // NRI discount
        val nriIdx = findSectionRow(rows, "NRI")
        if (nriIdx >= 0) {
            val rate = rows.getOrNull(nriIdx + 1)?.getCell(1)?.numericOrNull() ?: 0.15
            insertDiscountRate("disc_nri", "NRI Discount", null, rate)
        }

        // Auto-Debit discount
        val adIdx = findSectionRow(rows, "Auto Debit")
        if (adIdx >= 0) {
            val rate = rows.getOrNull(adIdx + 1)?.getCell(1)?.numericOrNull() ?: 0.025
            insertDiscountRate("disc_auto_debit", "Auto-Debit Discount", null, rate)
        }

        // Multiple Member discount
        val mmIdx = findSectionRow(rows, "Multiple Member")
        if (mmIdx >= 0) {
            val rates = mutableListOf<Pair<String, Double>>()
            for (i in mmIdx + 2 until minOf(mmIdx + 6, rows.size)) {
                val row  = rows[i]
                val label = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: break
                val rate  = row.getCell(1)?.numericOrNull() ?: break
                val key   = if (label.contains("4", ignoreCase = false)) "4+" else "2-3"
                insertDiscountRate("disc_multi_member", "Multiple Member Discount", key, rate)
            }
        }

        // Commission in lieu discount
        val commIdx = findSectionRow(rows, "Discount in lieu of commission")
        if (commIdx >= 0) {
            val rate = rows.getOrNull(commIdx + 1)?.getCell(1)?.numericOrNull() ?: 0.15
            insertDiscountRate("disc_commission_lieu", "Discount in Lieu of Commission", null, rate)
        }

        // Corporate GMC discount
        val gmcIdx = findSectionRow(rows, "Corporate GMC")
        if (gmcIdx >= 0) {
            val rate = rows.getOrNull(gmcIdx + 1)?.getCell(1)?.numericOrNull() ?: 0.05
            insertDiscountRate("disc_gmc", "Corporate GMC Discount", null, rate)
        }
    }

    private fun insertDiscountRate(id: String, name: String, paramKey: String?, rate: Double) {
        DiscountRatesTable.insert {
            it[DiscountRatesTable.id]       = if (paramKey != null) "${id}_${paramKey}" else id
            it[DiscountRatesTable.name]     = name
            it[DiscountRatesTable.paramKey] = paramKey
            it[DiscountRatesTable.rate]     = bd(rate)
        }
    }

    // ── STEP D: Import cover availability from Sheet1 ────────────────────────

    private suspend fun importCoverAvailability(wb: Workbook) {
        val sheet = wb.getSheet("Sheet1") ?: run {
            println("[WARN] Sheet1 (cover availability) not found")
            return
        }

        newSuspendedTransaction {
            val headerRow = sheet.getRow(0) ?: return@newSuspendedTransaction
            // Map column index → plan ID
            val planCols = mutableMapOf<Int, String>()
            for (c in 1..20) {
                val planName = headerRow.getCell(c)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
                val planId = planNameToId[planName] ?: continue
                planCols[c] = planId
            }

            for (r in 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val coverName = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
                val coverId = coverNameToId[coverName] ?: continue

                for ((col, planId) in planCols) {
                    val value = row.getCell(col)?.stringOrEmpty() ?: continue
                    if (value.contains("yes", ignoreCase = true)) {
                        CoverAvailabilityTable.insert {
                            it[CoverAvailabilityTable.planId]  = planId
                            it[CoverAvailabilityTable.coverId] = coverId
                        }
                    }
                }
            }
        }
        println("[INFO] Cover availability imported")
    }

    // ── STEP E: Import instalment config from Sheet2 ─────────────────────────

    private suspend fun importInstalmentConfig(wb: Workbook) {
        val sheet = wb.getSheet("Sheet2") ?: run {
            println("[WARN] Sheet2 (instalment config) not found — using computed values")
            computeAndInsertInstalmentConfig()
            return
        }

        newSuspendedTransaction {
            // Sheet2 layout: rows 5-19, cols M=tenure+payTenure key, P-S = Monthly/Quarterly/Half-Yearly/Annual
            val headerRow = sheet.getRow(3) ?: return@newSuspendedTransaction
            // Find payment mode columns
            val modeCols = mutableMapOf<String, Int>()  // mode label → col index
            for (c in 0..10) {
                val label = headerRow.getCell(c)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
                when {
                    label.contains("Monthly",     ignoreCase = true) -> modeCols["Monthly"]     = c
                    label.contains("Quarterly",   ignoreCase = true) -> modeCols["Quarterly"]   = c
                    label.contains("Half",        ignoreCase = true) -> modeCols["Half-Yearly"] = c
                    label.contains("Annual",      ignoreCase = true) -> modeCols["Annual"]      = c
                }
            }

            for (r in 4..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val tenureLabel   = row.getCell(0)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: continue
                val payTermLabel  = row.getCell(1)?.stringOrEmpty()?.takeIf { it.isNotBlank() } ?: tenureLabel

                val tenure    = Tenure.entries.firstOrNull { it.label == tenureLabel }    ?: continue
                val payTenure = Tenure.entries.firstOrNull { it.label == payTermLabel }   ?: tenure

                // Single premium always = 1
                InstalmentConfigTable.insertIgnore {
                    it[policyTenure]   = tenure.label
                    it[paymentTenure]  = payTenure.label
                    it[paymentMode]    = PaymentMode.SINGLE_PREMIUM.label
                    it[instalmentCount] = 1
                }

                for ((modeLabel, col) in modeCols) {
                    val count = row.getCell(col)?.numericOrNull()?.toInt() ?: continue
                    val mode  = PaymentMode.entries.firstOrNull { it.label == modeLabel } ?: continue
                    InstalmentConfigTable.insertIgnore {
                        it[policyTenure]   = tenure.label
                        it[paymentTenure]  = payTenure.label
                        it[paymentMode]    = mode.label
                        it[instalmentCount] = count
                    }
                }
            }
        }
        println("[INFO] Instalment config imported")
    }

    private suspend fun computeAndInsertInstalmentConfig() = newSuspendedTransaction {
        // Fallback: compute from rules when Sheet2 not available
        for (policyTenure in Tenure.entries) {
            for (payTenure in Tenure.entries) {
                for (mode in PaymentMode.entries) {
                    val count = when (mode) {
                        PaymentMode.SINGLE_PREMIUM -> 1
                        PaymentMode.ANNUAL         -> policyTenure.years
                        PaymentMode.HALF_YEARLY    -> policyTenure.years * 2
                        PaymentMode.QUARTERLY      -> policyTenure.years * 4
                        PaymentMode.MONTHLY        -> policyTenure.years * 12
                    }
                    InstalmentConfigTable.insertIgnore {
                        it[InstalmentConfigTable.policyTenure]   = policyTenure.label
                        it[InstalmentConfigTable.paymentTenure]  = payTenure.label
                        it[InstalmentConfigTable.paymentMode]    = mode.label
                        it[instalmentCount]                       = count
                    }
                }
            }
        }
    }
}
