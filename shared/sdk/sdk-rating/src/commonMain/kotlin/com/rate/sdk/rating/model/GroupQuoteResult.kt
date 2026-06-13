package com.rate.sdk.rating.model

import com.rate.core.money.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-life rating line in a GROUP quote — one row per (grade × age-band) bucket.
 * [bookPremium] is the count × manual book rate; [allocatedPremium] is that life's
 * final share of the group total after size-discount/loading/experience blending.
 */
@Serializable
data class MemberPremiumLine(
    @SerialName("grade") val grade: String,
    @SerialName("ageBandLabel") val ageBandLabel: String,
    @SerialName("ageBandMinAge") val ageBandMinAge: Int,
    @SerialName("lives") val lives: Int,
    @SerialName("sumInsured") val sumInsured: Long,
    /** Manual book rate per life for this bucket (× SI-bucket etc.). */
    @SerialName("ratePerLife") val ratePerLife: Money,
    /** lives × ratePerLife — the bucket's manual book premium. */
    @SerialName("bookPremium") val bookPremium: Money,
    /** Final allocated premium for the bucket after group factors are applied. */
    @SerialName("allocatedPremium") val allocatedPremium: Money,
)

/**
 * Per-grade roll-up of a group quote — the employer-facing band summary (Grade A executives,
 * Grade C staff, …). [allocation] splits the grade premium employer/employee.
 */
@Serializable
data class GradePremiumLine(
    @SerialName("grade") val grade: String,
    @SerialName("totalLives") val totalLives: Int,
    @SerialName("sumInsured") val sumInsured: Long,
    @SerialName("bookPremium") val bookPremium: Money,
    @SerialName("allocatedPremium") val allocatedPremium: Money,
    @SerialName("allocation") val allocation: PremiumAllocation,
)

/**
 * The result of a GROUP quote. Sibling of the retail
 * [com.rate.core.rating.ports.model.QuoteResult] but Money-typed throughout (group figures
 * are exact rupee amounts) and structured around census lives/grades rather than a single
 * proposer's covers.
 *
 * [manualPremium] is the pure book rate; [experiencePremium] the burning-cost figure (zero
 * for [RatingStrategy.Manual]); [blendedPremium] the credibility-weighted result the rest of
 * the totals build on. [total] = blended + gst.
 */
@Serializable
data class GroupQuoteResult(
    @SerialName("requestId") val requestId: String,
    @SerialName("groupConfigId") val groupConfigId: String,
    @SerialName("employerPartyRef") val employerPartyRef: String,
    @SerialName("censusId") val censusId: String,
    @SerialName("totalLives") val totalLives: Int,
    @SerialName("strategy") val strategy: String,

    @SerialName("perMember") val perMember: List<MemberPremiumLine>,
    @SerialName("perGrade") val perGrade: List<GradePremiumLine>,

    /** Group-size discount fraction applied (0..1). */
    @SerialName("groupSizeDiscount") val groupSizeDiscount: Double,
    /** Industry loading fraction applied (0..1+). */
    @SerialName("industryLoading") val industryLoading: Double,

    @SerialName("manualPremium") val manualPremium: Money,
    @SerialName("experiencePremium") val experiencePremium: Money,
    /** Credibility used to blend manual & experience (Z, 0..1). */
    @SerialName("credibility") val credibility: Double,
    @SerialName("blendedPremium") val blendedPremium: Money,

    @SerialName("gstRate") val gstRate: Double = 0.18,
    @SerialName("gst") val gst: Money = Money.ZERO,
    @SerialName("total") val total: Money = Money.ZERO,

    /** Employer/employee split of the [total]. */
    @SerialName("allocation") val allocation: PremiumAllocation,

    @SerialName("isValid") val isValid: Boolean = true,
    @SerialName("validationErrors") val validationErrors: List<String> = emptyList(),
)
