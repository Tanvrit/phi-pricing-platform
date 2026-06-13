package com.rate.sdk.rating.model

/**
 * Cover ID constants — the engine rate-row contract.
 *
 * Relocated VERBATIM from the monolith's `com.rate.domain.model.CoverIds`. These stable
 * string codes are the [com.rate.core.rating.ports.model.CoverSelection.coverId] values the
 * [com.rate.sdk.rating.handler.PricingEngine] looks up in [RateDataProvider]. They are also
 * the codes carried by each seed [com.rate.sdk.catalog.model.Cover.code] in sdk-catalog
 * (the catalog owns display/CRUD metadata; the engine owns the rate-row dispatch).
 *
 * Kept HERE (rating, not catalog) because the engine's row-order calculation and
 * accumulation bases ([COVER_ACCUM_BASES]) are a tight unit — parity with Excel
 * Rate_Calculator_v7.0 depends on these exact codes and the exact SUM() ranges.
 */
object CoverIds {
    // Base (virtual, always present)
    const val BASE = "base"

    // Accumulating % covers — rows 7-30 (Excel Calculator sheet)
    const val DAY1_INSTANT               = "day1_instant"            // row 7
    const val LOYALTY_BONUS              = "loyalty_bonus"           // row 8
    const val DOUBLE_COVER_7YR           = "double_cover_7yr"        // row 9
    const val CHRONIC_INSTANT            = "chronic_instant"         // row 10
    const val CONSUMABLES_LIST1          = "consumables_list1"       // row 11
    const val PED_WAITING                = "ped_waiting"             // row 12 (yr1 only)
    const val SPECIFIC_ILLNESS_WAITING   = "specific_illness_waiting"// row 13
    const val MODERN_TREATMENT_PLUS      = "modern_treatment_plus"   // row 14
    const val ROOM_RENT_MOD              = "room_rent_mod"           // row 15
    const val DISEASE_SUBLIMIT           = "disease_sublimit"        // row 16
    const val PRE_POST_HOSP              = "pre_post_hosp"           // row 17
    const val CONSUMABLE_PLUS            = "consumable_plus"         // row 18
    const val HOME_CARE                  = "home_care"               // row 19 — FLAT (excluded from most bases)
    const val INFINITE_CLAIM             = "infinite_claim"          // row 20
    const val RESTORATION_PLUS           = "restoration_plus"        // row 21
    const val DONOR_PLUS                 = "donor_plus"              // row 22 — FLAT × members
    const val SPOUSE_PROTECT             = "spouse_protect"          // row 23
    const val DURABLE_MEDICAL            = "durable_medical"         // row 24
    const val TENURE_WISE                = "tenure_wise"             // row 25
    const val SMART_SELECT               = "smart_select"            // row 26 — discount
    const val GOOD_HEALTH                = "good_health"             // row 27
    const val PER_CLAIM_DEDUCTIBLE       = "per_claim_deductible"    // row 28 — discount
    const val AGGREGATE_DEDUCTIBLE       = "aggregate_deductible"    // row 29 — discount
    const val CO_PAY                     = "co_pay"                  // row 30 — discount

