package com.rate.sdk.policy.handler

import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.RenewalRateProvider
import com.rate.core.rating.ports.model.RenewalIllustrationLine
import com.rate.core.regulatory.Irdai
import com.rate.core.regulatory.getAgeBand
import com.rate.sdk.policy.model.Policy
import com.rate.sdk.policy.model.RenewalQuote
import kotlinx.datetime.Instant

/**
 * Pure renewal calculator.
 *
 * Given an in-force [Policy], computes the next-year renewal quote and a 1..5-year forward
 * illustration. The engine performs NO IO of its own beyond the two injected PORTs and is a
 * pure function over the policy + the providers — trivial to unit-test and reproducible for
 * audit.
 *
 * Relocated from the monolith's `lifecycle/RenewalEngine`. Two deliberate changes:
 *  1. The unused `pricingEngine: PricingEngine` constructor param was removed (verified dead).
 *  2. It depends on the [RenewalRateProvider] PORT for base-rate lookups (NOT the concrete
 *     `RateDataProvider`/`PricingEngine`), inverting the cross-layer edge. Plan entry/exit ages
 *     come from the [PlanRepository] PORT.
 *
 * NCB logic: 5% per claim-free renewal year, capped at 50% (see [Ncb]). The age-band step-up
 * is the year-over-year jump in base premium attributable to crossing an actuarial age band
 * (e.g. moving from 36-40 to 41-45).
 *
 * Scope: produces the *quote*, not the bound renewal. Persisting the renewal and writing the
 * audit event is the handler/route layer's job.
 */
class RenewalEngine(
    private val rates: RenewalRateProvider,
    private val plans: PlanRepository,
) {

    sealed class Result {
        data class Success(val quote: RenewalQuote) : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * Compute the renewal quote.
     *
     * @param policy            the in-force policy being renewed
     * @param dueDate           contractual renewal date (= `policy.expiresAt`)
     * @param gracePeriodDays   IRDAI grace period for individual health — 30 days standard
     * @param illustrationYears how many years of forward projection to include (clamped 1..5)
     */
    suspend fun quote(
        policy: Policy,
        dueDate: Instant,
        gracePeriodDays: Int = Irdai.GRACE_PERIOD_DAYS_ANNUAL,
        illustrationYears: Int = 5,
    ): Result {
        val plan = plans.getPlan(policy.planId)
            ?: return Result.Failure("Plan ${policy.planId} not found")

        // Renewal at the *next* policy year's primary age.
        val nextAge = policy.primaryAge + policy.currentTenure
        if (nextAge > plan.maxAge) {
            return Result.Failure("Age $nextAge exceeds plan exit age (plan.maxAge=${plan.maxAge})")
        }
        if (nextAge < plan.minAge) {
            return Result.Failure("Age $nextAge below plan entry age (plan.minAge=${plan.minAge})")
        }

        val ncbPct = Ncb.rate(policy.claimFreeYears)

        // Current-band vs next-band base premium — used to surface the age step-up cleanly
        // when the band changes on renewal. Both looked up at the band's min age so the
        // step-up isolates ONLY the band crossing, not within-band drift.
        val curAge = nextAge - 1
        val curBand = getAgeBand(curAge)
        val nextBand = getAgeBand(nextAge)
        val currentBasePremium = rates.baseFor(
            planId = policy.planId,
            age = curBand.minAge,
            sumInsured = policy.currentSumInsured,
            familyType = policy.familyType,
            zone = policy.currentZone,
        )
        val nextBasePremium = rates.baseFor(
            planId = policy.planId,
            age = nextBand.minAge,
            sumInsured = policy.currentSumInsured,
            familyType = policy.familyType,
            zone = policy.currentZone,
        )
        val ageStepUp =
            if (curBand.minAge != nextBand.minAge) (nextBasePremium - currentBasePremium) else 0.0

        val ncbAmount = ncbPct * nextBasePremium
        val projected = nextBasePremium - ncbAmount

        // Forward illustration — claim-free continuation, plan/SI/family held constant. For
        // years > 1 the claim-free streak increments, naturally capping at the NCB ceiling.
        val years = illustrationYears.coerceIn(1, 5)
        val lines = mutableListOf<RenewalIllustrationLine>()
        for (i in 0 until years) {
            val age = nextAge + i
            if (age > plan.maxAge) break
            val band = getAgeBand(age)
            val basePrem = rates.baseFor(
                planId = policy.planId,
                age = band.minAge,
                sumInsured = policy.currentSumInsured,
                familyType = policy.familyType,
                zone = policy.currentZone,
            )
            val freeYears = policy.claimFreeYears + i
            val pct = Ncb.rate(freeYears)
            val amt = pct * basePrem
            lines += RenewalIllustrationLine(
                year = i + 1,
                age = age,
                projectedPremium = basePrem - amt,
                ncbPercent = pct,
                ncbAmount = amt,
            )
        }

        return Result.Success(
            RenewalQuote(
                policyId = policy.id,
                dueDate = dueDate,
                gracePeriodEnds = dueDate.plusDays(gracePeriodDays),
                currentPremium = currentBasePremium,
                projectedPremium = projected,
                ncbPercent = ncbPct,
                ncbAmount = ncbAmount,
                ageStepUpAmount = ageStepUp,
                illustrationLines = lines,
            ),
        )
    }
}

/** Tiny KMP-safe Instant + days helper used by renewal grace-period math. */
internal fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + days.toLong() * 24L * 60L * 60L * 1000L)
