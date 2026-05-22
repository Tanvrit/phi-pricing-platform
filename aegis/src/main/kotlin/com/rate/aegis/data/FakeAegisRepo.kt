package com.rate.aegis.data

import com.rate.domain.model.CoPaymentTable
import com.rate.domain.model.GeographyScope
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanType
import com.rate.domain.model.UnderwritingCategory

/**
 * Plan lifecycle status — Aegis-side concept (not yet on the domain `Plan`).
 *
 * The audit notes a `Plan.status` field is missing; until Ship-6 lands the field
 * onto the domain, the dashboard maintains the mapping locally here so the
 * Plan Configurator's read mode can show LIVE / DRAFT / RETIRED pills today.
 */
enum class PlanLifecycle(val label: String) {
    LIVE("Live"),
    DRAFT("Draft"),
    RETIRED("Retired"),
}

/** Synthetic Aegis-side metadata that doesn't yet live on the domain `Plan`. */
data class PlanMeta(
    val planId: String,
    val uin: String,
    val lifecycle: PlanLifecycle,
    val lastModifiedIso: String,
    val lastModifiedBy: String,
)

/**
 * Synthetic in-memory repo backing the Aegis surfaces while Ship-4/5 are read-only.
 *
 * Hand-curated to mirror `docs/07-business-rules.md` §13 (the 14 plan registry) plus
 * a Senior Sub-Standard variant to make the master list interesting. Sum-insured
 * grids, allowed family types and geographies follow the rate tables in
 * `docs/03-rate-tables.md`.
 *
 * Once the server-driven plans land (currently disabled in :aegis), the surfaces
 * will switch to `LocalRateDataProvider.getAllPlans()` or `PlanRepository.getAllPlans()`;
 * this file becomes a fixture for screenshot tests only.
 */
object FakeAegisRepo {

    private val SI_DOMESTIC = listOf(
        300_000L, 500_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L,
        5_000_000L, 7_500_000L, 10_000_000L,
    )
    private val SI_FLAGSHIP = listOf(
        500_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L, 5_000_000L,
        7_500_000L, 10_000_000L, 15_000_000L, 20_000_000L, 30_000_000L, 50_000_000L,
    )
    private val SI_SENIOR = listOf(
        300_000L, 500_000L, 1_000_000L, 1_500_000L, 2_000_000L, 2_500_000L, 3_000_000L, 5_000_000L,
    )
    private val SI_GLOBAL = listOf(
        5_000_000L, 10_000_000L, 15_000_000L, 20_000_000L, 30_000_000L, 50_000_000L, 100_000_000L,
    )

    private val ZONES_ALL = listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4", "Pan India")
    private val ZONES_SENIOR_SUB = listOf("Pan India")
    private val FAMILY_FULL = listOf("1A", "2A", "2A1C", "2A2C", "2A3C", "2A4C", "1A1C", "1A2C", "1A3C", "1A4C")
    private val FAMILY_SENIOR = listOf("1A", "2A")

