package com.rate.domain.lifecycle

import com.rate.domain.engine.PricingEngine
import com.rate.domain.model.*
import com.rate.domain.repository.RateDataProvider
import kotlinx.datetime.Instant

/**
 * Pure renewal calculator.
 *
 * Given an in-force policy and a [RateDataProvider], computes the next-year renewal
 * quote and a 1..5-year forward illustration. The engine does NOT consult the database
 * or any IO — it is a pure function over the policy + the rate provider. This keeps it
 * trivial to unit-test and reproducible for audit.
 *
 * NCB logic: 5% per claim-free renewal year, capped at 50% (see [Ncb]). The age-band
 * step-up is the year-over-year jump in base premium attributable to crossing an
 * actuarial age band (e.g. moving from 36-40 to 41-45).
 *
 * Phase 5 scope: produces the *quote*, not the bound renewal. Persisting the renewal
 * (and writing the audit event) is a separate stream.
 */
class RenewalEngine(
    private val rateData: RateDataProvider,
    private val pricingEngine: PricingEngine
) {

    sealed class Result {
        data class Success(val quote: RenewalQuote) : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * Compute the renewal quote.
     *
     * @param policy           the in-force policy being renewed
     * @param dueDate          contractual renewal date (= policy.expiresAt)
     * @param gracePeriodDays  IRDAI grace period for individual health — 30 days standard
     * @param illustrationYears how many years of forward projection to include (max 5)
     */
    suspend fun quote(
        policy: Policy,
        dueDate: Instant,
        gracePeriodDays: Int = 30,
        illustrationYears: Int = 5
    ): Result {
        val plan = rateData.getPlan(policy.planId)
            ?: return Result.Failure("Plan ${policy.planId} not found")

        // Renewal at the *next* policy year's primary age
        val nextAge = policy.primaryAge + policy.currentTenure
        if (nextAge > plan.maxAge) {
            return Result.Failure("Age exceeds plan exit age (plan.maxAge=${plan.maxAge})")
        }
        if (nextAge < plan.minAge) {
            return Result.Failure("Age below plan entry age (plan.minAge=${plan.minAge})")
        }

        val ncbPct  = Ncb.rate(policy.claimFreeYears)

        // Current-band base premium (what the policy was issued at) — used to surface the
        // age step-up amount cleanly when the band changes on renewal.
        val curBand     = getAgeBand(policy.primaryAge + policy.currentTenure - 1)
        val nextBand    = getAgeBand(nextAge)
        val currentBasePremium = rateData.getBasePremium(
            policy.planId, policy.familyType, policy.currentZone,
            curBand.minAge, policy.currentSumInsured
        )
        val nextBasePremium = rateData.getBasePremium(
            policy.planId, policy.familyType, policy.currentZone,
            nextBand.minAge, policy.currentSumInsured
        )
        val ageStepUp = if (curBand.minAge != nextBand.minAge)
            (nextBasePremium - currentBasePremium) else 0.0

        val ncbAmount   = ncbPct * nextBasePremium
        val projected   = nextBasePremium - ncbAmount

        // Forward illustration — same assumption: claim-free continuation, plan stays the same.
        // NB: for years > 1 we increment claimFreeYears, capping at the NCB max naturally.
        val years = illustrationYears.coerceIn(1, 5)
        val lines = mutableListOf<RenewalIllustrationLine>()
        for (i in 0 until years) {
            val age = nextAge + i
            if (age > plan.maxAge) break
            val band = getAgeBand(age)
            val basePrem = rateData.getBasePremium(
                policy.planId, policy.familyType, policy.currentZone,
                band.minAge, policy.currentSumInsured
            )
            val freeYears = policy.claimFreeYears + i
            val pct = Ncb.rate(freeYears)
            val amt = pct * basePrem
            lines += RenewalIllustrationLine(
                policyYear           = i + 1,
                ageAtRenewal         = age,
                ageBand              = band.label,
                projectedBasePremium = basePrem,
                ncbPercent           = pct,
                ncbAmount            = amt,
                projectedTotalPremium = basePrem - amt
            )
        }

        // audit:event_record_here (lifecycle.renewal.quote_generated)
        return Result.Success(
            RenewalQuote(
                policyId        = policy.id,
                dueDate         = dueDate,
                gracePeriodEnds = dueDate.plusDays(gracePeriodDays),
                currentPremium  = currentBasePremium,
                projectedPremium = projected,
                ncbPercent      = ncbPct,
                ncbAmount       = ncbAmount,
                ageStepUpAmount = ageStepUp,
                illustrationLines = lines
            )
        )
    }
}

/** Tiny KMP-safe Instant + days helper used by renewal grace-period math. */
internal fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + days.toLong() * 24L * 60L * 60L * 1000L)
