package com.rate.core.rating.ports.model

import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.id.newId
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ────────────────────────────────────────────────────────────────────────────
// Enums (relocated from the monolithic shared domain — the rating contract)
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
enum class PlanType(val displayName: String) {
    DOMESTIC("Domestic"),
    DOMESTIC_FLAGSHIP("Flagship"),
    DOMESTIC_SENIOR("Senior"),
    DOMESTIC_SUBSTANDARD("Sub Standard"),
    DOMESTIC_POSP("POSP"),
    GLOBAL("Global"),
    GLOBAL_PLUS("Global Plus"),
}

@Serializable
enum class GeographyScope(val label: String) {
    DOMESTIC("India"),
    GLOBAL_EXCL_US_CANADA("Worldwide excl. USA & Canada"),
    GLOBAL_ASIA_EXCL_INDIA("Asia excl. India"),
    GLOBAL_EUROPE("Europe"),
    GLOBAL_INCL_US_CANADA("Worldwide incl. USA & Canada"),
}

@Serializable
enum class UnderwritingCategory { STANDARD, SUB_STANDARD, SENIOR }

@Serializable
enum class CoPaymentTable { OMNIBUS, SENIOR, SUB_STANDARD }

@Serializable
enum class PlanLifecycle { LIVE, DRAFT, RETIRED }

/** Retail vs Group line of business — first-class discriminator threaded through the platform. */
@Serializable
enum class ProductLine { RETAIL, GROUP }

// ────────────────────────────────────────────────────────────────────────────
// Plan — admin-CRUD config entity AND the rating anchor (engine reads it).
// ────────────────────────────────────────────────────────────────────────────
@Serializable
data class Plan(
    @SerialName("_id") override val id: String = newId(),
    val name: String,
    val planType: PlanType,
    val productLine: ProductLine = ProductLine.RETAIL,
    val underwritingCategory: UnderwritingCategory = UnderwritingCategory.STANDARD,
    val geographyScope: GeographyScope = GeographyScope.DOMESTIC,
    val coPaymentTable: CoPaymentTable = CoPaymentTable.OMNIBUS,
    val description: String = "",
    val availableSumInsureds: List<Long> = emptyList(),
    val availableZones: List<String> = emptyList(),
    val availableFamilyTypes: List<String> = emptyList(),
    /** Empty = all covers available. Non-empty = explicit allowlist. */
    val allowedCoverIds: Set<String> = emptySet(),
    val maxDiscountCap: Double = 0.30,
    val rateTableId: String = "",
    val minAge: Int = 5,
    val maxAge: Int = 99,
    val isActive: Boolean = true,
    val lifecycle: PlanLifecycle = PlanLifecycle.LIVE,
    val gstRate: Double = 0.18,
    // ── Catalog cross-references (additive; GROUP wiring) ───────────────────
    /** Links this plan tier to a CriticalIllnessList. */
    @SerialName("ciListRef") val ciListRef: String? = null,
    /** References to grade definitions applicable to this plan. */
    @SerialName("gradeRefs") val gradeRefs: List<String> = emptyList(),
    /** Reference to the eligibility rule-set governing this plan. */
    @SerialName("eligibilityRef") val eligibilityRef: String? = null,
    /** References to benefit schedules attached to this plan. */
    @SerialName("benefitScheduleRefs") val benefitScheduleRefs: List<String> = emptyList(),
    /** Version tag of the rate set this plan resolves against. */
    @SerialName("rateVersion") val rateVersion: String? = null,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity

// ────────────────────────────────────────────────────────────────────────────
// Quote request / result model (Double throughout for engine parity).
// ────────────────────────────────────────────────────────────────────────────
@Serializable
data class Member(
    val memberId: Int,
    val age: Int,
    val relationship: String,
    val gender: String = "M",
) {
    val isAdult: Boolean get() = age >= 18
}

@Serializable
data class CoverParam(val param1: String? = null, val param2: String? = null)

@Serializable
data class CoverSelection(val coverId: String, val params: CoverParam = CoverParam())

@Serializable
data class DiscountSelection(val discountId: String, val param: String? = null)

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
    val maxDiscountCap: Double = 0.30,
)

@Serializable
data class CoverPremiumBreakdown(
    val coverId: String,
    val coverName: String,
    val yearlyPremiums: List<Double>,
    val totalPremium: Double,
    val rateApplied: Double? = null,
    val isDiscount: Boolean = false,
)

@Serializable
data class DiscountBreakdown(
    val discountId: String,
    val discountName: String,
    val rateApplied: Double,
    val amount: Double,
)

@Serializable
data class YearBreakdown(
    val year: Int,
    val age: Int,
    val ageBand: String,
    val basePremium: Double,
    val coverPremiums: Map<String, Double>,
    val subtotal: Double,
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
    val gstRate: Double = 0.18,
    val gstAmount: Double = 0.0,
    val totalIncludingGst: Double = 0.0,
    val engineVersion: String = "1.1.0",
    val rateTableVersion: String = "unknown",
    val calculatedAt: Instant? = null,
)

/**
 * Renewal illustration row. Lives in core (not sdk-policy) so the IRDAI doc builders in
 * sdk-quoting can consume it without a same-layer sdk→sdk dependency.
 */
@Serializable
data class RenewalIllustrationLine(
    val year: Int,
    val age: Int,
    val projectedPremium: Double,
    val ncbPercent: Double = 0.0,
    val ncbAmount: Double = 0.0,
)
