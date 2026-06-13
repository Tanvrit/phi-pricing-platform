package com.rate.sdk.policy.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * IRDAI Section 21A portability request — a customer porting from a competitor to us.
 *
 * Per Section 21A:
 * - Waiting periods already served at the previous insurer must be credited.
 * - Maternity waiting, if satisfied, transfers as-satisfied.
 * - The request must be submitted at least 45 days before the existing policy's renewal.
 * - The new insurer must respond within 15 days; otherwise acceptance is implied.
 *
 * `accumulatedWaitingPeriodDays` is the total of all served waiting-period days the customer
 * brings with them (PED, specific illness, etc.). The new policy honours up to the lesser of
 * the equivalent waiting period on the new plan minus what has been served.
 *
 * The validation/decision logic lives in [com.rate.sdk.policy.handler.PortabilityEngine].
 */
@Serializable
data class PortabilityRequest(
    @SerialName("fromInsurer") val fromInsurer: String,
    @SerialName("fromPolicyNumber") val fromPolicyNumber: String,
    @SerialName("accumulatedWaitingPeriodDays") val accumulatedWaitingPeriodDays: Int,
    @SerialName("claimsHistory") val claimsHistory: List<ClaimRecord> = emptyList(),
    @SerialName("hasMaternityWaitingSatisfied") val hasMaternityWaitingSatisfied: Boolean = false,
    @SerialName("proposedPlanId") val proposedPlanId: String,
    @SerialName("customerName") val customerName: String,
    @SerialName("customerMobile") val customerMobile: String,
)

/**
 * Outcome of [com.rate.sdk.policy.handler.PortabilityEngine.accept]. Pure data — the server
 * route layer translates this to HTTP.
 */
sealed class PortabilityResult {
    /** Accepted: the [seed] is consumed by the proposal builder; [complianceNotes] surface 21A credits. */
    data class Accepted(val seed: PolicySeed, val complianceNotes: List<String>) : PortabilityResult()
    data class Rejected(val reasons: List<String>) : PortabilityResult()
}
