package com.rate.sdk.policy.model

import com.rate.core.rating.ports.model.RenewalIllustrationLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Output of [com.rate.sdk.policy.handler.RenewalEngine.quote] — everything the customer /
 * agent needs to decide on a renewal. All money is Double (INR), gross of GST, consistent
 * with the rating engine's parity convention; conversion to [com.rate.core.money.Money]
 * happens at display assembly.
 *
 * - `currentPremium`     : base premium of the expiring policy (current age band).
 * - `projectedPremium`   : what they would pay on renewal (post-NCB).
 * - `ageStepUpAmount`    : portion of the delta attributable to crossing an age band.
 * - `illustrationLines`  : year-by-year forward projection (the IRDAI sales-illustration
 *                          rows, modelled with the shared core [RenewalIllustrationLine]).
 *
 * `RenewalIllustrationLine` lives in `core` (not here) so the IRDAI document builders in
 * sdk-quoting can consume it without a same-layer sdk→sdk dependency.
 */
@Serializable
data class RenewalQuote(
    @SerialName("policyId") val policyId: String,
    @SerialName("dueDate") val dueDate: Instant,
    @SerialName("gracePeriodEnds") val gracePeriodEnds: Instant,
    @SerialName("currentPremium") val currentPremium: Double,
    @SerialName("projectedPremium") val projectedPremium: Double,
    @SerialName("ncbPercent") val ncbPercent: Double,
    @SerialName("ncbAmount") val ncbAmount: Double,
    @SerialName("ageStepUpAmount") val ageStepUpAmount: Double,
    @SerialName("illustrationLines") val illustrationLines: List<RenewalIllustrationLine>,
    @SerialName("isValid") val isValid: Boolean = true,
    @SerialName("validationErrors") val validationErrors: List<String> = emptyList(),
)