    val plans: List<Plan> = listOf(
        Plan(
            id = "PHI_BASIC",
            name = "PHI Basic",
            planType = PlanType.DOMESTIC,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            coPaymentTable = CoPaymentTable.OMNIBUS,
            description = "Entry-level domestic hospitalisation cover. Pre-existing waiting period 36 months. Standalone covers limited to the PHI Basic catalog.",
            availableSumInsureds = SI_DOMESTIC,
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            allowedCoverIds = setOf(
                "day1_instant", "loyalty_bonus", "consumables_list1", "ped_waiting",
                "disease_sublimit", "pre_post_hosp", "smart_select", "good_health",
                "child_protect", "daily_hospital_cash", "personal_accident",
                "second_opinion", "co_pay", "per_claim_deductible",
            ),
            maxDiscountCap = 0.30,
            rateTableId = "PHI_BASIC",
            minAge = 5, maxAge = 65, isActive = true, gstRate = 0.18,
        ),
        Plan(
            id = "PHI_POSP",
            name = "PHI POSP",
            planType = PlanType.DOMESTIC_POSP,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            description = "Point-of-sale person variant of PHI Basic with simplified underwriting; capped SI grid.",
            availableSumInsureds = listOf(300_000L, 500_000L, 1_000_000L, 1_500_000L, 2_500_000L, 5_000_000L),
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.20,
            rateTableId = "PHI_POSP",
            minAge = 5, maxAge = 60, isActive = true,
        ),
        Plan(
            id = "PHI_FLAGSHIP1",
            name = "PHI Flagship 1",
            planType = PlanType.DOMESTIC_FLAGSHIP,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            description = "Mid-tier domestic flagship with the full add-on catalog. Restoration Plus and Infinite Claim available.",
            availableSumInsureds = SI_FLAGSHIP,
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            allowedCoverIds = emptySet(), // all covers
            maxDiscountCap = 0.35,
            rateTableId = "PHI_FLAGSHIP1",
            minAge = 5, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_FLAGSHIP2",
            name = "PHI Flagship 2",
            planType = PlanType.DOMESTIC_FLAGSHIP,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            description = "Senior flagship variant with maternity and infertility add-ons; preferred tier for working professionals.",
            availableSumInsureds = SI_FLAGSHIP,
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.35,
            rateTableId = "PHI_FLAGSHIP2",
            minAge = 5, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_FLAGSHIP3",
            name = "PHI Flagship 3",
            planType = PlanType.DOMESTIC_FLAGSHIP,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            description = "Flagship 3 — withdrawn in FY2025Q4. Retained for renewal-only servicing.",
            availableSumInsureds = SI_FLAGSHIP,
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_FLAGSHIP3",
            minAge = 5, maxAge = 75, isActive = false,
        ),
        Plan(
            id = "PHI_FLAGSHIP4",
            name = "PHI Flagship 4 (Private Banker)",
            planType = PlanType.DOMESTIC_FLAGSHIP,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            description = "HNI/private-banker channel. Pre-bundled premium add-ons, lowest co-pay tier, highest discount cap.",
            availableSumInsureds = SI_FLAGSHIP.drop(2),
            availableZones = ZONES_ALL,
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.40,
            rateTableId = "PHI_FLAGSHIP4",
            minAge = 18, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_SENIOR",
            name = "PHI Senior",
            planType = PlanType.DOMESTIC_SENIOR,
            underwritingCategory = UnderwritingCategory.SENIOR,
            geographyScope = GeographyScope.DOMESTIC,
            coPaymentTable = CoPaymentTable.SENIOR,
            description = "Senior citizen plan (entry age 60+). Mandatory 20% co-payment; PAN-India zone only.",
            availableSumInsureds = SI_SENIOR,
            availableZones = ZONES_SENIOR_SUB,
            availableFamilyTypes = FAMILY_SENIOR,
            allowedCoverIds = setOf(
                "loyalty_bonus", "co_pay", "second_opinion", "daily_hospital_cash",
                "personal_accident", "compassionate", "smart_select", "good_health",
            ),
            maxDiscountCap = 0.20,
            rateTableId = "PHI_SENIOR",
            minAge = 60, maxAge = 99, isActive = true,
        ),
        Plan(
            id = "PHI_SUBSTANDARD",
            name = "PHI Sub Standard",
            planType = PlanType.DOMESTIC_SUBSTANDARD,
            underwritingCategory = UnderwritingCategory.SUB_STANDARD,
            geographyScope = GeographyScope.DOMESTIC,
            coPaymentTable = CoPaymentTable.SUB_STANDARD,
            description = "Sub-standard-risk product for declined / loaded lives. Higher co-payment ladder, PAN-India zone only.",
            availableSumInsureds = SI_DOMESTIC.dropLast(4),
            availableZones = ZONES_SENIOR_SUB,
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.15,
            rateTableId = "PHI_SUBSTANDARD",
            minAge = 18, maxAge = 65, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_EXCL",
            name = "PHI Global (Excl. USA & Canada)",
            planType = PlanType.GLOBAL,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_EXCL_US_CANADA,
            description = "International coverage outside USA & Canada with India base. Cashless network across 130+ countries.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_EXCL",
            minAge = 5, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_ASIA",
            name = "PHI Global (Asia excl. India)",
            planType = PlanType.GLOBAL,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_ASIA_EXCL_INDIA,
            description = "Cost-optimised global plan for Asia-focused travellers.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_ASIA",
            minAge = 5, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_EUROPE",
            name = "PHI Global (Europe)",
            planType = PlanType.GLOBAL,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_EUROPE,
            description = "Europe-focused worldwide plan. Schengen-friendly travel hospitalisation included.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_EUROPE",
            minAge = 5, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_PLUS",
            name = "PHI Global Plus (Incl. USA & Canada)",
            planType = PlanType.GLOBAL_PLUS,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_INCL_US_CANADA,
            description = "Highest tier worldwide plan. Includes US & Canada. Direct-billing with HCA and Cleveland Clinic networks.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_PLUS",
            minAge = 18, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_PLUS_EXCL",
            name = "PHI Global Plus (Excl. USA & Canada)",
            planType = PlanType.GLOBAL_PLUS,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_EXCL_US_CANADA,
            description = "Global Plus tier outside USA & Canada — premium cover, lower premium than the US-incl. variant.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_PLUS_EXCL",
            minAge = 18, maxAge = 75, isActive = true,
        ),
        Plan(
            id = "PHI_GLOBAL_PLUS_ASIA",
            name = "PHI Global Plus (Asia excl. India)",
            planType = PlanType.GLOBAL_PLUS,
            underwritingCategory = UnderwritingCategory.STANDARD,
            geographyScope = GeographyScope.GLOBAL_ASIA_EXCL_INDIA,
            description = "Awaiting actuarial sign-off. Currently in draft for FY2026Q2 launch.",
            availableSumInsureds = SI_GLOBAL,
            availableZones = listOf("Pan India"),
            availableFamilyTypes = FAMILY_FULL,
            maxDiscountCap = 0.30,
            rateTableId = "PHI_GLOBAL_PLUS_ASIA",
            minAge = 18, maxAge = 75, isActive = false,
        ),
    )

