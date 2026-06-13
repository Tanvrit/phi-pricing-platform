package com.rate.sdk.ingestion.handler

import com.rate.core.base.model.PageRequest
import com.rate.core.money.Money
import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanLifecycle
import com.rate.core.rating.ports.model.PlanType
import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Vendor
import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.ChronicOpdGrid
import com.rate.sdk.catalog.model.group.ConsumablesList
import com.rate.sdk.catalog.model.group.DayCareProcedure
import com.rate.sdk.catalog.model.group.DeviceCategory
import com.rate.sdk.catalog.model.group.EligibilityCriteria
import com.rate.sdk.catalog.model.group.GroupGrade
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.catalog.model.group.HealthCheckupPackage
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.PpdPtdTable
import com.rate.sdk.catalog.model.group.SublimitScope
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog
import com.rate.sdk.catalog.model.group.VaccinationItem
import com.rate.sdk.catalog.model.group.WaitingPeriod
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.BenefitScheduleRepository
import com.rate.sdk.catalog.repository.ChronicOpdGridRepository
import com.rate.sdk.catalog.repository.ConsumablesListRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.DayCareProcedureRepository
import com.rate.sdk.catalog.repository.EligibilityCriteriaRepository
import com.rate.sdk.catalog.repository.GroupGradeRepository
import com.rate.sdk.catalog.repository.GroupProductConfigRepository
import com.rate.sdk.catalog.repository.HealthCheckupPackageRepository
import com.rate.sdk.catalog.repository.MedicalDeviceCatalogRepository
import com.rate.sdk.catalog.repository.PpdPtdTableRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.SurgicalSublimitRepository
import com.rate.sdk.catalog.repository.TenureRepository
import com.rate.sdk.catalog.repository.VaccinationCatalogRepository
import com.rate.sdk.catalog.repository.VendorRepository
import com.rate.sdk.catalog.repository.WaitingPeriodRepository
import com.rate.sdk.ingestion.event.IngestionEvent
import com.rate.sdk.ingestion.event.IngestionEventSink
import com.rate.sdk.ingestion.model.ImportSummary
import com.rate.sdk.ingestion.model.ImportSummary.Companion.Counts
import com.rate.sdk.ingestion.network.SectionBenefitInput
import com.rate.sdk.ingestion.network.SeedCatalogRequest

/**
 * Bundle of the parsed CSV files that compose ONE group product (Employer-Employee). Each field is
 * the raw CSV text of a `/data` file; null = that file wasn't provided. Lets the seeder run a whole
 * folder in one idempotent pass while keeping the parser per-shape and pure.
 */
data class CatalogSourceBundle(
    val pbtIndexCsv: String? = null,
    val groupProductCsv: String? = null,
    val baseBenefitScheduleCsv: String? = null,
    val flatCiListCsv: String? = null,
    val flatCiListCode: String = "CI_101",
    val tieredCiListCsv: String? = null,
    val waitingPeriodsCsv: String? = null,
    val eligibilityCsv: String? = null,
    val ppdPtdCsv: String? = null,
    val dayCareCsv: String? = null,
    val consumablesCsv: String? = null,
    val healthCheckupCsv: String? = null,
    val annexureCsv: String? = null,
    val chronicOpdCsv: String? = null,
    /** New Vendor Registration / KYC form → one global [Vendor]. */
    val vendorKycCsv: String? = null,
    /** GROUP EE annexure "Sublimits …" table → [SurgicalSublimit]s. */
    val surgicalSublimitCsv: String? = null,
    /** GROUP EE annexure "Adult Vaccination List" → a [VaccinationCatalog]. */
    val vaccinationCatalogCsv: String? = null,
    /** GROUP EE annexure "List of Monitoring / Medical Devices" → a [MedicalDeviceCatalog]. */
    val medicalDeviceCatalogCsv: String? = null,
    /** Per-section benefit files (D1-PA/CI/HC/EMI, D2-OPD, OPD-TBD, PBT-PA) yield [Cover]s linked to their Section. */
    val sectionBenefitCsvs: List<SectionBenefitInput> = emptyList(),
    val productLine: ProductLine = ProductLine.GROUP,
) {
    companion object {
        /** Build a bundle from the wire [SeedCatalogRequest] (server route → seeder). */
        fun fromRequest(req: SeedCatalogRequest): CatalogSourceBundle = CatalogSourceBundle(
            pbtIndexCsv = req.pbtIndexCsv,
            groupProductCsv = req.groupProductCsv,
            baseBenefitScheduleCsv = req.baseBenefitScheduleCsv,
            flatCiListCsv = req.flatCiListCsv,
            flatCiListCode = req.flatCiListCode,
            tieredCiListCsv = req.tieredCiListCsv,
            waitingPeriodsCsv = req.waitingPeriodsCsv,
            eligibilityCsv = req.eligibilityCsv,
            ppdPtdCsv = req.ppdPtdCsv,
            dayCareCsv = req.dayCareCsv,
            consumablesCsv = req.consumablesCsv,
            healthCheckupCsv = req.healthCheckupCsv,
            annexureCsv = req.annexureCsv,
            chronicOpdCsv = req.chronicOpdCsv,
            vendorKycCsv = req.vendorKycCsv,
            surgicalSublimitCsv = req.surgicalSublimitCsv,
            vaccinationCatalogCsv = req.vaccinationCatalogCsv,
            medicalDeviceCatalogCsv = req.medicalDeviceCatalogCsv,
            sectionBenefitCsvs = req.sectionBenefitCsvs,
            productLine = req.productLine,
        )
    }
}

