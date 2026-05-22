package com.rate.domain.buyonline

import com.rate.domain.model.CoverIds
import com.rate.domain.model.CoverParam
import com.rate.domain.model.CoverSelection

/**
 * Bridge between the customer-facing "Premier / Signature / Global" tier UI in the
 * buy-online journey and the actuarial plan IDs the PricingEngine knows about.
 *
 * Why this exists: pre-Foundation-Pack the buy-online module had its own hardcoded
 * pricing (`estimatedPremium()` with arbitrary multipliers 1.0/1.25/1.80 and arbitrary
 * SI-bucketed base premiums). That diverged from both the server engine and from the
 * Excel actuarial table. This mapping makes the buy-online a thin presentation layer
 * over the real engine.
 *
 * When real IRDAI-approved products gain UINs in the Product Catalog, this mapping
 * is the one place that needs to change.
 */
enum class BuyOnlineTier(val displayName: String) {
    PREMIER("PRU Premier"),
    SIGNATURE("PRU Signature"),
    GLOBAL("PRU Global")
}

/** Maps a customer-facing tier to the actuarial plan ID. */
fun BuyOnlineTier.toPlanId(): String = when (this) {
    BuyOnlineTier.PREMIER   -> "PHI_BASIC"
    BuyOnlineTier.SIGNATURE -> "PHI_FLAGSHIP1"
    BuyOnlineTier.GLOBAL    -> "PHI_GLOBAL1"
}

/**
 * Customer-facing add-on bundle for each tier — defined in terms of real CoverIds so
 * the same selection drives the actuarial engine. Signature & Global ship with the
 * bundle pre-selected; Premier ships empty (customer chooses).
 *
 * Each CoverSelection carries the param that the engine needs (e.g. maternity needs
 * an amount + waiting-period).
 */
fun BuyOnlineTier.defaultAddOnCovers(): List<CoverSelection> = when (this) {
    BuyOnlineTier.PREMIER -> emptyList()
    BuyOnlineTier.SIGNATURE -> listOf(
        CoverSelection(CoverIds.MATERNITY_NEWBORN, CoverParam("50000", "9 Months")),
        CoverSelection(CoverIds.CASHLESS_OPD, CoverParam("5000")),
        CoverSelection(CoverIds.PRE_POST_HOSP),
        CoverSelection(CoverIds.HOME_CARE),
    )
    BuyOnlineTier.GLOBAL -> listOf(
        CoverSelection(CoverIds.MATERNITY_NEWBORN, CoverParam("100000", "9 Months")),
        CoverSelection(CoverIds.CASHLESS_OPD, CoverParam("10000")),
        CoverSelection(CoverIds.PRE_POST_HOSP),
        CoverSelection(CoverIds.HOME_CARE),
        CoverSelection(CoverIds.AIR_AMBULANCE),
        CoverSelection(CoverIds.SECOND_OPINION),
        CoverSelection(CoverIds.ENHANCED_GEO, CoverParam("Worldwide excl. USA & Canada")),
    )
}

/**
 * Customer-facing add-on catalogue exposed in BuyOnline — a curated subset of the
 * 50+ engine covers. Real Phase 2 work expands this to the full catalogue with proper
 * IRDAI-mandated explanations, waiting periods, sub-limits, etc.
 */
data class BuyOnlineAddOn(
    val id: String,
    val coverId: String,
    val title: String,
    val description: String,
    val defaultParam: CoverParam = CoverParam()
)

val BUYONLINE_ADDONS: List<BuyOnlineAddOn> = listOf(
    BuyOnlineAddOn(
        "maternity", CoverIds.MATERNITY_NEWBORN,
        "Maternity & Newborn",
        "Covers delivery, pre & post-natal care, newborn cover for the first 90 days.",
        CoverParam("50000", "9 Months")
    ),
    BuyOnlineAddOn(
        "opd", CoverIds.CASHLESS_OPD,
        "Cashless OPD",
        "Consultation, diagnostics, pharmacy at PRU Network providers — no upfront payment.",
        CoverParam("5000")
    ),
    BuyOnlineAddOn(
        "home_care", CoverIds.HOME_CARE,
        "Home Care",
        "Hospitalisation-grade treatment from home for eligible conditions.",
    ),
    BuyOnlineAddOn(
        "air_ambulance", CoverIds.AIR_AMBULANCE,
        "Air Ambulance",
        "Emergency air evacuation to the nearest network hospital.",
    ),
    BuyOnlineAddOn(
        "personal_accident", CoverIds.PERSONAL_ACCIDENT,
        "Personal Accident",
        "Lump-sum benefit on accidental death or permanent disability.",
        CoverParam("1000000")
    ),
    BuyOnlineAddOn(
        "critical_illness", CoverIds.CRITICAL_ILLNESS,
        "Critical Illness",
        "₹5L lump-sum payout on diagnosis of 13 critical conditions (cancer, stroke, heart attack…).",
        CoverParam("500000")
    ),
    BuyOnlineAddOn(
        "daily_cash", CoverIds.DAILY_HOSPITAL_CASH,
        "Daily Hospital Cash",
        "₹1,000/day during hospitalisation to cover incidentals.",
        CoverParam("1000")
    ),
    BuyOnlineAddOn(
        "fitness", CoverIds.FITNESS_PLUS,
        "Fitness Plus",
        "Gym membership + nutrition consult + fitness tracker partner discounts.",
    ),
)