    private val metaById: Map<String, PlanMeta> = mapOf(
        "PHI_BASIC" to PlanMeta("PHI_BASIC", "UIN: PRUHLIP26001V012526", PlanLifecycle.LIVE, "2026-05-12", "Aman Khanna"),
        "PHI_POSP" to PlanMeta("PHI_POSP", "UIN: PRUHLIP26002V012526", PlanLifecycle.LIVE, "2026-04-30", "Pooja Reddy"),
        "PHI_FLAGSHIP1" to PlanMeta("PHI_FLAGSHIP1", "UIN: PRUHLIP26003V012526", PlanLifecycle.LIVE, "2026-05-18", "Aman Khanna"),
        "PHI_FLAGSHIP2" to PlanMeta("PHI_FLAGSHIP2", "UIN: PRUHLIP26004V012526", PlanLifecycle.LIVE, "2026-05-19", "Pooja Reddy"),
        "PHI_FLAGSHIP3" to PlanMeta("PHI_FLAGSHIP3", "UIN: PRUHLIP25011V032425", PlanLifecycle.RETIRED, "2025-11-22", "Anil Bhatia"),
        "PHI_FLAGSHIP4" to PlanMeta("PHI_FLAGSHIP4", "UIN: PRUHLIP26005V012526", PlanLifecycle.LIVE, "2026-05-15", "Anil Bhatia"),
        "PHI_SENIOR" to PlanMeta("PHI_SENIOR", "UIN: PRUHLIP26006V012526", PlanLifecycle.LIVE, "2026-03-28", "Aman Khanna"),
        "PHI_SUBSTANDARD" to PlanMeta("PHI_SUBSTANDARD", "UIN: PRUHLIP26007V012526", PlanLifecycle.LIVE, "2026-02-14", "Anil Bhatia"),
        "PHI_GLOBAL_EXCL" to PlanMeta("PHI_GLOBAL_EXCL", "UIN: PRUHLIP26008V012526", PlanLifecycle.LIVE, "2026-04-04", "Pooja Reddy"),
        "PHI_GLOBAL_ASIA" to PlanMeta("PHI_GLOBAL_ASIA", "UIN: PRUHLIP26009V012526", PlanLifecycle.LIVE, "2026-04-04", "Pooja Reddy"),
        "PHI_GLOBAL_EUROPE" to PlanMeta("PHI_GLOBAL_EUROPE", "UIN: PRUHLIP26010V012526", PlanLifecycle.LIVE, "2026-04-04", "Pooja Reddy"),
        "PHI_GLOBAL_PLUS" to PlanMeta("PHI_GLOBAL_PLUS", "UIN: PRUHLIP26011V012526", PlanLifecycle.LIVE, "2026-04-25", "Aman Khanna"),
        "PHI_GLOBAL_PLUS_EXCL" to PlanMeta("PHI_GLOBAL_PLUS_EXCL", "UIN: PRUHLIP26012V012526", PlanLifecycle.LIVE, "2026-04-25", "Aman Khanna"),
        "PHI_GLOBAL_PLUS_ASIA" to PlanMeta("PHI_GLOBAL_PLUS_ASIA", "UIN: pending registry", PlanLifecycle.DRAFT, "2026-05-20", "Aman Khanna"),
    )