/**
 * Idempotent catalog seeding from parsed CSV sources, written through the sdk-catalog repository
 * PORTs ([com.rate.core.base.repository.ConfigRepository.bulkUpsert]).
 *
 * Relocated semantics from the monolith's `server/import` pipeline, but inverted to depend only on
 * the catalog PORTs (no Mongo here). Idempotency: every parsed entity carries a deterministic
 * business key (section number, cover code, list code, schedule name, …); the seeder upserts under
 * a STABLE id derived from that key, so re-seeding the same source updates in place. Cross-refs that
 * the parser can't know (e.g. `groupProductRef`) are stitched here after the parent id is fixed.
 *
 * Pure-KMP, transport-agnostic; the server calls [seedGroupBundle] on boot/import, and emits an
 * [IngestionEvent] through the [sink] for the audit log.
 */
class CatalogSeeder(
    private val sections: SectionRepository,
    private val covers: CoverRepository,
    private val criticalIllnessLists: CriticalIllnessListRepository,
    private val annexures: AnnexureRepository,
    private val groupProducts: GroupProductConfigRepository,
    private val benefitSchedules: BenefitScheduleRepository,
    private val waitingPeriods: WaitingPeriodRepository,
    private val eligibility: EligibilityCriteriaRepository,
    private val ppdPtdTables: PpdPtdTableRepository,
    private val dayCare: DayCareProcedureRepository,
    private val consumables: ConsumablesListRepository,
    private val healthCheckups: HealthCheckupPackageRepository,
    private val chronicOpd: ChronicOpdGridRepository,
    // ── Plans / grades / tenures (rating anchors seeded from the CI tiers + grade bands) ──
    private val plans: PlanRepository,
    private val groupGrades: GroupGradeRepository,
    private val tenures: TenureRepository,
    // ── Global vendor KYC + GROUP annexure-derived structured entities (S2/S4) ──
    private val vendors: VendorRepository,
    private val surgicalSublimits: SurgicalSublimitRepository,
    private val vaccinationCatalogs: VaccinationCatalogRepository,
    private val medicalDeviceCatalogs: MedicalDeviceCatalogRepository,
    private val parser: CsvCatalogParser = CsvCatalogParser(),
    private val vendorKycParser: VendorKycParser = VendorKycParser(),
    private val sink: IngestionEventSink = IngestionEventSink.NOOP,
) {

    /** Server-route convenience: seed straight from the wire request. */
    suspend fun seed(request: SeedCatalogRequest, actor: String? = "ingestion"): ImportSummary =
        seedGroupBundle(CatalogSourceBundle.fromRequest(request), actor, request.force)

    /**
     * Parse + upsert an entire group source bundle. Returns the [ImportSummary] with per-entity
     * counts and any warnings (a file that parses to zero entities is a warning, not an error).
     * [force] = false skips a collection that already has rows (first-boot-only seed); [force] = true
     * re-imports (CSV becomes authoritative again — used by the admin "re-import" action).
     */
    suspend fun seedGroupBundle(
        bundle: CatalogSourceBundle,
        actor: String? = "ingestion",
        force: Boolean = false,
    ): ImportSummary {
        val counts = LinkedHashMap<String, Int>()
        val warnings = ArrayList<String>()
        val pl = bundle.productLine

        // ── Group product config (the parent — needed for child refs) ─────────
        var groupProductId: String? = null
        bundle.groupProductCsv?.let { csv ->
            val gp = parser.parseGroupProductConfig(csv).withStableId("groupprod", "GROUP_EE")
            groupProductId = gp.id
            counts.bump(Counts.GROUP_PRODUCT_CONFIGS, upsertIfNeeded(groupProducts, listOf(gp), actor, force))
        }

        // ── Sections ──────────────────────────────────────────────────────────
        // Keep the seeded sections (with their stable ids) so the per-section benefit files below
        // can resolve their `sectionRef` (section NUMBER placeholder → Section id).
        val seededSections = ArrayList<Section>()
        bundle.pbtIndexCsv?.let { csv ->
            val parsed = parser.parseSections(csv, pl)
            warnEmpty(parsed, "PBT Index", warnings)
            val stamped = parsed.map { it.withStableId("section", "${pl.name}:${it.sectionNumber}:${it.name}") }
            seededSections += stamped
            counts.bump(Counts.SECTIONS, upsertIfNeeded(sections, stamped, actor, force))
        }
        // Resolve a PBT section NUMBER → seeded Section id (first match on the top-level number).
        fun sectionIdFor(number: String): String? =
            seededSections.firstOrNull { it.sectionNumber == number }?.id
                ?: seededSections.firstOrNull { it.sectionNumber.substringBefore('.') == number }?.id

        // ── Base benefit schedule (BenefitSchedule + its lines only) ──────────
        // We intentionally do NOT mint Cover stubs from the schedule lines here. The section
        // benefit files (D1-PA/CI/HC/EMI, D2-OPD, PBT-PA) are the SOLE source of Cover documents;
        // they carry the full field set (trigger, coverageText, options, sublimits, changeNote,
        // sectionRef…). Minting minimal stubs here previously produced 55 orphan covers with no
        // sectionRef and DUPLICATES of the section covers under different ids (the base key
        // "GROUP:code" vs the section key "GROUP:1:code" never merged). The schedule's lines keep
        // all the cover-code/limit data, so nothing is lost by dropping the stubs.
        // Keep the seeded base-schedule id so Plans / GroupGrades can reference it.
        var baseScheduleId: String? = null
        bundle.baseBenefitScheduleCsv?.let { csv ->
            val schedule = parser.parseBaseBenefitSchedule(csv)
                .copy(groupProductRef = groupProductId.orEmpty())
                .withStableId("schedule", "${groupProductId.orEmpty()}:${"Base Covers"}")
            baseScheduleId = schedule.id
            counts.bump(Counts.BENEFIT_SCHEDULES, upsertIfNeeded(benefitSchedules, listOf(schedule), actor, force))
        }

        // ── Critical-illness lists (flat 101/92 + tiered Plan 1..5) ───────────
        // Keep the seeded TIERED lists (with stable ids) so we can mint one Plan per CI tier below
        // and back-fill each list's tieredPlanRefs in the post-seed linkage pass.
        val seededTieredCiLists = ArrayList<CriticalIllnessList>()
        bundle.flatCiListCsv?.let { csv ->
            val list = parser.parseFlatCriticalIllnessList(csv, bundle.flatCiListCode)
                .withStableId("cilist", bundle.flatCiListCode)
            counts.bump(Counts.CRITICAL_ILLNESS_LISTS, upsertIfNeeded(criticalIllnessLists, listOf(list), actor, force))
        }
        bundle.tieredCiListCsv?.let { csv ->
            val tiers = parser.parseTieredCriticalIllnessLists(csv)
            warnEmpty(tiers, "Tiered CI List", warnings)
            val stamped = tiers.map { it.withStableId("cilist", it.listCode) }
            seededTieredCiLists += stamped
            counts.bump(Counts.CRITICAL_ILLNESS_LISTS, upsertIfNeeded(criticalIllnessLists, stamped, actor, force))
        }

        // ── Waiting periods ─────────────────────────────────────────────────
        bundle.waitingPeriodsCsv?.let { csv ->
            val parsed = parser.parseWaitingPeriods(csv).map {
                it.copy(groupProductRef = groupProductId.orEmpty())
                    .withStableId("waiting", "${groupProductId.orEmpty()}:${it.slNo}:${it.clause}")
            }
            warnEmpty(parsed, "Waiting Periods", warnings)
            counts.bump(Counts.WAITING_PERIODS, upsertIfNeeded(waitingPeriods, parsed, actor, force))
        }

        // ── Eligibility ─────────────────────────────────────────────────────
        // Keep the seeded eligibility id so Plans / GroupGrades can reference it.
        var eligibilityId: String? = null
        bundle.eligibilityCsv?.let { csv ->
            val elig = parser.parseEligibility(csv)
                .copy(groupProductRef = groupProductId.orEmpty())
                .withStableId("eligibility", groupProductId.orEmpty().ifBlank { "GROUP_EE" })
            eligibilityId = elig.id
            counts.bump(Counts.ELIGIBILITY_CRITERIA, upsertIfNeeded(eligibility, listOf(elig), actor, force))
        }

        // ── PPD / PTD tables ────────────────────────────────────────────────
        bundle.ppdPtdCsv?.let { csv ->
            val parsed = parser.parsePpdPtdTables(csv).map {
                it.copy(groupProductRef = groupProductId.orEmpty())
                    .withStableId("ppdptd", "${groupProductId.orEmpty()}:${it.tableType}:${it.name}")
            }
            warnEmpty(parsed, "PPD/PTD Tables", warnings)
            counts.bump(Counts.PPD_PTD_TABLES, upsertIfNeeded(ppdPtdTables, parsed, actor, force))
        }

        // ── Day-care procedures ─────────────────────────────────────────────
        // Key on category + displayOrder: the source repeats a category name ("Operations on the
        // tongue" appears twice) and keying on the name alone collapsed the two blocks into one,
        // dropping the second block's items. displayOrder is unique per parsed block.
        bundle.dayCareCsv?.let { csv ->
            val parsed = parser.parseDayCare(csv).map {
                it.withStableId("daycare", "${it.displayOrder}:${it.category}")
            }
            warnEmpty(parsed, "Day Care List", warnings)
            counts.bump(Counts.DAY_CARE_PROCEDURES, upsertIfNeeded(dayCare, parsed, actor, force))
        }

        // ── Consumables (4 lists) ───────────────────────────────────────────
        bundle.consumablesCsv?.let { csv ->
            val parsed = parser.parseConsumables(csv).map { it.withStableId("consumables", it.listType.name) }
            warnEmpty(parsed, "Consumables List", warnings)
            counts.bump(Counts.CONSUMABLES_LISTS, upsertIfNeeded(consumables, parsed, actor, force))
        }

        // ── Health check-up packages ────────────────────────────────────────
        bundle.healthCheckupCsv?.let { csv ->
            val parsed = parser.parseHealthCheckupPackages(csv).map { it.withStableId("checkup", it.name) }
            warnEmpty(parsed, "Health Check Up Packages", warnings)
            counts.bump(Counts.HEALTH_CHECKUP_PACKAGES, upsertIfNeeded(healthCheckups, parsed, actor, force))
        }

        // ── Annexure (generic lists: OPD minor procedures etc.) ─────────────
        bundle.annexureCsv?.let { csv ->
            val generic = parser.parseAnnexures(csv).map { it.withStableId("annexure", it.annexureCode) }
            counts.bump(Counts.ANNEXURES, upsertIfNeeded(annexures, generic, actor, force))
        }

        // ── Chronic-OPD grid (the GHI "Annexure" sheet: condition → consults/tests) ──
        bundle.chronicOpdCsv?.let { csv ->
            val grid = parser.parseChronicOpdGrid(csv).map { it.withStableId("chronicopd", it.condition + ":" + it.category) }
            counts.bump(Counts.CHRONIC_OPD_GRIDS, upsertIfNeeded(chronicOpd, grid, actor, force))
        }

        // ── Section benefit files → Covers (D1-PA/CI/HC/EMI, D2-OPD, PBT-PA) ──
        // Each file's rows become Covers linked (sectionRef) to the seeded Section that matches the
        // input's sectionNumber. The stable id is keyed on the cover CODE ALONE ("GROUP:code") — NOT
        // on the section number — so the same cover described in two files (e.g. PBT-PA's
        // "Accidental Death Cover (AD)" and D1-PA's "Accidental Death (AD)", both aliased to one
        // canonical code) MERGES into a single document rather than producing duplicates. We also
        // collapse same-code covers parsed within one batch by keeping the RICHEST version (the one
        // with the most populated fields), so the file with trigger/changeNote/options wins.
        val coversByCode = LinkedHashMap<String, Cover>()
        for (input in bundle.sectionBenefitCsvs) {
            val parsedCovers = parser.parseCoversFromSection(input.csv, pl, input.sectionNumber)
            warnEmpty(parsedCovers, "Section ${input.sectionNumber} (${input.name})", warnings)
            if (parsedCovers.isEmpty()) continue
            val sectionId = sectionIdFor(input.sectionNumber)
            if (sectionId == null) {
                warnings += "Section ${input.sectionNumber} (${input.name}): no matching Section " +
                    "found in PBT Index — covers stored with unresolved sectionRef"
            }
            for (cover in parsedCovers) {
                val canonical = canonicalCoverCode(cover.code)
                val resolved = cover.copy(sectionRef = sectionId ?: cover.sectionRef)
                val existing = coversByCode[canonical]
                coversByCode[canonical] =
                    if (existing == null) resolved else mergeCovers(existing, resolved)
            }
        }
        // Keep the stamped covers so we can populate each Section's coverRefs in the linkage pass.
        val seededCovers = ArrayList<Cover>()
        if (coversByCode.isNotEmpty()) {
            val stamped = coversByCode.map { (canonical, cover) ->
                cover.withStableId("cover", "${pl.name}:$canonical")
            }
            seededCovers += stamped
            counts.bump(Counts.COVERS, upsertIfNeeded(covers, stamped, actor, force))
        }

        // ── Vendor KYC (GLOBAL, single record) ───────────────────────────────
        bundle.vendorKycCsv?.let { csv ->
            val vendor = vendorKycParser.parseVendor(csv)
            if (vendor.vendorName.isBlank()) {
                warnings += "Vendor KYC: form has no vendor name — skipped"
            } else {
                val stamped = vendor.withStableId("vendor", vendor.vendorName)
                counts.bump(COUNT_VENDORS, upsertIfNeeded(vendors, listOf(stamped), actor, force))
            }
        }

        // ── GROUP EE annexure-derived structured entities (S2) ───────────────
        // The three sources (sublimit table / vaccination list / device list) all live in the GROUP
        // EE Annexure.csv; DevSeedRoutes points each field at that file, but they parse independent
        // sub-tables, so keeping them as separate inputs keeps each entity swappable to its own file.
        bundle.surgicalSublimitCsv?.let { csv ->
            val parsed = parseSurgicalSublimits(csv).map {
                it.withStableId("sublimit", "${it.slNo}:${it.surgeryName}")
            }
            warnEmpty(parsed, "Surgical Sublimits", warnings)
            counts.bump(COUNT_SURGICAL_SUBLIMITS, upsertIfNeeded(surgicalSublimits, parsed, actor, force))
        }
        bundle.vaccinationCatalogCsv?.let { csv ->
            val parsed = parseVaccinationCatalogs(csv).map { it.withStableId("vaccination", it.listCode) }
            warnEmpty(parsed, "Vaccination List", warnings)
            counts.bump(COUNT_VACCINATION_CATALOGS, upsertIfNeeded(vaccinationCatalogs, parsed, actor, force))
        }
        bundle.medicalDeviceCatalogCsv?.let { csv ->
            val parsed = parseMedicalDeviceCatalogs(csv).map { it.withStableId("device", it.listCode) }
            warnEmpty(parsed, "Medical Device List", warnings)
            counts.bump(COUNT_MEDICAL_DEVICE_CATALOGS, upsertIfNeeded(medicalDeviceCatalogs, parsed, actor, force))
        }

        // ── Tenures (1yr 0% .. 5yr 15% single-premium multi-tenure discount) ──
        // Relocated from the buy-online hardcoded ladder; admin-tunable from here on. Seeded under
        // stable ids per (productLine, years) so re-seeding upserts in place.
        run {
            val tenureRows = TENURE_DISCOUNTS.map { (years, discount) ->
                Tenure(
                    years = years,
                    label = "$years Year" + if (years == 1) "" else "s",
                    productLine = pl,
                    multiTenureDiscount = discount,
                    displayOrder = years,
                ).withStableId("tenure", "${pl.name}:$years")
            }
            counts.bump(COUNT_TENURES, upsertIfNeeded(tenures, tenureRows, actor, force))
        }

        // ── Group grades (standard A/B/C bands) ──────────────────────────────
        // The EE eligibility expresses occupational RISK CLASSES, not HR grades, so we seed the
        // standard A/B/C grade bands with illustrative SI. Each grade points at the base benefit
        // schedule + eligibility so quoting can resolve a grade's covers/SI.
        val seededGrades = ArrayList<GroupGrade>()
        run {
            val grades = buildGroupGrades(
                groupProductId = groupProductId,
                baseScheduleId = baseScheduleId,
                eligibilityId = eligibilityId,
            )
            seededGrades += grades
            counts.bump(Counts.GROUP_GRADES, upsertIfNeeded(groupGrades, grades, actor, force))
        }

        // ── Plans (one tier per seeded tiered CI list) ───────────────────────
        // Each CI tier ("Plan 1".."Plan 5") becomes a Plan, linked to its CriticalIllnessList
        // (ciListRef), the seeded grades (gradeRefs), eligibility (eligibilityRef) and the base
        // benefit schedule (benefitScheduleRefs). Stable ids per tier index so re-seeding upserts.
        val seededPlans = ArrayList<Plan>()
        if (seededTieredCiLists.isNotEmpty()) {
            seededTieredCiLists.forEachIndexed { idx, ciList ->
                val tierNo = idx + 1
                val plan = Plan(
                    id = stableId("plan", "${pl.name}:TIER$tierNo"),
                    name = ciList.name.ifBlank { "Plan $tierNo" },
                    planType = PlanType.DOMESTIC,
                    productLine = pl,
                    description = "Group EE plan tier $tierNo (${ciList.size} CI)",
                    lifecycle = PlanLifecycle.LIVE,
                    ciListRef = ciList.id,
                    gradeRefs = seededGrades.map { it.id },
                    eligibilityRef = eligibilityId,
                    benefitScheduleRefs = listOfNotNull(baseScheduleId),
                    rateVersion = PLAN_RATE_VERSION,
                )
                seededPlans += plan
            }
            counts.bump(COUNT_PLANS, upsertPlansIfNeeded(seededPlans, force))
        }

        // ── POST-SEED LINKAGE ────────────────────────────────────────────────
        // After covers + sections are seeded, populate each Section.coverRefs from the covers that
        // resolved to it. After plans + CI lists are seeded, populate each CI list's tieredPlanRefs
        // ("Plan N" → [planId]). Both passes upsert the parents in place (idempotent under force).
        if (seededSections.isNotEmpty() && seededCovers.isNotEmpty()) {
            val refsBySection = seededCovers
                .filter { it.sectionRef.isNotBlank() }
                .groupBy { it.sectionRef }
                .mapValues { (_, list) -> list.map { it.id } }
            val relinked = seededSections.mapNotNull { section ->
                val refs = refsBySection[section.id] ?: return@mapNotNull null
                if (refs == section.coverRefs) null else section.copy(coverRefs = refs)
            }
            if (relinked.isNotEmpty()) {
                // Write back the linkage (computed from THIS run's covers) regardless of the
                // first-boot guard — the sections were just (re)seeded above.
                sections.bulkUpsert(relinked, actor)
            }
        }
        if (seededPlans.isNotEmpty() && seededTieredCiLists.isNotEmpty()) {
            val relinked = seededTieredCiLists.mapIndexedNotNull { idx, ciList ->
                val plan = seededPlans.getOrNull(idx) ?: return@mapIndexedNotNull null
                val refs = mapOf("Plan ${idx + 1}" to listOf(plan.id))
                if (refs == ciList.tieredPlanRefs) null else ciList.copy(tieredPlanRefs = refs)
            }
            if (relinked.isNotEmpty()) criticalIllnessLists.bulkUpsert(relinked, actor)
        }

        val summary = ImportSummary(
            ok = true,
            productLine = pl,
            counts = counts,
            warnings = warnings,
        )
        sink.emit(
            IngestionEvent.CatalogSeeded(
                productLine = pl,
                totalWritten = summary.totalWritten,
                counts = counts,
                actor = actor,
                at = com.rate.core.base.time.Now.instant(),
            ),
        )
        return summary
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Collapse cover-code variants that name the SAME benefit to one canonical code so covers
     * described in two source files merge instead of duplicating. The recurring divergence is a
     * trailing "_cover" token ("accidental_death_cover_ad" vs "accidental_death_ad") and an
     * embedded "_cover_" ("daily_cash_cover_dcb_…"); we strip a "_cover" that sits before a
     * trailing acronym/qualifier or at the very end. Idempotent for codes that have no "_cover".
     */
    private fun canonicalCoverCode(code: String): String {
        var c = code
        // "..._cover_<suffix>" → "..._<suffix>" (e.g. accidental_death_cover_ad → accidental_death_ad).
        c = c.replace(Regex("""_cover(_[a-z0-9]+)$"""), "$1")
        // trailing "_cover" → "" (e.g. spouse_cover → spouse).
        c = c.removeSuffix("_cover")
        return c.ifBlank { code }
    }

    /**
     * Merge two covers parsed from different files for the same canonical code: keep the RICHER
     * value of each field (non-blank / larger collection / non-zero money), so the file that
     * carries trigger / changeNote / options / sublimits wins and partial rows are completed.
     */
    private fun mergeCovers(a: Cover, b: Cover): Cover = a.copy(
        sectionRef = a.sectionRef.ifBlank { b.sectionRef },
        name = a.name.ifBlank { b.name },
        description = longer(a.description, b.description),
        trigger = a.trigger.ifBlank { b.trigger },
        coverageText = longer(a.coverageText, b.coverageText),
        minSumInsured = if (a.minSumInsured != com.rate.core.money.Money.ZERO) a.minSumInsured else b.minSumInsured,
        maxSumInsured = if (a.maxSumInsured != com.rate.core.money.Money.ZERO) a.maxSumInsured else b.maxSumInsured,
        payoutType = if (a.payoutType != com.rate.core.regulatory.PayoutType.INDEMNITY) a.payoutType else b.payoutType,
        options = richerList(a.options, b.options) { it.label },
        optionsParam2 = richerList(a.optionsParam2, b.optionsParam2) { it.label },
        percentOfSI = if (a.percentOfSI > 0.0) a.percentOfSI else b.percentOfSI,
        changeNote = a.changeNote.ifBlank { b.changeNote },
        survivalPeriod = (a.survivalPeriod + b.survivalPeriod).distinct(),
        sublimits = richerList(a.sublimits, b.sublimits) { it.label },
        deductibleOptions = (a.deductibleOptions + b.deductibleOptions).distinct(),
        maxPayableDuration = (a.maxPayableDuration + b.maxPayableDuration).distinct(),
        importAliases = (a.importAliases + b.importAliases).distinct(),
    )

    private fun longer(a: String, b: String): String = if (a.length >= b.length) a else b

    private fun <T> richerList(a: List<T>, b: List<T>, key: (T) -> String): List<T> =
        (a + b).distinctBy { key(it) }

    /** Bump a count key by [delta]. */
    private fun MutableMap<String, Int>.bump(key: String, delta: Int) {
        if (delta != 0) this[key] = (this[key] ?: 0) + delta
    }

    private fun warnEmpty(list: List<*>, label: String, warnings: MutableList<String>) {
        if (list.isEmpty()) warnings += "Source '$label' parsed to 0 entities (shape mismatch or empty file)"
    }

    /**
     * Upsert through a [com.rate.core.base.repository.ConfigRepository], honouring [force]: when
     * not forcing, an already-populated collection is left untouched (first-boot-only seed).
     */
    private suspend fun <T : com.rate.core.base.model.ConfigEntity> upsertIfNeeded(
        repo: com.rate.core.base.repository.ConfigRepository<T>,
        entities: List<T>,
        actor: String?,
        force: Boolean,
    ): Int {
        if (entities.isEmpty()) return 0
        if (!force && repo.list(PageRequest(size = 1)).total > 0L) return 0
        return repo.bulkUpsert(entities, actor)
    }

    /**
     * Upsert [Plan]s through the core [PlanRepository] PORT (Plan is NOT a sdk-catalog
     * ConfigRepository — its actual serves both the rating + admin surfaces). Honours [force] the
     * same way: when not forcing and any plan already exists, the first-boot-only seed is skipped.
     */
    private suspend fun upsertPlansIfNeeded(entities: List<Plan>, force: Boolean): Int {
        if (entities.isEmpty()) return 0
        if (!force && plans.getAllPlans().isNotEmpty()) return 0
        entities.forEach { plans.upsertPlan(it) }
        return entities.size
    }

    /**
     * Build the standard A/B/C GROUP grade bands to seed. Each grade carries the base benefit
     * schedule + eligibility refs so quoting can resolve its covers/SI. SI is illustrative (the
     * rating agent scaffolds real rates) — Money is derived per band.
     */
    private fun buildGroupGrades(
        groupProductId: String?,
        baseScheduleId: String?,
        eligibilityId: String?,
    ): List<GroupGrade> {
        val scheduleRefs = listOfNotNull(baseScheduleId)
        return DEFAULT_GRADE_BANDS.mapIndexed { idx, (grade, siRupees) ->
            GroupGrade(
                groupProductRef = groupProductId.orEmpty(),
                grade = grade,
                description = "$grade band (illustrative SI)",
                sumInsured = Money.fromRupees(siRupees),
                benefitScheduleRefs = scheduleRefs,
                eligibilityRef = eligibilityId,
                displayOrder = idx,
            ).withStableId("grade", "${groupProductId.orEmpty()}:$grade")
        }
    }

    // ── Inline parsers for the GROUP EE annexure structured sub-tables (S2) ───
    // Self-contained pure-KMP parsing of the three annexure sub-tables into the S2 entities. Kept
    // here (not in CsvCatalogParser) because they target the E3-owned seed path; swappable later.

    /**
     * Parse the "Sublimits on Treatments/…(Per Claim)" sub-table of the GROUP EE annexure into
     * [SurgicalSublimit]s. The block opens with a "Sl no,Surgeries,Min,Max" header; each data row
     * is `slNo, surgeryName, "X% of SI Opted", "Y% of SI Opted"`. Percent bounds are converted to
     * FRACTIONS (10% → 0.10). The scope is inferred from the surgery label ("Per eye" → PER_EYE,
     * "Per family" → PER_FAMILY, else PER_PERSON).
     */
    private fun parseSurgicalSublimits(csv: String): List<SurgicalSublimit> {
        val rows = Csv.parse(csv)
        val out = ArrayList<SurgicalSublimit>()
        var inBlock = false
        var order = 0
        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            // Header row "Sl no,Surgeries,Min,Max" opens the block.
            if (a.equals("Sl no", true) && b.equals("Surgeries", true)) { inBlock = true; continue }
            if (!inBlock) continue
            val slNo = a.toIntOrNull()
            if (slNo == null) {
                // A non-numbered, non-empty row after the data ends the block (e.g. the *indicative
                // list note or the next section heading).
                if (a.isNotBlank() || b.isNotBlank()) inBlock = false
                continue
            }
            if (b.isBlank()) continue
            out += SurgicalSublimit(
                slNo = slNo,
                surgeryName = b,
                minPercentOfSI = parseSiPercentFraction(Csv.cell(r, 2)),
                maxPercentOfSI = parseSiPercentFraction(Csv.cell(r, 3)),
                scope = scopeFor(b),
                displayOrder = order++,
            )
        }
        return out
    }

    /** "10% of SI Opted" → 0.10; blank/unparseable → null. */
    private fun parseSiPercentFraction(raw: String): Double? {
        val pct = Regex("""(\d+(?:\.\d+)?)\s*%""").find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
        return pct?.let { it / 100.0 }
    }

    private fun scopeFor(label: String): SublimitScope = when {
        label.contains("per eye", true) -> SublimitScope.PER_EYE
        label.contains("per family", true) -> SublimitScope.PER_FAMILY
        else -> SublimitScope.PER_PERSON
    }

    /**
     * Parse the "Adult Vaccination List" sub-table of the GROUP EE annexure into a single
     * [VaccinationCatalog]. The block opens with "Sl no,ADULT VACCINATIONS"; each following row is
     * `slNo, vaccinationName`. Ends at the first blank/heading row after the list.
     */
    private fun parseVaccinationCatalogs(csv: String): List<VaccinationCatalog> {
        val rows = Csv.parse(csv)
        val items = ArrayList<VaccinationItem>()
        var inBlock = false
        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            if (a.equals("Sl no", true) && b.contains("VACCINATION", true)) { inBlock = true; continue }
            if (!inBlock) continue
            val slNo = a.toIntOrNull()
            if (slNo == null) {
                if (a.isNotBlank() || b.isNotBlank()) break // next section starts
                continue
            }
            if (b.isBlank()) continue
            items += VaccinationItem(slNo = slNo, name = b)
        }
        if (items.isEmpty()) return emptyList()
        return listOf(
            VaccinationCatalog(
                listCode = VACCINATION_LIST_CODE,
                name = "Adult Vaccination List",
                items = items,
            ),
        )
    }

    /**
     * Parse the "List of Monitoring / Medical Devices" sub-table of the GROUP EE annexure into a
     * single [MedicalDeviceCatalog]. The block opens with a "List of … Devices" title; numbered
     * col-0 rows are CATEGORY headings (col 1 = category name) and blank-col-0 rows are the devices
     * under the current category.
     */
    private fun parseMedicalDeviceCatalogs(csv: String): List<MedicalDeviceCatalog> {
        val rows = Csv.parse(csv)
        val categories = ArrayList<DeviceCategory>()
        var currentName: String? = null
        var currentDevices = ArrayList<String>()
        var inBlock = false

        fun flush() {
            val name = currentName
            if (name != null) categories += DeviceCategory(name = name, devices = currentDevices.toList())
            currentDevices = ArrayList()
        }

        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            if (!inBlock) {
                if (a.startsWith("List of", true) && a.contains("Device", true)) inBlock = true
                continue
            }
            val numbered = a.toIntOrNull() != null
            when {
                // A numbered row with a category name in col 1 → start a new category.
                numbered && b.isNotBlank() -> { flush(); currentName = b }
                // A blank-col-0 row with a device name in col 1 → device under the current category.
                a.isBlank() && b.isNotBlank() -> currentDevices.add(b)
                // A new non-numbered title in col 0 → the device block has ended.
                a.isNotBlank() && !numbered -> { flush(); currentName = null; inBlock = false }
            }
        }
        flush()
        if (categories.isEmpty()) return emptyList()
        return listOf(
            MedicalDeviceCatalog(
                listCode = MEDICAL_DEVICE_LIST_CODE,
                name = "List of Monitoring / Medical Devices",
                categories = categories,
            ),
        )
    }

    companion object {
        // Count-map keys for the entities seeded by E3 (ImportSummary.Counts owns the shared ones).
        private const val COUNT_VENDORS = "vendors"
        private const val COUNT_SURGICAL_SUBLIMITS = "surgicalSublimits"
        private const val COUNT_VACCINATION_CATALOGS = "vaccinationCatalogs"
        private const val COUNT_MEDICAL_DEVICE_CATALOGS = "medicalDeviceCatalogs"
        private const val COUNT_PLANS = "plans"
        private const val COUNT_TENURES = "tenures"

        /** Stable lookup keys for the single annexure-derived lists. */
        private const val VACCINATION_LIST_CODE = "ADULT_VACC"
        private const val MEDICAL_DEVICE_LIST_CODE = "MED_DEVICES"

        /** Rate-set tag stamped on seeded plans (the rating agent scaffolds illustrative rates). */
        private const val PLAN_RATE_VERSION = "scaffold-v1"

        /** Multi-tenure single-premium discount ladder (years → discount fraction). */
        private val TENURE_DISCOUNTS = listOf(
            1 to 0.0,
            2 to 0.075,
            3 to 0.10,
            4 to 0.125,
            5 to 0.15,
        )

        /** Default GROUP grade bands (grade label → illustrative SI in rupees). */
        private val DEFAULT_GRADE_BANDS = listOf(
            "Grade A" to 1_000_000L,
            "Grade B" to 500_000L,
            "Grade C" to 300_000L,
        )
    }
}

// ── Stable-id stamping (deterministic ids → idempotent bulkUpsert) ───────────
// Each entity's id is `<prefix>:<sha8(businessKey)>` so re-seeding the same source upserts the
// same document. We reuse the module's pure-KMP SHA-256 (8-char prefix is collision-safe here).
private fun stableId(prefix: String, businessKey: String): String =
    "$prefix:" + SourceHash.ofText(businessKey).take(20)

private fun Section.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun Cover.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun CriticalIllnessList.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun Annexure.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun GroupProductConfig.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun BenefitSchedule.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun WaitingPeriod.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun EligibilityCriteria.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun PpdPtdTable.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun DayCareProcedure.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun ConsumablesList.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun HealthCheckupPackage.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun ChronicOpdGrid.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun Vendor.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun SurgicalSublimit.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun VaccinationCatalog.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun MedicalDeviceCatalog.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun GroupGrade.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
private fun Tenure.withStableId(prefix: String, key: String) = copy(id = stableId(prefix, key))