    // Flat / member-level covers — rows 31-51
    const val CHILD_PROTECT              = "child_protect"           // row 31 — FLAT
    const val DAILY_HOSPITAL_CASH        = "daily_hospital_cash"     // row 32 — member-level
    const val CONVALESCENCE              = "convalescence"           // row 33
    const val COMPASSIONATE              = "compassionate"           // row 34
    const val PERSONAL_ACCIDENT          = "personal_accident"       // row 35 — member-level
    const val AIR_AMBULANCE              = "air_ambulance"           // row 36 — FLAT
    const val FITNESS_PLUS               = "fitness_plus"            // row 37 — FLAT
    const val WELLNESS_PACKAGE           = "wellness_package"        // row 38 — FLAT
    const val SECOND_OPINION             = "second_opinion"          // row 39 — FLAT
    const val MATERNITY_NEWBORN          = "maternity_newborn"       // row 40
    const val POST_DELIVERY_CARE         = "post_delivery_care"      // row 41 — FLAT
    const val INFERTILITY                = "infertility"             // row 42
    const val SURROGATE_MOTHER           = "surrogate_mother"        // row 43 — FLAT
    const val OOCYTE_DONOR               = "oocyte_donor"            // row 44 — FLAT
    const val POST_DISCHARGE_CARE        = "post_discharge_care"     // row 45 — FLAT
    const val ADVENTURE_SPORTS           = "adventure_sports"        // row 46 — FLAT × adults
    const val CHRONIC_MANAGEMENT         = "chronic_management"      // row 47 — member-level
    const val FEMALE_VACCINATION         = "female_vaccination"      // row 48 — FLAT × females
    const val PRU_HEALTH_SPECIALIST      = "pru_health_specialist"   // row 49 — FLAT
    const val ADVANCE_HEALTH_CHECKUP     = "advance_health_checkup"  // row 50 — FLAT × adults
    const val CASHLESS_OPD               = "cashless_opd"            // row 51

    // Pre-calculated rows (needed by 52 / 56)
    const val CRITICAL_ILLNESS           = "critical_illness"        // row 54 — member-level
    const val MATERNITY_FIXED            = "maternity_fixed"         // row 55
    const val CANCER_BOOSTER             = "cancer_booster"          // row 57 — per-year age/SI factor
    const val CANCER_SCREENING           = "cancer_screening"        // row 58 — per age band

    // Post covers referencing forward rows
    const val PRUDENTIAL_HEALTHY         = "prudential_healthy"      // row 52
    const val PREMIUM_RETURN             = "premium_return"          // row 53
    const val ENHANCED_GEO               = "enhanced_geo"            // row 56

    // Underwriting loading
    const val UW_LOADING                 = "uw_loading"              // row 59

    // Discounts (rows 64-70)
    const val DISC_TENURE                = "disc_tenure"
    const val DISC_EMPLOYEE              = "disc_employee"
    const val DISC_NRI                   = "disc_nri"
    const val DISC_AUTO_DEBIT            = "disc_auto_debit"
    const val DISC_COMMISSION_LIEU       = "disc_commission_lieu"
    const val DISC_GMC                   = "disc_gmc"
    const val DISC_CIBIL                 = "disc_cibil"
    const val DISC_MULTI_MEMBER          = "disc_multi_member"
}

// ────────────────────────────────────────────────────────────────────────────
// Accumulation bases — exact SUM() ranges from each Excel row's formula.
// Each entry = list of cover IDs whose year-premium is summed to form the
// multiplication base for that cover. Relocated VERBATIM from the monolith's
// `com.rate.domain.model.CoverDefinitions.COVER_ACCUM_BASES`.
// ────────────────────────────────────────────────────────────────────────────