    fun metaFor(planId: String): PlanMeta =
        metaById[planId] ?: PlanMeta(planId, "UIN: pending", PlanLifecycle.DRAFT, "—", "—")

    /**
     * Synthetic "how many plans use this cover" map.
     *
     * Stable per-cover-id so the table doesn't shuffle between renders. Built by
     * iterating the plan list and checking the allowedCoverIds allowlist. For plans
     * with an empty allowlist (`allowedCoverIds.isEmpty()` = all covers allowed) we
     * include them in the count for every cover.
     */
    val coverUsageCount: Map<String, Int> by lazy {
        val counts = mutableMapOf<String, Int>()
        com.rate.domain.data.CoverCatalog.ALL.forEach { cover ->
            counts[cover.id] = plans.count { plan ->
                plan.allowedCoverIds.isEmpty() || plan.allowedCoverIds.contains(cover.id)
            }
        }
        counts
    }

    /** Synthetic list of plan IDs that include a given cover, for the drawer detail. */
    fun plansForCover(coverId: String): List<String> = plans
        .filter { it.allowedCoverIds.isEmpty() || it.allowedCoverIds.contains(coverId) }
        .map { it.id }

    // ── Synthetic quote ledger (powers Home recent activity + Quote Explorer) ──────

    /**
     * A synthetic quote row. Mirrors the columns that Quote Explorer (Ship-3) actually shows
     * — Home + Quotes both read from this list.
     */
    data class FakeQuote(
        val id: String,
        val createdAt: String,            // ISO-8601 short
        val planId: String,
        val planName: String,
        val primaryAge: Int,
        val sumInsured: Long,
        val familyType: String,
        val zone: String,
        val tenureLabel: String,
        val totalIncludingGst: Double,
        val isValid: Boolean = true,
    )

