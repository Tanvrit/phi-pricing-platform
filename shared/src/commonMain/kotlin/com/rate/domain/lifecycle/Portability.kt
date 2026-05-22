package com.rate.domain.lifecycle

import com.rate.domain.model.Plan
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
 * `accumulatedWaitingPeriodDays` is the total of all served waiting-period days the
 * customer brings with them (PED, specific illness, etc.). The new policy honours up to
 * the lesser of the equivalent waiting period on the new plan minus what's been served.
 */
@Serializable
data class PortabilityRequest(
    val fromInsurer: String,
    val fromPolicyNumber: String,
    val accumulatedWaitingPeriodDays: Int,
    val claimsHistory: List<ClaimRecord> = emptyList(),
    val hasMaternityWaitingSatisfied: Boolean = false,
    val proposedPlanId: String,
    val customerName: String,
    val customerMobile: String
)

/** Result of [acceptPortability]. Pure data — the route layer translates this to HTTP. */
sealed class PortabilityResult {
    data class Accepted(val seed: PolicySeed, val complianceNotes: List<String>) : PortabilityResult()
    data class Rejected(val reasons: List<String>) : PortabilityResult()
}

/**
 * Validate a portability request against the target plan. Pure function — no IO.
 *
 * Returns [PortabilityResult.Accepted] (with a [PolicySeed] the proposal builder consumes)
 * if Section 21A compliance criteria are met, [PortabilityResult.Rejected] otherwise.
 */
fun acceptPortability(req: PortabilityRequest, plan: Plan): PortabilityResult {
    val errors = mutableListOf<String>()
    val notes  = mutableListOf<String>()

    if (req.fromInsurer.isBlank()) errors += "Source insurer name is required"
    if (req.fromPolicyNumber.isBlank()) errors += "Source policy number is required"
    if (req.accumulatedWaitingPeriodDays < 0) errors += "Accumulated waiting period cannot be negative"
    if (req.proposedPlanId != plan.id) errors += "Proposed plan ${req.proposedPlanId} mismatches target plan ${plan.id}"
    if (!plan.isActive) errors += "Plan ${plan.id} is not active and cannot accept portability requests"

    // Open claims that exceed a year of premium are flagged for underwriting review,
    // not a hard rejection (Section 21A explicitly forbids loading solely because of
    // portability, but the new insurer may decline on standard medical underwriting).
    val openClaims = req.claimsHistory.count { !it.isSettled }
    if (openClaims > 0) notes += "$openClaims open claim(s) carried over — manual UW review required"

    if (req.hasMaternityWaitingSatisfied) {
        notes += "Maternity waiting period treated as satisfied (carried over from ${req.fromInsurer})"
    } else {
        notes += "Maternity waiting period applies afresh per plan defaults"
    }

    notes += "Credit ${req.accumulatedWaitingPeriodDays} day(s) of pre-existing-disease " +
        "waiting period from ${req.fromInsurer} (Section 21A)"

    return if (errors.isEmpty()) {
        // audit:event_record_here (lifecycle.portability.accepted)
        PortabilityResult.Accepted(
            seed = PolicySeed(
                planId                   = plan.id,
                carryForwardWaitingDays  = req.accumulatedWaitingPeriodDays,
                maternityWaitingSatisfied = req.hasMaternityWaitingSatisfied,
                portedFromInsurer        = req.fromInsurer,
                portedFromPolicyNumber   = req.fromPolicyNumber
            ),
            complianceNotes = notes
        )
    } else {
        PortabilityResult.Rejected(errors)
    }
}