private val R7_BASE  = listOf(CoverIds.BASE)
private val R8_BASE  = listOf(CoverIds.BASE, CoverIds.DAY1_INSTANT)
private val R9_BASE  = listOf(CoverIds.BASE, CoverIds.DAY1_INSTANT, CoverIds.LOYALTY_BONUS)
private val R10_BASE = listOf(CoverIds.BASE, CoverIds.DAY1_INSTANT, CoverIds.LOYALTY_BONUS, CoverIds.DOUBLE_COVER_7YR)
private val R11_BASE = R10_BASE + CoverIds.CHRONIC_INSTANT
private val R12_BASE = R11_BASE + CoverIds.CONSUMABLES_LIST1
private val R13_BASE = R12_BASE + CoverIds.PED_WAITING
private val R14_BASE = R13_BASE + CoverIds.SPECIFIC_ILLNESS_WAITING
private val R15_BASE = R14_BASE + CoverIds.MODERN_TREATMENT_PLUS
private val R16_BASE = R15_BASE + CoverIds.ROOM_RENT_MOD
private val R17_BASE = R16_BASE + CoverIds.DISEASE_SUBLIMIT
private val R18_BASE = R17_BASE + CoverIds.PRE_POST_HOSP
// R19 (HOME_CARE) = FLAT — no accum base
// R20 skips HOME_CARE
private val R20_BASE = R18_BASE + CoverIds.CONSUMABLE_PLUS   // = base..r18, skips r19
// R21 also skips HOME_CARE
private val R21_BASE = R20_BASE + CoverIds.INFINITE_CLAIM
// R22 (DONOR_PLUS) = FLAT*members — no accum base
// R23 includes HOME_CARE + DONOR_PLUS
private val R23_BASE = R21_BASE + listOf(CoverIds.HOME_CARE, CoverIds.RESTORATION_PLUS, CoverIds.DONOR_PLUS)
// R24 includes HOME_CARE but skips DONOR_PLUS and SPOUSE_PROTECT (SPOUSE_PROTECT is calculated separately)
private val R24_BASE = R21_BASE + listOf(CoverIds.HOME_CARE, CoverIds.RESTORATION_PLUS)
// R25 (TENURE_WISE) accumulates all rows 4-24 — add DONOR_PLUS + SPOUSE_PROTECT + DURABLE_MEDICAL
private val R25_BASE = R24_BASE + listOf(CoverIds.DONOR_PLUS, CoverIds.SPOUSE_PROTECT, CoverIds.DURABLE_MEDICAL)
// R26 skips HOME_CARE
private val R26_BASE = R20_BASE + listOf(CoverIds.INFINITE_CLAIM, CoverIds.RESTORATION_PLUS, CoverIds.DONOR_PLUS, CoverIds.SPOUSE_PROTECT, CoverIds.TENURE_WISE)
// R27 = SUM(X25:X26, X20:X21, X7:X18, X4) = rows {4,7-18,20,21,25,26}
// Explicitly skips HOME_CARE(19), DONOR_PLUS(22), SPOUSE_PROTECT(23), DURABLE_MEDICAL(24)
private val R27_BASE = R20_BASE + listOf(CoverIds.INFINITE_CLAIM, CoverIds.RESTORATION_PLUS, CoverIds.TENURE_WISE, CoverIds.SMART_SELECT)
// Full accumulation through row 27 (for R28 onwards: SUM(X7-X27, X4))
private val FULL_THROUGH_R27 = R25_BASE + listOf(CoverIds.TENURE_WISE, CoverIds.SMART_SELECT, CoverIds.GOOD_HEALTH)
private val R28_BASE = FULL_THROUGH_R27
private val R29_BASE = R28_BASE + CoverIds.PER_CLAIM_DEDUCTIBLE
private val R30_BASE = R29_BASE + CoverIds.AGGREGATE_DEDUCTIBLE

// Rows 31-51 flat covers — accumulated by R52
private val FLAT_COVER_IDS = listOf(
    CoverIds.CHILD_PROTECT, CoverIds.DAILY_HOSPITAL_CASH, CoverIds.CONVALESCENCE,
    CoverIds.COMPASSIONATE, CoverIds.PERSONAL_ACCIDENT, CoverIds.AIR_AMBULANCE,
    CoverIds.FITNESS_PLUS, CoverIds.WELLNESS_PACKAGE, CoverIds.SECOND_OPINION,
    CoverIds.MATERNITY_NEWBORN, CoverIds.POST_DELIVERY_CARE, CoverIds.INFERTILITY,
    CoverIds.SURROGATE_MOTHER, CoverIds.OOCYTE_DONOR, CoverIds.POST_DISCHARGE_CARE,
    CoverIds.ADVENTURE_SPORTS, CoverIds.CHRONIC_MANAGEMENT, CoverIds.FEMALE_VACCINATION,
    CoverIds.PRU_HEALTH_SPECIALIST, CoverIds.ADVANCE_HEALTH_CHECKUP, CoverIds.CASHLESS_OPD
)

// R57 = factor × SUM(base, r7-r30)
private val R57_BASE = R30_BASE + CoverIds.CO_PAY

