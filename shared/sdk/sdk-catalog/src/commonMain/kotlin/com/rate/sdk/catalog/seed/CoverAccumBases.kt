package com.rate.sdk.catalog.seed

/**
 * Relocated `com.rate.domain.model.CoverDefinitions.COVER_ACCUM_BASES` — the exact SUM()
 * ranges from each Excel Calculator-sheet row. Each entry = cover code → ordered list of cover
 * codes whose year-premium is summed to form that cover's multiplication base.
 *
 * These become the [com.rate.sdk.catalog.model.Cover.accumBase] field of each seed Cover, so
 * the rating engine dispatches on data instead of hardcoded row numbers. The "base" virtual
 * cover is the always-present base premium row.
 */
internal object CoverAccumBases {

    private const val BASE = "base"

    // Accumulating-% covers (rows 7-30) — progressive accumulation.
    private val R7  = listOf(BASE)
    private val R8  = listOf(BASE, "day1_instant")
    private val R9  = listOf(BASE, "day1_instant", "loyalty_bonus")
    private val R10 = listOf(BASE, "day1_instant", "loyalty_bonus", "double_cover_7yr")
    private val R11 = R10 + "chronic_instant"
    private val R12 = R11 + "consumables_list1"
    private val R13 = R12 + "ped_waiting"
    private val R14 = R13 + "specific_illness_waiting"
    private val R15 = R14 + "modern_treatment_plus"
    private val R16 = R15 + "room_rent_mod"
    private val R17 = R16 + "disease_sublimit"
    private val R18 = R17 + "pre_post_hosp"
    // R19 (home_care) = FLAT — no accum base; R20 skips it.
    private val R20 = R18 + "consumable_plus"
    private val R21 = R20 + "infinite_claim"
    // R22 (donor_plus) = FLAT × members — no accum base.
    private val R23 = R21 + listOf("home_care", "restoration_plus", "donor_plus")
    private val R24 = R21 + listOf("home_care", "restoration_plus")
    private val R25 = R24 + listOf("donor_plus", "spouse_protect", "durable_medical")
    private val R26 = R20 + listOf("infinite_claim", "restoration_plus", "donor_plus", "spouse_protect", "tenure_wise")
    private val R27 = R20 + listOf("infinite_claim", "restoration_plus", "tenure_wise", "smart_select")
    private val FULL_THROUGH_R27 = R25 + listOf("tenure_wise", "smart_select", "good_health")
    private val R28 = FULL_THROUGH_R27
    private val R29 = R28 + "per_claim_deductible"
    private val R30 = R29 + "aggregate_deductible"

    private val FLAT_COVERS = listOf(
        "child_protect", "daily_hospital_cash", "convalescence", "compassionate",
        "personal_accident", "air_ambulance", "fitness_plus", "wellness_package",
        "second_opinion", "maternity_newborn", "post_delivery_care", "infertility",
        "surrogate_mother", "oocyte_donor", "post_discharge_care", "adventure_sports",
        "chronic_management", "female_vaccination", "pru_health_specialist",
        "advance_health_checkup", "cashless_opd",
    )

    private val R57 = R30 + "co_pay"
    private val R52 = R57 + FLAT_COVERS + listOf("critical_illness", "cancer_booster", "cancer_screening")
    private val R53 = R57 + FLAT_COVERS + listOf("prudential_healthy")
    private val R56 = R57 + FLAT_COVERS + listOf(
        "critical_illness", "maternity_fixed", "prudential_healthy", "premium_return",
        "cancer_booster", "cancer_screening",
    )
    private val R59 = R30 + listOf(
        "co_pay", "child_protect", "daily_hospital_cash", "convalescence", "compassionate",
        "air_ambulance", "second_opinion", "adventure_sports", "chronic_management",
        "pru_health_specialist", "critical_illness", "prudential_healthy", "premium_return",
        "enhanced_geo", "cancer_booster",
    )

    /** Cover code → accumulation base (ordered list of cover codes). */
    val byCoverCode: Map<String, List<String>> = mapOf(
        "day1_instant" to R7,
        "loyalty_bonus" to R8,
        "double_cover_7yr" to R9,
        "chronic_instant" to R10,
        "consumables_list1" to R11,
        "ped_waiting" to R12,
        "specific_illness_waiting" to R13,
        "modern_treatment_plus" to R14,
        "room_rent_mod" to R15,
        "disease_sublimit" to R16,
        "pre_post_hosp" to R17,
        "consumable_plus" to R18,
        "infinite_claim" to R20,
        "restoration_plus" to R21,
        "spouse_protect" to R23,
        "durable_medical" to R24,
        "tenure_wise" to R25,
        "smart_select" to R26,
        "good_health" to R27,
        "per_claim_deductible" to R28,
        "aggregate_deductible" to R29,
        "co_pay" to R30,
        "cancer_booster" to R57,
        "prudential_healthy" to R52,
        "premium_return" to R53,
        "enhanced_geo" to R56,
        "uw_loading" to R59,
    )
}
