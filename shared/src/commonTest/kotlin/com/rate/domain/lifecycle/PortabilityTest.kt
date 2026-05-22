package com.rate.domain.lifecycle

import com.rate.domain.engine.TEST_PLAN_BASIC
import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests Section 21A portability acceptance — happy path + the 4 rejection modes.
 */
class PortabilityTest {

    private fun req(
        planId: String = TEST_PLAN_BASIC.id,
        days: Int = 730,                 // ~2 years served
        fromInsurer: String = "Star Health",
        fromPolicyNumber: String = "SH-123456",
        maternitySatisfied: Boolean = false,
        claims: List<ClaimRecord> = emptyList()
    ) = PortabilityRequest(
        fromInsurer = fromInsurer,
        fromPolicyNumber = fromPolicyNumber,
        accumulatedWaitingPeriodDays = days,
        claimsHistory = claims,
        hasMaternityWaitingSatisfied = maternitySatisfied,
        proposedPlanId = planId,
        customerName = "Rohan Sharma",
        customerMobile = "9876543210"
    )

    @Test fun `valid request is Accepted`() {
        val r = acceptPortability(req(), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Accepted)
        val acc = r as PortabilityResult.Accepted
        assertEquals(730, acc.seed.carryForwardWaitingDays)
        assertEquals("Star Health", acc.seed.portedFromInsurer)
        assertTrue(acc.complianceNotes.any { "Section 21A" in it })
    }

    @Test fun `blank source insurer is Rejected`() {
        val r = acceptPortability(req(fromInsurer = ""), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Rejected)
        assertTrue((r as PortabilityResult.Rejected).reasons.any { "Source insurer" in it })
    }

    @Test fun `blank source policy number is Rejected`() {
        val r = acceptPortability(req(fromPolicyNumber = ""), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Rejected)
        assertTrue((r as PortabilityResult.Rejected).reasons.any { "Source policy number" in it })
    }

    @Test fun `negative accumulated waiting days Rejected`() {
        val r = acceptPortability(req(days = -10), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Rejected)
        assertTrue((r as PortabilityResult.Rejected).reasons.any { "cannot be negative" in it })
    }

    @Test fun `plan id mismatch Rejected`() {
        val r = acceptPortability(req(planId = "WRONG_PLAN"), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Rejected)
        assertTrue((r as PortabilityResult.Rejected).reasons.any { "mismatches" in it })
    }

    @Test fun `inactive plan Rejected`() {
        val inactive = TEST_PLAN_BASIC.copy(isActive = false)
        val r = acceptPortability(req(planId = inactive.id), inactive)
        assertTrue(r is PortabilityResult.Rejected)
        assertTrue((r as PortabilityResult.Rejected).reasons.any { "not active" in it })
    }

    @Test fun `maternity-satisfied carries through`() {
        val r = acceptPortability(req(maternitySatisfied = true), TEST_PLAN_BASIC)
        val acc = r as PortabilityResult.Accepted
        assertTrue(acc.seed.maternityWaitingSatisfied)
        assertTrue(acc.complianceNotes.any { "Maternity" in it && "satisfied" in it })
    }

    @Test fun `open claims surfaced as UW note, not rejection`() {
        val openClaim = ClaimRecord(
            claimId = "C1", policyId = "POL-OLD",
            intimatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            amount = Money.fromRupees(50_000.0), cause = "Hospitalisation",
            isSettled = false
        )
        val r = acceptPortability(req(claims = listOf(openClaim)), TEST_PLAN_BASIC)
        assertTrue(r is PortabilityResult.Accepted, "Open claims should not auto-reject")
        val acc = r as PortabilityResult.Accepted
        assertTrue(acc.complianceNotes.any { "open claim" in it.lowercase() && "UW review" in it })
    }

    @Test fun `multiple errors all surfaced together`() {
        val r = acceptPortability(req(fromInsurer = "", days = -5), TEST_PLAN_BASIC)
        val rej = r as PortabilityResult.Rejected
        assertTrue(rej.reasons.size >= 2,
            "Both blank-insurer and negative-days should be reported, got ${rej.reasons}")
    }
}