// R52 = factor × SUM(base, r7-r51, r54, r57, r58)
private val R52_BASE = R57_BASE + FLAT_COVER_IDS + listOf(CoverIds.CRITICAL_ILLNESS, CoverIds.CANCER_BOOSTER, CoverIds.CANCER_SCREENING)

// R53 = factor × SUM(base, r7-r52) = rows {4,7-51,52}
// X52's cell VALUE already embeds rows 54/57/58 — do NOT add them again explicitly
private val R53_BASE = R57_BASE + FLAT_COVER_IDS + listOf(CoverIds.PRUDENTIAL_HEALTHY)

// R56 = factor × SUM(base, r7-r55, r57, r58)
private val R56_BASE = R57_BASE + FLAT_COVER_IDS + listOf(CoverIds.CRITICAL_ILLNESS, CoverIds.MATERNITY_FIXED, CoverIds.PRUDENTIAL_HEALTHY, CoverIds.PREMIUM_RETURN, CoverIds.CANCER_BOOSTER, CoverIds.CANCER_SCREENING)

// R59 UW Loading base (from Excel formula)
private val R59_BASE = R30_BASE + listOf(
    CoverIds.CO_PAY, CoverIds.CHILD_PROTECT, CoverIds.DAILY_HOSPITAL_CASH,
    CoverIds.CONVALESCENCE, CoverIds.COMPASSIONATE,
    CoverIds.AIR_AMBULANCE, CoverIds.SECOND_OPINION, CoverIds.ADVENTURE_SPORTS,
    CoverIds.CHRONIC_MANAGEMENT, CoverIds.PRU_HEALTH_SPECIALIST,
    CoverIds.CRITICAL_ILLNESS, CoverIds.PRUDENTIAL_HEALTHY,
    CoverIds.PREMIUM_RETURN, CoverIds.ENHANCED_GEO, CoverIds.CANCER_BOOSTER
)

/** Maps each cover ID → ordered list of cover IDs that form its multiplication base. */
val COVER_ACCUM_BASES: Map<String, List<String>> = mapOf(
    CoverIds.DAY1_INSTANT             to R7_BASE,
    CoverIds.LOYALTY_BONUS           to R8_BASE,
    CoverIds.DOUBLE_COVER_7YR        to R9_BASE,
    CoverIds.CHRONIC_INSTANT         to R10_BASE,
    CoverIds.CONSUMABLES_LIST1       to R11_BASE,
    CoverIds.PED_WAITING             to R12_BASE,
    CoverIds.SPECIFIC_ILLNESS_WAITING to R13_BASE,
    CoverIds.MODERN_TREATMENT_PLUS   to R14_BASE,
    CoverIds.ROOM_RENT_MOD           to R15_BASE,
    CoverIds.DISEASE_SUBLIMIT        to R16_BASE,
    CoverIds.PRE_POST_HOSP           to R17_BASE,
    CoverIds.CONSUMABLE_PLUS         to R18_BASE,
    CoverIds.INFINITE_CLAIM          to R20_BASE,
    CoverIds.RESTORATION_PLUS        to R21_BASE,
    CoverIds.SPOUSE_PROTECT          to R23_BASE,
    CoverIds.DURABLE_MEDICAL         to R24_BASE,
    CoverIds.TENURE_WISE             to R25_BASE,
    CoverIds.SMART_SELECT            to R26_BASE,
    CoverIds.GOOD_HEALTH             to R27_BASE,
    CoverIds.PER_CLAIM_DEDUCTIBLE    to R28_BASE,
    CoverIds.AGGREGATE_DEDUCTIBLE    to R29_BASE,
    CoverIds.CO_PAY                  to R30_BASE,
    CoverIds.CANCER_BOOSTER          to R57_BASE,
    CoverIds.PRUDENTIAL_HEALTHY      to R52_BASE,
    CoverIds.PREMIUM_RETURN          to R53_BASE,
    CoverIds.ENHANCED_GEO            to R56_BASE,
    CoverIds.UW_LOADING              to R59_BASE
)
