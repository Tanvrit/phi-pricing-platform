package com.rate.sdk.policy.handler

import com.rate.core.money.Money
import com.rate.core.money.toMoney
import com.rate.core.rating.ports.RenewalRateProvider
import com.rate.core.regulatory.getAgeBand
import com.rate.sdk.policy.model.Endorsement
import com.rate.sdk.policy.model.EndorsementResult
import com.rate.sdk.policy.model.Nominee
import com.rate.sdk.policy.model.Policy
import kotlinx.datetime.Instant

/**
 * Prices mid-term [Endorsement]s pro-rata over the unexpired tenure. Pure over the injected
 * [RenewalRateProvider] PORT (base-rate lookups) — no IO, no concrete engine. Relocated &
 * fleshed out from the monolith's `Endorsement` (which carried only the rationale strings; the
 * rupee math lived in a TODO server service). Net-new here.
 *
 * Pro-rata convention: `chargeOrRefund = fullTermDelta × (daysRemaining / tenureDays)`.
 *  - A SI increase / member add yields a positive charge.
 *  - A member removal yields a refund (negative net amount).
 *  - An address change charges only the zone-premium delta (zero if the zone is unchanged).
 *  - A nominee change has no premium impact.
 */
class EndorsementEngine(
    private val rates: RenewalRateProvider,
) {

    private companion object {
        const val DAYS_PER_YEAR = 365
    }

    /** Price [endorsement] against its owning [policy]. */
    suspend fun price(policy: Policy, endorsement: Endorsement): EndorsementResult {
        require(endorsement.policyId == policy.id) {
            "Endorsement.policyId ${endorsement.policyId} != policy ${policy.id}"
        }

        val tenureDays = policy.currentTenure * DAYS_PER_YEAR
        val daysRemaining = daysRemaining(policy, endorsement.requestedAt, tenureDays)
        val errors = mutableListOf<String>()
        if (!policy.isInForce) errors += "Policy ${policy.id} is not in force (status=${policy.status})"

        return when (endorsement) {
            is Endorsement.NomineeChange -> {
                Nominee.validateShares(endorsement.newNominees)?.let { errors += it }
                result(policy, endorsement, "NomineeChange", daysRemaining, tenureDays, Money.ZERO, errors)
            }

            is Endorsement.AddressChange -> {
                val newZone = endorsement.newZone
                val delta = if (newZone != null && newZone != policy.currentZone) {
                    val oldPrem = baseFor(policy, policy.currentZone)
                    val newPrem = baseFor(policy, newZone)
                    (newPrem - oldPrem).toMoney()
                } else {
                    Money.ZERO
                }
                result(policy, endorsement, "AddressChange", daysRemaining, tenureDays, delta, errors)
            }

            is Endorsement.IncreaseSumInsured -> {
                if (endorsement.toAmount.toRupees() <= policy.currentSumInsured) {
                    errors += "New SI ${endorsement.toAmount.toRupees()} must exceed current SI ${policy.currentSumInsured}"
                }
                val oldPrem = baseFor(policy, policy.currentZone, sumInsured = policy.currentSumInsured)
                val newPrem = baseFor(policy, policy.currentZone, sumInsured = endorsement.toAmount.toRupees().toLong())
                val delta = (newPrem - oldPrem).toMoney()
                result(policy, endorsement, "IncreaseSumInsured", daysRemaining, tenureDays, delta, errors)
            }

            is Endorsement.AddMember -> {
                // Charge = the new member's own base premium at their age band, full term.
                val memberBand = getAgeBand(endorsement.memberToAdd.age)
                val memberPrem = rates.baseFor(
                    planId = policy.planId,
                    age = memberBand.minAge,
                    sumInsured = policy.currentSumInsured,
                    familyType = policy.familyType,
                    zone = policy.currentZone,
                )
                result(policy, endorsement, "AddMember", daysRemaining, tenureDays, memberPrem.toMoney(), errors)
            }

            is Endorsement.RemoveMember -> {
                // Refund = pro-rata of the policy's primary base premium attributed to the
                // removed member (full-term delta is negative → a refund).
                val basePrem = baseFor(policy, policy.currentZone)
                result(policy, endorsement, "RemoveMember", daysRemaining, tenureDays, (-basePrem).toMoney(), errors)
            }
        }
    }

    private suspend fun baseFor(
        policy: Policy,
        zone: String,
        sumInsured: Long = policy.currentSumInsured,
    ): Double {
        val band = getAgeBand(policy.primaryAge)
        return rates.baseFor(
            planId = policy.planId,
            age = band.minAge,
            sumInsured = sumInsured,
            familyType = policy.familyType,
            zone = zone,
        )
    }

    private fun daysRemaining(policy: Policy, at: Instant, tenureDays: Int): Int {
        val elapsedMs = at.toEpochMilliseconds() - policy.issuedAt.toEpochMilliseconds()
        val elapsedDays = (elapsedMs / (24L * 60 * 60 * 1000)).toInt()
        return (tenureDays - elapsedDays).coerceIn(0, tenureDays)
    }

    private fun result(
        policy: Policy,
        endorsement: Endorsement,
        type: String,
        daysRemaining: Int,
        tenureDays: Int,
        fullTermDelta: Money,
        errors: List<String>,
    ): EndorsementResult {
        val proRataFactor = if (tenureDays > 0) daysRemaining.toDouble() / tenureDays.toDouble() else 0.0
        val net = fullTermDelta * proRataFactor
        return EndorsementResult(
            policyId = policy.id,
            endorsementType = type,
            daysRemaining = daysRemaining,
            tenureDays = tenureDays,
            fullTermDelta = fullTermDelta,
            netAmount = net,
            isCharge = !net.isNegative(),
            rationale = endorsement.proRataChargeRationale,
            isValid = errors.isEmpty(),
            validationErrors = errors,
        )
    }
}
