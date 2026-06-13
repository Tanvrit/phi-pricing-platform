package com.rate.sdk.policy

import com.rate.core.money.Money
import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.RenewalRateProvider
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanType
import com.rate.sdk.policy.handler.EndorsementEngine
import com.rate.sdk.policy.handler.FreeLookEngine
import com.rate.sdk.policy.handler.Ncb
import com.rate.sdk.policy.handler.PortabilityEngine
import com.rate.sdk.policy.handler.RenewalEngine
import com.rate.sdk.policy.handler.plusDays
import com.rate.sdk.policy.model.Endorsement
import com.rate.sdk.policy.model.Policy
import com.rate.sdk.policy.model.PolicyStatus
import com.rate.sdk.policy.model.PortabilityRequest
import com.rate.sdk.policy.model.PortabilityResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deterministic age-banded base-rate provider for the engine tests. */
private class FakeRenewalRates(
    private val byAge: Map<Int, Double>,
    private val zoneFactor: Map<String, Double> = mapOf("Zone 1" to 1.0),
) : RenewalRateProvider {
    override suspend fun baseFor(
        planId: String,
        age: Int,
        sumInsured: Long,
        familyType: String,
        zone: String,
    ): Double = (byAge[age] ?: 0.0) * (zoneFactor[zone] ?: 1.0)
}

private class FakePlans(private val plan: Plan) : PlanRepository {
    override suspend fun getAllPlans(): List<Plan> = listOf(plan)
    override suspend fun getPlan(id: String): Plan? = plan.takeIf { it.id == id }
    override suspend fun upsertPlan(plan: Plan) {}
    override suspend fun deletePlan(id: String) {}
}

private fun samplePolicy(
    primaryAge: Int = 40,
    tenure: Int = 1,
    claimFreeYears: Int = 0,
    status: PolicyStatus = PolicyStatus.ACTIVE,
    issuedAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
) = Policy(
    id = "POL1",
    uin = "UIN123",
    planId = "PLAN1",
    status = status,
    issuedAt = issuedAt,
    expiresAt = issuedAt.plusDays(365 * tenure),
    holderId = "H1",
    holderMobile = "9876543210",
    proposalId = "PR1",
    currentSumInsured = 1_000_000,
    currentTenure = tenure,
    currentZone = "Zone 1",
    quotedAt = issuedAt,
    freeLookEndsAt = issuedAt.plusDays(15),
    claimFreeYears = claimFreeYears,
    primaryAge = primaryAge,
    familyType = "1A",
)

private val samplePlan = Plan(
    id = "PLAN1",
    name = "Test Plan",
    planType = PlanType.DOMESTIC,
    minAge = 5,
    maxAge = 99,
)

class PolicyLifecycleTest {

    // ── NCB ──────────────────────────────────────────────────────────────────
    @Test
    fun ncb_accrues_and_caps() {
        assertEquals(0.0, Ncb.rate(0))
        assertEquals(0.05, Ncb.rate(1), 1e-9)
        assertEquals(0.50, Ncb.rate(10), 1e-9)
        assertEquals(0.50, Ncb.rate(25), 1e-9) // capped
        assertEquals(50.0, Ncb.discount(10, 100.0), 1e-9)
    }

    // ── PolicyStatus FSM ───────────────────────────────────────────────────────
    @Test
    fun fsm_allows_and_rejects_transitions() {
        assertTrue(PolicyStatus.ACTIVE.canTransitionTo(PolicyStatus.LAPSED))
        assertTrue(PolicyStatus.LAPSED.canTransitionTo(PolicyStatus.REVIVED))
        assertFalse(PolicyStatus.ACTIVE.canTransitionTo(PolicyStatus.DRAFT))
        assertFalse(PolicyStatus.CANCELLED.canTransitionTo(PolicyStatus.ACTIVE))
        assertTrue(PolicyStatus.CANCELLED.terminal)
        assertFalse(PolicyStatus.ACTIVE.terminal)
    }

    // ── RenewalEngine ──────────────────────────────────────────────────────────
    @Test
    fun renewal_quote_applies_ncb_and_age_step_up() = runTest {
        // Age 40 → band 36-40 (min 36). Age 41 → band 41-45 (min 41).
        val rates = FakeRenewalRates(byAge = mapOf(36 to 10_000.0, 41 to 12_000.0, 46 to 14_000.0))
        val engine = RenewalEngine(rates, FakePlans(samplePlan))
        val policy = samplePolicy(primaryAge = 40, tenure = 1, claimFreeYears = 2)

        val r = engine.quote(policy, dueDate = policy.expiresAt, illustrationYears = 2)
        assertTrue(r is RenewalEngine.Result.Success)
        val q = r.quote
        // nextAge = 41 → next-band base 12_000; current-band (36-40) base 10_000.
        assertEquals(10_000.0, q.currentPremium, 1e-6)
        assertEquals(2_000.0, q.ageStepUpAmount, 1e-6) // 12000 - 10000 (band crossed)
        // claimFreeYears=2 → 10% NCB on 12_000 = 1_200.
        assertEquals(0.10, q.ncbPercent, 1e-9)
        assertEquals(1_200.0, q.ncbAmount, 1e-6)
        assertEquals(10_800.0, q.projectedPremium, 1e-6)
        assertEquals(2, q.illustrationLines.size)
        // Year 1: age 41, base 12_000, 10% NCB → 10_800.
        assertEquals(41, q.illustrationLines[0].age)
        assertEquals(10_800.0, q.illustrationLines[0].projectedPremium, 1e-6)
    }

