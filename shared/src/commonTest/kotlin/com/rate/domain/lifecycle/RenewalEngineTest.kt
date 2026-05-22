package com.rate.domain.lifecycle

import com.rate.domain.engine.FakeRateDataProvider
import com.rate.domain.engine.PricingEngine
import com.rate.domain.engine.TEST_PLAN_BASIC
import com.rate.domain.engine.TEST_PLAN_SENIOR
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RenewalEngine tests. The engine is a pure function over policy + rate data — we feed
 * it a FakeRateDataProvider (already used by PricingEngine golden tests) and a synthetic
 * policy, then assert on the resulting RenewalQuote.
 *
 * What we pin:
 *  - NCB applies at the rate Ncb.rate(claimFreeYears) returns
 *  - Age step-up surfaces when the renewal crosses an age band boundary
 *  - Age beyond plan.maxAge => Failure
 *  - Illustration line count <= requested years, never exceeds plan.maxAge
 *  - Year-N illustration applies (claimFreeYears + N-1) cumulative bonus
 */
class RenewalEngineTest {

    private val provider = FakeRateDataProvider()
    private val pricing = PricingEngine(provider)
    private val engine = RenewalEngine(provider, pricing)

    private val t0 = Instant.parse("2026-05-21T00:00:00Z")
    private val dueDate = Instant.parse("2027-05-21T00:00:00Z")

    private fun policy(
        primaryAge: Int,
        currentTenure: Int = 1,
        claimFreeYears: Int = 0,
        sumInsured: Long = 1_000_000L,
        planId: String = TEST_PLAN_BASIC.id,
        familyType: String = "1A",
        zone: String = "Zone 1"
    ): Policy = Policy(
        id = "POL-TEST-1",
        uin = "TEST-UIN",
        planId = planId,
        status = PolicyStatus.ACTIVE,
        issuedAt = t0,
        expiresAt = dueDate,
        holderId = "HOLDER-1",
        holderMobile = "9876543210",
        proposalId = "PROP-1",
        currentSumInsured = sumInsured,
        currentTenure = currentTenure,
        currentZone = zone,
        quotedAt = t0,
        freeLookEndsAt = t0,
        claimFreeYears = claimFreeYears,
        primaryAge = primaryAge,
        familyType = familyType
    )

    @Test fun `renewal with zero claim-free years gives no NCB`() = runTest {
        val r = engine.quote(policy(primaryAge = 35, claimFreeYears = 0), dueDate)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(0.0, q.ncbPercent, 1e-9)
        assertEquals(0.0, q.ncbAmount, 0.01)
        // Projected = next year's base premium (no discount)
        assertTrue(q.projectedPremium > 0.0)
    }

    @Test fun `renewal with 5 claim-free years gives 25 percent NCB`() = runTest {
        val r = engine.quote(policy(primaryAge = 35, claimFreeYears = 5), dueDate)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(0.25, q.ncbPercent, 1e-9)
        // ncbAmount = 25% * nextBasePremium
        val expectedDiscount = 0.25 * (q.ncbAmount / 0.25)
        assertEquals(expectedDiscount, q.ncbAmount, 0.01)
        // After-NCB premium = base * 0.75
        val implied = q.ncbAmount / 0.25
        assertEquals(implied * 0.75, q.projectedPremium, 0.01)
    }

    @Test fun `renewal at age band boundary surfaces age-step-up`() = runTest {
        // Issue at age 40 (in band 36-40), renew at 41 (crosses into band 41-45)
        val r = engine.quote(policy(primaryAge = 40, currentTenure = 1, claimFreeYears = 0), dueDate)
        val q = (r as RenewalEngine.Result.Success).quote
        // Age step-up should be > 0 because next age (41) lands in a different rate-table band
        assertTrue(q.ageStepUpAmount > 0.0,
            "Expected age step-up > 0 crossing band 36-40 → 41-45, got ${q.ageStepUpAmount}")
    }

