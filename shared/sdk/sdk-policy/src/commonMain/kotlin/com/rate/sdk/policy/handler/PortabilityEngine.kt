package com.rate.sdk.policy.handler

import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.policy.model.PolicySeed
import com.rate.sdk.policy.model.PortabilityRequest
import com.rate.sdk.policy.model.PortabilityResult

/**
 * IRDAI Section 21A portability decision engine. Pure — no IO; the route layer translates the
 * [PortabilityResult] to HTTP and (on acceptance) feeds the [PolicySeed] into the proposal
 * builder. Relocated from the monolith's free `acceptPortability(...)` function.
 */
object PortabilityEngine {

    /**
     * Validate a portability request against the target [plan]. Returns
     * [PortabilityResult.Accepted] (with a [PolicySeed] and 21A compliance notes) when the
     * criteria are met, [PortabilityResult.Rejected] otherwise.
     *
     * Open claims are *flagged for manual underwriting review*, never a hard rejection:
     * Section 21A forbids loading solely because of portability, but the new insurer may still
     * decline on standard medical underwriting.
     */
    fun accept(req: PortabilityRequest, plan: Plan): PortabilityResult {
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()

        if (req.fromInsurer.isBlank()) errors += "Source insurer name is required"
        if (req.fromPolicyNumber.isBlank()) errors += "Source policy number is required"
        if (req.accumulatedWaitingPeriodDays < 0) errors += "Accumulated waiting period cannot be negative"
        if (req.proposedPlanId != plan.id) {
            errors += "Proposed plan ${req.proposedPlanId} mismatches target plan ${plan.id}"
        }
        if (!plan.isActive) errors += "Plan ${plan.id} is not active and cannot accept portability requests"

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
            PortabilityResult.Accepted(
                seed = PolicySeed(
                    planId = plan.id,
                    carryForwardWaitingDays = req.accumulatedWaitingPeriodDays,
                    maternityWaitingSatisfied = req.hasMaternityWaitingSatisfied,
                    portedFromInsurer = req.fromInsurer,
                    portedFromPolicyNumber = req.fromPolicyNumber,
                ),
                complianceNotes = notes,
            )
        } else {
            PortabilityResult.Rejected(errors)
        }
    }
}
