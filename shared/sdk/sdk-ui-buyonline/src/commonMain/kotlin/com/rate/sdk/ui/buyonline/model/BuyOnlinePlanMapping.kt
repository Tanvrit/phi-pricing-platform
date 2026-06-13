package com.rate.sdk.ui.buyonline.model

import com.rate.core.rating.ports.model.CoverParam
import com.rate.core.rating.ports.model.CoverSelection
import com.rate.sdk.rating.model.CoverIds

/**
 * Bridge between the customer-facing "Premier / Signature / Global" tier UI and the actuarial
 * plan ids + cover selections the server-side rating engine understands.
 *
 * RELOCATED from the monolith's `com.rate.domain.buyonline.BuyOnlinePlanMapping`, repackaged
 * onto the core rating contract ([CoverSelection] / [CoverParam]) and sdk-rating's [CoverIds].
 *
 * Why it exists: the buy-online journey is a thin presentation layer over the real engine. The
 * UI VM uses this to translate the customer's tier/add-on picks into the wire shapes that the
 * sdk-proposal `PremiumHandler` / sdk-quoting `QuoteHandler` price server-side. When real
 * IRDAI-approved products gain UINs in the catalog, this mapping is the one place to change.
 */
enum class BuyOnlineTier(val displayName: String) {
    PREMIER("PRU Premier"),
    SIGNATURE("PRU Signature"),
    GLOBAL("PRU Global"),
}

/** Maps a customer-facing tier to the actuarial plan id. */
fun BuyOnlineTier.toPlanId(): String = when (this) {
    BuyOnlineTier.PREMIER   -> "PHI_BASIC"
    BuyOnlineTier.SIGNATURE -> "PHI_FLAGSHIP1"
    BuyOnlineTier.GLOBAL    -> "PHI_GLOBAL1"
}

/** Reverse of [toPlanId] — unknown plan ids fall back to PREMIER. */
fun planIdToTier(planId: String): BuyOnlineTier = when (planId) {
    "PHI_BASIC"     -> BuyOnlineTier.PREMIER
    "PHI_FLAGSHIP1" -> BuyOnlineTier.SIGNATURE
    "PHI_GLOBAL1"   -> BuyOnlineTier.GLOBAL
    else            -> BuyOnlineTier.PREMIER
}

/** Bridge the UI [PlanTier] enum to the mapping's [BuyOnlineTier]. */
fun PlanTier.toBuyOnlineTier(): BuyOnlineTier = when (this) {
    PlanTier.PREMIER   -> BuyOnlineTier.PREMIER
    PlanTier.SIGNATURE -> BuyOnlineTier.SIGNATURE
    PlanTier.GLOBAL    -> BuyOnlineTier.GLOBAL
}

/**
 * Customer-facing add-on catalogue exposed in the journey — a curated subset of the 50+ engine
 * covers. Each [BuyOnlineAddOn] carries the actuarial [coverId] + the [CoverParam] the engine
 * needs, so the same selection drives the priced quote.
 */
data class BuyOnlineAddOn(
    val id: String,
    val coverId: String,
    val title: String,
    val description: String,
    val defaultParam: CoverParam = CoverParam(),
)

val BUYONLINE_ADDONS: List<BuyOnlineAddOn> = listOf(
    BuyOnlineAddOn(
        "maternity", CoverIds.MATERNITY_NEWBORN,
        "Maternity & Newborn",
        "Covers delivery, pre & post-natal care, newborn cover for the first 90 days.",
        CoverParam("50000", "9 Months"),
    ),
    BuyOnlineAddOn(
        "opd", CoverIds.PRE_POST_HOSP,
        "Pre & Post Hospitalisation",
        "Diagnostics, consultations and pharmacy before and after a hospital stay.",
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
        CoverParam("1000000"),
    ),
    BuyOnlineAddOn(
        "critical_illness", CoverIds.CRITICAL_ILLNESS,
        "Critical Illness",
        "₹5L lump-sum payout on diagnosis of 13 critical conditions (cancer, stroke, heart attack…).",
        CoverParam("500000"),
    ),
    BuyOnlineAddOn(
        "daily_cash", CoverIds.DAILY_HOSPITAL_CASH,
        "Daily Hospital Cash",
        "₹1,000/day during hospitalisation to cover incidentals.",
        CoverParam("1000"),
    ),
    BuyOnlineAddOn(
        "fitness", CoverIds.FITNESS_PLUS,
        "Fitness Plus",
        "Gym membership + nutrition consult + fitness tracker partner discounts.",
    ),
)

/** Resolve a set of UI add-on ids → engine cover selections (drops unknown ids). */
fun coverSelectionsFor(addOnIds: Set<String>): List<CoverSelection> =
    BUYONLINE_ADDONS.filter { it.id in addOnIds }.map { CoverSelection(it.coverId, it.defaultParam) }