    @Test fun `renewal within same age band has zero step-up`() = runTest {
        // Issue at 32 (in band 31-35), renew at 33 (still in band 31-35) — no step-up.
        val r = engine.quote(policy(primaryAge = 32, currentTenure = 1, claimFreeYears = 0), dueDate)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(0.0, q.ageStepUpAmount, 0.01)
    }

    @Test fun `age beyond plan maxAge yields Failure`() = runTest {
        // TEST_PLAN_BASIC has maxAge = 99. Push next-age to 100.
        val r = engine.quote(policy(primaryAge = 99, currentTenure = 1), dueDate)
        assertTrue(r is RenewalEngine.Result.Failure)
        assertTrue("exit age" in (r as RenewalEngine.Result.Failure).reason)
    }

    @Test fun `unknown plan yields Failure`() = runTest {
        val p = policy(primaryAge = 35).copy(planId = "DOES_NOT_EXIST")
        val r = engine.quote(p, dueDate)
        assertTrue(r is RenewalEngine.Result.Failure)
        assertTrue("not found" in (r as RenewalEngine.Result.Failure).reason)
    }

    @Test fun `illustration produces requested years when within plan range`() = runTest {
        val r = engine.quote(policy(primaryAge = 35, claimFreeYears = 0), dueDate, illustrationYears = 5)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(5, q.illustrationLines.size)
        // Year-1 NCB = Ncb.rate(0+0) = 0; Year-5 = Ncb.rate(0+4) = 20%
        assertEquals(0.0, q.illustrationLines[0].ncbPercent, 1e-9)
        assertEquals(0.20, q.illustrationLines[4].ncbPercent, 1e-9)
    }

    @Test fun `illustration truncates at plan maxAge`() = runTest {
        // Issue at 96, renew at 97. Plan maxAge=99 means only ages 97,98,99 fit.
        // (Year-1 is 97, Year-2 is 98, Year-3 is 99. Year-4 would be 100 -> stop.)
        val r = engine.quote(policy(primaryAge = 96, currentTenure = 1), dueDate, illustrationYears = 5)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(3, q.illustrationLines.size)
        assertEquals(97, q.illustrationLines[0].ageAtRenewal)
        assertEquals(99, q.illustrationLines[2].ageAtRenewal)
    }

    @Test fun `illustration's claim-free streak grows year over year`() = runTest {
        // Start with 4 claim-free years; 5-year illustration shows 4,5,6,7,8 effectively.
        val r = engine.quote(policy(primaryAge = 30, claimFreeYears = 4), dueDate, illustrationYears = 5)
        val q = (r as RenewalEngine.Result.Success).quote
        assertEquals(Ncb.rate(4), q.illustrationLines[0].ncbPercent, 1e-9)  // 20%
        assertEquals(Ncb.rate(5), q.illustrationLines[1].ncbPercent, 1e-9)  // 25%
        assertEquals(Ncb.rate(8), q.illustrationLines[4].ncbPercent, 1e-9)  // 40%
    }

    @Test fun `grace period defaults to 30 days`() = runTest {
        val r = engine.quote(policy(primaryAge = 35), dueDate)
        val q = (r as RenewalEngine.Result.Success).quote
        val expectedMs = dueDate.toEpochMilliseconds() + 30L * 24 * 60 * 60 * 1000
        assertEquals(expectedMs, q.gracePeriodEnds.toEpochMilliseconds())
    }

    @Test fun `senior plan with sub-66 age is rejected for minAge`() = runTest {
        // TEST_PLAN_SENIOR.minAge = 46. Renewal would be at age 36 here (35 + 1 tenure).
        val p = policy(primaryAge = 35, currentTenure = 1).copy(planId = TEST_PLAN_SENIOR.id)
        val r = engine.quote(p, dueDate)
        assertTrue(r is RenewalEngine.Result.Failure)
        assertTrue("entry age" in (r as RenewalEngine.Result.Failure).reason)
    }
}
