package com.rate.domain.model

import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ────────────────────────────────────────────────────────────────────────────
// Enums
// ────────────────────────────────────────────────────────────────────────────

@Serializable
enum class Tenure(val years: Int, val label: String) {
    ONE_YEAR(1, "1 Year"),
    TWO_YEARS(2, "2 Years"),
    THREE_YEARS(3, "3 Years"),
    FOUR_YEARS(4, "4 Years"),
    FIVE_YEARS(5, "5 Years");

    companion object {
        fun fromLabel(label: String) = entries.first { it.label == label }
        fun fromYears(years: Int) = entries.first { it.years == years }
    }
}

@Serializable
enum class PaymentMode(val label: String, val instalmentLoadingRate: Double) {
    SINGLE_PREMIUM("Single Premium", 0.0),
    MONTHLY("Monthly", 0.06),
    QUARTERLY("Quarterly", 0.04),
    HALF_YEARLY("Half-Yearly", 0.02),
    ANNUAL("Annual", 0.0);

    companion object {
        fun fromLabel(label: String) = entries.first { it.label == label }
    }
}

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

@Serializable
enum class PlanType(val displayName: String) {
    DOMESTIC("Domestic"),
    DOMESTIC_FLAGSHIP("Flagship"),
    DOMESTIC_SENIOR("Senior"),
    DOMESTIC_SUBSTANDARD("Sub Standard"),
    DOMESTIC_POSP("POSP"),
    GLOBAL("Global"),
    GLOBAL_PLUS("Global Plus")
}

/** Geographic coverage scope — differentiates Global plan variants */
@Serializable
enum class GeographyScope(val label: String) {
    DOMESTIC("India"),
    GLOBAL_EXCL_US_CANADA("Worldwide excl. USA & Canada"),
    GLOBAL_ASIA_EXCL_INDIA("Asia excl. India"),
    GLOBAL_EUROPE("Europe"),
    GLOBAL_INCL_US_CANADA("Worldwide incl. USA & Canada")
}

/** Underwriting risk category — determines rate table and co-pay structure */
@Serializable
enum class UnderwritingCategory { STANDARD, SUB_STANDARD, SENIOR }

/** Which co-payment table to use for this plan */
@Serializable
enum class CoPaymentTable { OMNIBUS, SENIOR, SUB_STANDARD }

// ────────────────────────────────────────────────────────────────────────────
// Age bands (from Excel Ref sheet I:J rows 2-16)
// ────────────────────────────────────────────────────────────────────────────

@Serializable
data class AgeBand(
    val minAge: Int,
    val maxAge: Int,   // 999 = no upper bound (85+)
    val label: String
)

val AGE_BANDS = listOf(
    AgeBand(5,  17,  "5 - 17"),
    AgeBand(18, 25,  "18 - 25"),
    AgeBand(26, 30,  "26 - 30"),
    AgeBand(31, 35,  "31 - 35"),
    AgeBand(36, 40,  "36 - 40"),
    AgeBand(41, 45,  "41 - 45"),
    AgeBand(46, 50,  "46 - 50"),
    AgeBand(51, 55,  "51 - 55"),
    AgeBand(56, 60,  "56 - 60"),
    AgeBand(61, 65,  "61 - 65"),
    AgeBand(66, 70,  "66 - 70"),
    AgeBand(71, 75,  "71 - 75"),
    AgeBand(76, 80,  "76 - 80"),
    AgeBand(81, 85,  "81 - 85"),
    AgeBand(86, 999, "86+")
)

/**
 * VLOOKUP approximate-match equivalent — returns the last band whose minAge ≤ age.
 *
 * Throws on out-of-range input so silent miscalculation is impossible. The Excel
 * model only supports ages 5..99 (rate tables go to 86+); we accept a wider band
 * 0..120 to be lenient on input but reject impossible values.
 */
fun getAgeBand(age: Int): AgeBand {
    require(age in 0..120) { "Age $age is outside supported range 0..120" }
    return AGE_BANDS.lastOrNull { it.minAge <= age } ?: AGE_BANDS.first()
}

