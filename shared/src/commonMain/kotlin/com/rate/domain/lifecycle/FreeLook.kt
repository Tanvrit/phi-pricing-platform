package com.rate.domain.lifecycle

import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * IRDAI free-look period for individual health insurance: 15 days from the date the
 * customer receives the policy document, during which the customer may cancel for a
 * refund.
 *
 * Refund formula (per IRDAI standard):
 *   refund = totalPaid
 *          − proportional risk premium for days enjoyed
 *          − stamp duty
 *          − pre-policy medical exam expenses (if any)
 *          − admin charges (if any)
 *
 * Days enjoyed = min(today − issueDate, tenureDays). If the customer cancels on day 0,
 * almost the full premium is refunded. If they wait until day 14, a small risk-premium
 * deduction applies. Day 15+ → outside the window, normal cancellation rules apply.
 *
 * Phase 5b will wire the actual stamp-duty/exam/admin numbers from the proposal record;
 * this class isolates the math so a future actuarial review touches one place.
 */
@Serializable
data class FreeLookRefundBreakdown(
    val totalPaid: Money,
    val daysEnjoyed: Int,
    val tenureDays: Int,
    val proportionalRiskPremium: Money,
    val stampDuty: Money,
    val medicalExamExpense: Money,
    val adminCharges: Money,
    val refundAmount: Money,
    val withinWindow: Boolean
)

object FreeLookEngine {

    /** IRDAI standard: 15 days from policy-receipt. */
    const val WINDOW_DAYS: Int = 15

    /** Days in a policy year (365.25 averages leap years; using integer 365 for the engine). */
    private const val DAYS_PER_YEAR: Int = 365

    /**
     * Is [requestedAt] within the customer's free-look window?
     * The window opens at [policy.issuedAt] and lasts WINDOW_DAYS days.
     */
    fun isWithinWindow(policy: Policy, requestedAt: Instant): Boolean {
        val days = daysBetween(policy.issuedAt, requestedAt)
        return days in 0..WINDOW_DAYS
    }

    /**
     * Compute the refund the customer receives if they cancel within the free-look window.
     * Caller must guard with [isWithinWindow]; this returns withinWindow=false when called
     * outside the window (in which case the refundAmount field is Money.ZERO and the
     * breakdown is informational only).
     */
    fun refund(
        policy: Policy,
        requestedAt: Instant,
        totalPaid: Money,
        stampDuty: Money = Money.ZERO,
        medicalExamExpense: Money = Money.ZERO,
        adminCharges: Money = Money.ZERO
    ): FreeLookRefundBreakdown {
        val daysEnjoyedRaw = daysBetween(policy.issuedAt, requestedAt)
        val daysEnjoyed = max(0, daysEnjoyedRaw)
        val tenureDays = policy.currentTenure * DAYS_PER_YEAR
        val within = daysEnjoyedRaw in 0..WINDOW_DAYS

        if (!within) {
            return FreeLookRefundBreakdown(
                totalPaid = totalPaid,
                daysEnjoyed = daysEnjoyed,
                tenureDays = tenureDays,
                proportionalRiskPremium = Money.ZERO,
                stampDuty = stampDuty,
                medicalExamExpense = medicalExamExpense,
                adminCharges = adminCharges,
                refundAmount = Money.ZERO,
                withinWindow = false
            )
        }

        // Proportional risk premium = totalPaid * (daysEnjoyed / tenureDays).
        // Use Money's Double × method which rounds half-even to nearest paisa.
        val riskPremium = if (tenureDays > 0)
            totalPaid * (daysEnjoyed.toDouble() / tenureDays.toDouble())
        else Money.ZERO

        val refund = totalPaid - riskPremium - stampDuty - medicalExamExpense - adminCharges
        // Refund cannot be negative; deductions clamped at zero.
        val safeRefund = if (refund.isNegative()) Money.ZERO else refund

        return FreeLookRefundBreakdown(
            totalPaid = totalPaid,
            daysEnjoyed = daysEnjoyed,
            tenureDays = tenureDays,
            proportionalRiskPremium = riskPremium,
            stampDuty = stampDuty,
            medicalExamExpense = medicalExamExpense,
            adminCharges = adminCharges,
            refundAmount = safeRefund,
            withinWindow = true
        )
    }

    private fun daysBetween(start: Instant, end: Instant): Int {
        val ms = end.toEpochMilliseconds() - start.toEpochMilliseconds()
        return (ms / (24L * 60 * 60 * 1000)).toInt()
    }
}