    @Test
    fun renewal_fails_past_exit_age() = runTest {
        val rates = FakeRenewalRates(byAge = mapOf(81 to 50_000.0))
        val plan = samplePlan.copy(maxAge = 80)
        val engine = RenewalEngine(rates, FakePlans(plan))
        val policy = samplePolicy(primaryAge = 80, tenure = 1)
        val r = engine.quote(policy, dueDate = policy.expiresAt)
        assertTrue(r is RenewalEngine.Result.Failure)
    }

    // ── FreeLookEngine ─────────────────────────────────────────────────────────
    @Test
    fun freelook_within_window_refunds_minus_risk_premium() {
        val policy = samplePolicy(tenure = 1)
        val totalPaid = Money.fromRupees(12_000L)
        // Cancel on day 10 of a 365-day tenure.
        val at = policy.issuedAt.plusDays(10)
        assertTrue(FreeLookEngine.isWithinWindow(policy, at))
        val b = FreeLookEngine.refund(policy, at, totalPaid)
        assertTrue(b.withinWindow)
        // risk = 12000 * 10/365 ≈ 328.77 → refund ≈ 11_671.23
        assertEquals(10, b.daysEnjoyed)
        assertTrue(b.refundAmount < totalPaid)
        assertTrue(b.refundAmount.paise > 0)
    }

    @Test
    fun freelook_outside_window_zero_refund() {
        val policy = samplePolicy(tenure = 1)
        val at = policy.issuedAt.plusDays(20)
        assertFalse(FreeLookEngine.isWithinWindow(policy, at))
        val b = FreeLookEngine.refund(policy, at, Money.fromRupees(12_000L))
        assertFalse(b.withinWindow)
        assertEquals(Money.ZERO, b.refundAmount)
    }

    // ── PortabilityEngine ──────────────────────────────────────────────────────
    @Test
    fun portability_accepts_valid_request() {
        val req = PortabilityRequest(
            fromInsurer = "Acme Health",
            fromPolicyNumber = "AC-999",
            accumulatedWaitingPeriodDays = 730,
            hasMaternityWaitingSatisfied = true,
            proposedPlanId = "PLAN1",
            customerName = "Asha",
            customerMobile = "9876543210",
        )
        val res = PortabilityEngine.accept(req, samplePlan)
        assertTrue(res is PortabilityResult.Accepted)
        assertEquals(730, res.seed.carryForwardWaitingDays)
        assertTrue(res.seed.maternityWaitingSatisfied)
        assertTrue(res.complianceNotes.any { it.contains("Section 21A") })
    }

    @Test
    fun portability_rejects_plan_mismatch() {
        val req = PortabilityRequest(
            fromInsurer = "Acme",
            fromPolicyNumber = "X",
            accumulatedWaitingPeriodDays = 0,
            proposedPlanId = "OTHER",
            customerName = "B",
            customerMobile = "9876543210",
        )
        val res = PortabilityEngine.accept(req, samplePlan)
        assertTrue(res is PortabilityResult.Rejected)
    }

    // ── EndorsementEngine ──────────────────────────────────────────────────────
    @Test
    fun endorsement_increase_si_is_prorated_charge() = runTest {
        // Age 40 → band 36-40 (min 36). Base rate scales with SI lookup key.
        val rates = object : RenewalRateProvider {
            override suspend fun baseFor(
                planId: String,
                age: Int,
                sumInsured: Long,
                familyType: String,
                zone: String,
            ): Double = sumInsured / 100.0 // ₹10,000 for 10L; ₹25,000 for 25L
        }
        val engine = EndorsementEngine(rates)
        // Issued 2025-01-01, 1-year tenure. Endorse halfway (~day 182).
        val policy = samplePolicy(primaryAge = 40, tenure = 1)
        val endo = Endorsement.IncreaseSumInsured(
            policyId = policy.id,
            requestedAt = policy.issuedAt.plusDays(182),
            toAmount = Money.fromRupees(2_500_000L),
        )
        val r = engine.price(policy, endo)
        assertTrue(r.isValid)
        assertTrue(r.isCharge)
        // full-term delta = 25000 - 10000 = 15000; pro-rata ≈ (365-182)/365 ≈ 0.5014
        assertEquals(15_000.0, r.fullTermDelta.toRupees(), 1e-6)
        assertTrue(r.netAmount.toRupees() in 7_400.0..7_700.0)
    }

    @Test
    fun endorsement_remove_member_is_refund() = runTest {
        val rates = FakeRenewalRates(byAge = mapOf(36 to 10_000.0))
        val engine = EndorsementEngine(rates)
        val policy = samplePolicy(primaryAge = 40, tenure = 1)
        val endo = Endorsement.RemoveMember(
            policyId = policy.id,
            requestedAt = policy.issuedAt.plusDays(100),
            memberId = 2,
        )
        val r = engine.price(policy, endo)
        assertFalse(r.isCharge)
        assertTrue(r.netAmount.isNegative())
    }

    @Test
    fun endorsement_nominee_change_no_premium_impact() = runTest {
        val rates = FakeRenewalRates(byAge = mapOf(36 to 10_000.0))
        val engine = EndorsementEngine(rates)
        val policy = samplePolicy(primaryAge = 40)
        val endo = Endorsement.NomineeChange(
            policyId = policy.id,
            requestedAt = policy.issuedAt.plusDays(30),
            newNominees = listOf(
                com.rate.sdk.policy.model.Nominee(
                    name = "Spouse",
                    relationship = "Spouse",
                    dob = kotlinx.datetime.LocalDate(1990, 1, 1),
                    percentShare = 100,
                ),
            ),
        )
        val r = engine.price(policy, endo)
        assertTrue(r.isValid)
        assertEquals(Money.ZERO, r.netAmount)
    }
}