// ────────────────────────────────────────────────────────────────────────────
// Core domain models
// ────────────────────────────────────────────────────────────────────────────

/** Lifecycle state of a Plan. Persisted server-side; surfaced on the Aegis dashboard. */
enum class PlanLifecycle { LIVE, DRAFT, RETIRED }

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val planType: PlanType,
    val underwritingCategory: UnderwritingCategory = UnderwritingCategory.STANDARD,
    val geographyScope: GeographyScope = GeographyScope.DOMESTIC,
    val coPaymentTable: CoPaymentTable = CoPaymentTable.OMNIBUS,
    val description: String = "",
    val availableSumInsureds: List<Long>,
    val availableZones: List<String>,
    val availableFamilyTypes: List<String>,
    /** Empty = all covers available. Non-empty = explicit allowlist. */
    val allowedCoverIds: Set<String> = emptySet(),
    val maxDiscountCap: Double = 0.30,
    val rateTableId: String = "",        // maps to rate table (defaults to id)
    val minAge: Int = 5,
    val maxAge: Int = 99,
    val isActive: Boolean = true,
    val lifecycle: PlanLifecycle = PlanLifecycle.LIVE,
    /**
     * Goods & Services Tax rate applied to the final premium. India = 18% (HSN 9971)
     * for health insurance. Configurable per-product because exempt / standard / future
     * GST changes can land without code changes.
     */
    val gstRate: Double = 0.18
)

@Serializable
data class Member(
    val memberId: Int,          // 1-based
    val age: Int,
    val relationship: String,   // "Self", "Spouse", "Son", "Daughter"
    val gender: String = "M"    // "M" or "F"
) {
    val isAdult: Boolean get() = age >= 18
}

@Serializable
data class CoverParam(
    val param1: String? = null,
    val param2: String? = null
)

@Serializable
data class CoverSelection(
    val coverId: String,
    val params: CoverParam = CoverParam()
)

@Serializable
data class DiscountSelection(
    val discountId: String,
    val param: String? = null
)

@Serializable
data class QuoteRequest(
    val planId: String,
    val primaryAge: Int,
    val sumInsured: Long,
    val familyType: String,
    val zone: String,
    val tenure: Tenure,
    val paymentMode: PaymentMode,
    val paymentTenure: Tenure,
    val members: List<Member>,
    val selectedCovers: List<CoverSelection> = emptyList(),
    val selectedDiscounts: List<DiscountSelection> = emptyList(),
    val uwLoadingFactor: Double = 0.0,
    val maxDiscountCap: Double = 0.30   // from Plan.maxDiscountCap
)

@Serializable
data class CoverPremiumBreakdown(
    val coverId: String,
    val coverName: String,
    val yearlyPremiums: List<Double>,
    val totalPremium: Double,
    val rateApplied: Double? = null,
    val isDiscount: Boolean = false
)

@Serializable
data class DiscountBreakdown(
    val discountId: String,
    val discountName: String,
    val rateApplied: Double,
    val amount: Double
)

@Serializable
data class YearBreakdown(
    val year: Int,
    val age: Int,
    val ageBand: String,
    val basePremium: Double,
    val coverPremiums: Map<String, Double>,
    val subtotal: Double
)

@Serializable
data class QuoteResult(
    val requestId: String,
    val planId: String,
    val basePremiumTotal: Double,
    val coverBreakdown: List<CoverPremiumBreakdown>,
    val totalAddons: Double,
    val uwLoadingAmount: Double,
    val totalBeforeDiscount: Double,
    val discountBreakdown: List<DiscountBreakdown>,
    val totalDiscountAmount: Double,
    val totalAfterDiscount: Double,
    val instalmentLoadingAmount: Double,
    val instalmentPremium: Double,
    val instalmentCount: Int,
    val yearlyBreakdown: List<YearBreakdown>,
    val isValid: Boolean = true,
    val validationErrors: List<String> = emptyList(),
    // ── Tax (GST / IRDAI) ─────────────────────────────────────────────────
    /** GST rate that was applied (e.g. 0.18 for 18%). */
    val gstRate: Double = 0.18,
    /** Tax amount in rupees, computed on (totalAfterDiscount + instalmentLoadingAmount). */
    val gstAmount: Double = 0.0,
    /** Final amount payable to the customer including GST. THIS is the headline figure. */
    val totalIncludingGst: Double = 0.0,
    // ── Audit / reproducibility ────────────────────────────────────────────
    /** Semver of the pricing engine that produced this result. */
    val engineVersion: String = "1.0.0",
    /** Version tag of the rate-table snapshot used (e.g. "excel-v7.0", "db-2026-05-20"). */
    val rateTableVersion: String = "unknown",
    /** ISO-8601 instant when the calculation ran. */
    val calculatedAt: Instant? = null
)