    /**
     * 50 synthetic quotes spanning every plan, age 25-72, full SI grid, 5 family-type cuts,
     * 5 zones, 5 tenure options. Distribution is intentionally lumpy (PHI_BASIC heaviest)
     * to mirror real funnel concentration. Stable seed so screenshots are reproducible.
     */
    val quotes: List<FakeQuote> by lazy {
        val rng = kotlin.random.Random(seed = 42)
        val zones = ZONES_ALL
        val families = FAMILY_FULL.take(7)
        val tenures = listOf("1 Year", "2 Years", "3 Years", "5 Years")
        val planRoll = listOf(
            "PHI_BASIC", "PHI_BASIC", "PHI_BASIC", "PHI_BASIC", "PHI_POSP",
            "PHI_FLAGSHIP1", "PHI_FLAGSHIP1", "PHI_FLAGSHIP2", "PHI_FLAGSHIP4",
            "PHI_SENIOR", "PHI_SUBSTANDARD",
            "PHI_GLOBAL_EXCL", "PHI_GLOBAL_ASIA", "PHI_GLOBAL_PLUS",
        )
        (1..50).map { i ->
            val planId = planRoll[rng.nextInt(planRoll.size)]
            val plan = plans.first { it.id == planId }
            val age = (rng.nextInt(plan.maxAge - plan.minAge) + plan.minAge).coerceIn(25, 72)
            val si = plan.availableSumInsureds.random(rng)
            val zone = if (plan.availableZones.size == 1) plan.availableZones[0] else zones.random(rng)
            val family = if (plan.availableFamilyTypes.contains("2A2C")) families.random(rng) else "1A"
            val tenure = tenures.random(rng)
            // Synthetic total — proportional to age × SI × family multiplier, plus 18% GST.
            val familyMult = when (family) {
                "1A" -> 1.0; "2A" -> 1.7; "2A1C" -> 2.0; "2A2C" -> 2.25
                "2A3C" -> 2.5; "1A1C" -> 1.35; "1A2C" -> 1.55
                else -> 1.0
            }
            val zoneMult = when (zone) {
                "Zone 1" -> 1.0; "Zone 2" -> 0.88; "Zone 3" -> 0.78
                "Zone 4" -> 0.70; "Pan India" -> 0.95
                else -> 1.0
            }
            val planMult = when (plan.planType.name) {
                "DOMESTIC" -> 1.0; "DOMESTIC_POSP" -> 0.90
                "DOMESTIC_FLAGSHIP" -> 1.35; "DOMESTIC_SENIOR" -> 1.20
                "DOMESTIC_SUBSTANDARD" -> 1.45
                "GLOBAL" -> 2.80; "GLOBAL_PLUS" -> 3.20
                else -> 1.0
            }
            val agePremium = (age * 90.0 + si / 100_000.0 * 320.0)
            val preTax = agePremium * familyMult * zoneMult * planMult * (1 + rng.nextDouble() * 0.04 - 0.02)
            val total = preTax * 1.18
            val isValid = !(planId == "PHI_SENIOR" && age < 60)   // mirror engine validation
            val day = (i % 28) + 1
            val month = ((i / 28) % 5) + 1
            FakeQuote(
                id = "Q-2026${month.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}-${i.toString().padStart(4, '0')}",
                createdAt = "2026-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
                planId = planId,
                planName = plan.name,
                primaryAge = age,
                sumInsured = si,
                familyType = family,
                zone = zone,
                tenureLabel = tenure,
                totalIncludingGst = if (isValid) total else 0.0,
                isValid = isValid,
            )
        }
    }

    /** KPI summary for the Home dashboard. */
    data class HomeKpis(
        val quotesToday: Int,
        val quotesYesterday: Int,
        val gwpThisMonth: Double,        // ₹ pre-tax
        val gwpLastMonth: Double,
        val conversionRatePct: Double,   // 0-100
        val conversionRateDeltaPct: Double,
        val activePlans: Int,
        val draftPlans: Int,
        val retiredPlans: Int,
        val uwBacklog: Int,
        val uwBreaches: Int,
    )

    /**
     * Synthesised but plausible KPI snapshot. Phase 9 will read from the audit_event +
     * quotes table; this is hand-tuned so the Home tiles look real.
     */
    val homeKpis: HomeKpis by lazy {
        HomeKpis(
            quotesToday = 142,
            quotesYesterday = 119,
            gwpThisMonth = 1_47_82_350.0,
            gwpLastMonth = 1_31_05_780.0,
            conversionRatePct = 38.7,
            conversionRateDeltaPct = +2.1,
            activePlans = plans.count { metaFor(it.id).lifecycle == PlanLifecycle.LIVE },
            draftPlans = plans.count { metaFor(it.id).lifecycle == PlanLifecycle.DRAFT },
            retiredPlans = plans.count { metaFor(it.id).lifecycle == PlanLifecycle.RETIRED },
            uwBacklog = 17,
            uwBreaches = 2,
        )
    }
}
