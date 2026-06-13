package com.rate.core.regulatory

import kotlinx.serialization.Serializable

// ────────────────────────────────────────────────────────────────────────────
// Geographic zone (Tier-1..4 + Pan-India). Resolution from pincode lives in catalog.
// ────────────────────────────────────────────────────────────────────────────
@Serializable
enum class Zone(val label: String) {
    ZONE_1("Zone 1"),
    ZONE_2("Zone 2"),
    ZONE_3("Zone 3"),
    ZONE_4("Zone 4"),
    PAN_INDIA("Pan India");

    companion object {
        fun fromLabel(label: String) = entries.first { it.label == label }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Age bands (Excel Ref sheet I:J rows 2-16). VLOOKUP approximate-match equivalent.
// ────────────────────────────────────────────────────────────────────────────
@Serializable
data class AgeBand(val minAge: Int, val maxAge: Int, val label: String)

val AGE_BANDS = listOf(
    AgeBand(5, 17, "5 - 17"),
    AgeBand(18, 25, "18 - 25"),
    AgeBand(26, 30, "26 - 30"),
    AgeBand(31, 35, "31 - 35"),
    AgeBand(36, 40, "36 - 40"),
    AgeBand(41, 45, "41 - 45"),
    AgeBand(46, 50, "46 - 50"),
    AgeBand(51, 55, "51 - 55"),
    AgeBand(56, 60, "56 - 60"),
    AgeBand(61, 65, "61 - 65"),
    AgeBand(66, 70, "66 - 70"),
    AgeBand(71, 75, "71 - 75"),
    AgeBand(76, 80, "76 - 80"),
    AgeBand(81, 85, "81 - 85"),
    AgeBand(86, 999, "86+"),
)

/** Returns the last band whose minAge ≤ age. Rejects impossible ages so misuse surfaces. */
fun getAgeBand(age: Int): AgeBand {
    require(age in 0..120) { "Age $age is outside supported range 0..120" }
    return AGE_BANDS.lastOrNull { it.minAge <= age } ?: AGE_BANDS.first()
}

// ────────────────────────────────────────────────────────────────────────────
// Family-type metadata (Ref sheet L:N). Retail floater/individual constructs.
// ────────────────────────────────────────────────────────────────────────────
data class FamilyTypeInfo(
    val code: String,
    val adultCount: Int,
    val childCount: Int,
    val totalMembers: Int,
    val isFloater: Boolean,
    val isIndividual: Boolean = false,
)

val FAMILY_TYPES = listOf(
    FamilyTypeInfo("1A", 1, 0, 1, false, true),
    FamilyTypeInfo("2A", 2, 0, 2, true),
    FamilyTypeInfo("2A1C", 2, 1, 3, true),
    FamilyTypeInfo("2A2C", 2, 2, 4, true),
    FamilyTypeInfo("2A3C", 2, 3, 5, true),
    FamilyTypeInfo("2A4C", 2, 4, 6, true),
    FamilyTypeInfo("1A1C", 1, 1, 2, true),
    FamilyTypeInfo("1A2C", 1, 2, 3, true),
    FamilyTypeInfo("1A3C", 1, 3, 4, true),
    FamilyTypeInfo("1A4C", 1, 4, 5, true),
    FamilyTypeInfo("multi", 2, 0, 2, false, true),
)

fun getFamilyTypeInfo(code: String): FamilyTypeInfo =
    FAMILY_TYPES.firstOrNull { it.code == code }
        ?: throw IllegalArgumentException(
            "Unknown family type code '$code'. Valid: ${FAMILY_TYPES.joinToString { it.code }}",
        )

// ────────────────────────────────────────────────────────────────────────────
// Cover payout semantics (drives benefit modelling + group PA/CI schedules).
// ────────────────────────────────────────────────────────────────────────────
@Serializable
enum class PayoutType { FIXED, INDEMNITY, SERVICE, PERCENT }

@Serializable
enum class CoverageLogic { BASE_SI, PART_OF_BASE_SI, OVER_AND_ABOVE }

// ────────────────────────────────────────────────────────────────────────────
// IRDAI / tax numeric constants. Defaults only — admin-tunable copies live in config.
// ────────────────────────────────────────────────────────────────────────────
object Gst {
    /** HSN 9971 health insurance GST. */
    const val DEFAULT_RATE: Double = 0.18
}

object DiscountCap {
    /** Aggregate discount hard cap. */
    const val DEFAULT_MAX: Double = 0.30
}

object Irdai {
    const val FREE_LOOK_DAYS: Int = 15
    const val GRACE_PERIOD_DAYS_ANNUAL: Int = 30
    const val NCB_STEP: Double = 0.05
    const val NCB_MAX: Double = 0.50
}

/** UIN registry value-type (admin-managed copies persist in catalog `uins`). */
@Serializable
data class Uin(
    val value: String,
    val productName: String,
    val planId: String,
    val isStandardProduct: Boolean = false,
)