// ────────────────────────────────────────────────────────────────────────────
// Family type metadata (from Ref sheet L:N)
// ────────────────────────────────────────────────────────────────────────────

data class FamilyTypeInfo(
    val code: String,
    val adultCount: Int,
    val childCount: Int,
    val totalMembers: Int,
    val isFloater: Boolean,
    val isIndividual: Boolean = false
)

val FAMILY_TYPES = listOf(
    FamilyTypeInfo("1A",   1, 0, 1, false, true),
    FamilyTypeInfo("2A",   2, 0, 2, true),
    FamilyTypeInfo("2A1C", 2, 1, 3, true),
    FamilyTypeInfo("2A2C", 2, 2, 4, true),
    FamilyTypeInfo("2A3C", 2, 3, 5, true),
    FamilyTypeInfo("2A4C", 2, 4, 6, true),
    FamilyTypeInfo("1A1C", 1, 1, 2, true),
    FamilyTypeInfo("1A2C", 1, 2, 3, true),
    FamilyTypeInfo("1A3C", 1, 3, 4, true),
    FamilyTypeInfo("1A4C", 1, 4, 5, true),
    FamilyTypeInfo("multi", 2, 0, 2, false, true)
)

/**
 * Throws on unknown family-type codes. Silent fallback to "1A" (the old behaviour) hid
 * typos and routing bugs in upstream code; engine/validation must reject unknown codes
 * cleanly so they surface as validation errors instead of silently wrong premiums.
 */
fun getFamilyTypeInfo(code: String): FamilyTypeInfo =
    FAMILY_TYPES.firstOrNull { it.code == code }
        ?: throw IllegalArgumentException(
            "Unknown family type code '$code'. Valid: ${FAMILY_TYPES.joinToString { it.code }}"
        )

// Sum insured options are now driven entirely by the imported plan data in the database.
// These constants are kept only as a UI fallback display before the first plan loads.
val DOMESTIC_SUM_INSUREDS: List<Long> = emptyList()
val GLOBAL_SUM_INSUREDS: List<Long>   = emptyList()

// ────────────────────────────────────────────────────────────────────────────
// Buy-online save+resume — snapshot of the customer's in-flight journey.
// Persisted server-side keyed by `sessionId`. Captures only the fields that
// take effort to re-enter; payment / KYC / proposal data is excluded for v1
// (sensitive + harder to safely rehydrate). `currentScreen` carries the sealed
// subtype's simple name so the client can resolve it back via a `when`.
// ────────────────────────────────────────────────────────────────────────────
@Serializable
data class BuyOnlineSessionState(
    val sessionId: String,
    val currentScreen: String,
    val mobile: String = "",
    val pincode: String = "",
    val eldestAge: String = "",
    val selectedMembers: List<String> = emptyList(),  // MemberType.name values
    val kidsCount: Int = 0,
    val hasPED: Boolean = false,
    val pedMembers: List<String> = emptyList(),
    val hasCriticalIllness: Boolean = false,
    val criticalIllnessMembers: List<String> = emptyList(),
    val selectedTier: String = "PREMIER",
    val selectedSumInsured: Long = 1_000_000L,
    val selectedTenure: Int = 1,
    val selectedAddOnIds: List<String> = emptyList(),
    val updatedAtIso: String = ""
)
