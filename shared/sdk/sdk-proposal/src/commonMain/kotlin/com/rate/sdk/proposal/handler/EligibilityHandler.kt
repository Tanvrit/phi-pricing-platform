package com.rate.sdk.proposal.handler

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.time.Now
import com.rate.sdk.proposal.event.ProposalEvent
import com.rate.sdk.proposal.event.ProposalEventSink
import com.rate.sdk.proposal.model.journey.EligibilityRequest
import com.rate.sdk.proposal.model.journey.EligibilityResult

/**
 * Eligibility check for the buy-online journey: a member is excluded from cover when they declared
 * a pre-existing disease (PED) OR a critical illness.
 *
 * RELOCATED from the monolith's `BuyOnlineRoutes` `/eligibility` route (the exclusion `filter`
 * was inline in the Ktor handler). Lifted to a pure, transport-agnostic handler so the same rule
 * is unit-testable and reusable by the operator console — no IO, runs on every KMP target.
 *
 * The 20-member cap from the route is preserved as a fail-fast guard.
 */
class EligibilityHandler(
    private val events: ProposalEventSink = ProposalEventSink.NOOP,
    private val maxMembers: Int = 20,
) {

    /** Evaluate eligibility; returns a [DomainError.Validation] when the member set is too large. */
    suspend fun evaluate(request: EligibilityRequest): AppResult<EligibilityResult> {
        if (request.members.size > maxMembers) {
            return AppResult.Err(
                DomainError.Validation(listOf("Cannot evaluate more than $maxMembers members.")),
            )
        }
        val result = pure(request)
        events.emit(
            ProposalEvent.EligibilityChecked(
                mobile = request.mobile,
                coveredCount = result.coveredMembers.size,
                uncoveredCount = result.uncoveredMembers.size,
                at = Now.instant(),
            ),
        )
        return AppResult.Ok(result)
    }

    /**
     * The pure exclusion rule (no IO, no events) — exposed so callers that already validated the
     * member count (e.g. proposal assembly) can reuse the decision directly.
     */
    fun pure(request: EligibilityRequest): EligibilityResult {
        val uncovered = request.members.filter { member ->
            (request.hasPED && member in request.pedMembers) ||
                (request.hasCriticalIllness && member in request.criticalIllnessMembers)
        }
        val covered = request.members.filter { it !in uncovered }
        return EligibilityResult(coveredMembers = covered, uncoveredMembers = uncovered)
    }
}
