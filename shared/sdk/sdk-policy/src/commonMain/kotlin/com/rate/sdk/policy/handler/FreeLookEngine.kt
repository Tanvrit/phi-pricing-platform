package com.rate.sdk.policy.handler

import com.rate.core.money.Money
import com.rate.core.regulatory.Irdai
import com.rate.sdk.policy.model.Policy
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * IRDAI free-look period for individual health insurance: 15 days from the date the customer
 * receives the policy document, during which they may cancel for a refund.
 *
 * Refund formula (per IRDAI standard):
 * ```
 *   refund = totalPaid
 *          − proportional risk premium for days enjoyed
 *          − stamp duty
 *          − pre-policy medical-exam expenses (if any)
 *          − admin charges (if any)
 * ```
 *
 * Days enjoyed = clamp(today − issueDate, 0..tenureDays). Cancel on day 0 → almost full
 * refund; wait until day 14 → a small risk-premium deduction. Day 15+ → outside the window,
 * normal cancellation rules apply.
 *
 * The math is isolated here so a future actuarial review touches one place; the stamp-duty /
 * exam / admin figures are supplied by the caller from the proposal record.
 */
@Serializable
data class FreeLookRefundBreakdown(
    @SerialName("totalPaid") val totalPaid: Money,
    @SerialName("daysEnjoyed") val daysEnjoyed: Int,
    @SerialName("tenureDays") val tenureDays: Int,
    @SerialName("proportionalRiskPremium") val proportionalRiskPremium: Money,
    @SerialName("stampDuty") val stampDuty: Money,
    @SerialName("medicalExamExpense") val medicalExamExpense: Money,
    @SerialName("adminCharges") val adminCharges: Money,
    @SerialName("refundAmount") val refundAmount: Money,
    @SerialName("withinWindow") val withinWindow: Boolean,
)

object FreeLookEngine {

    /** IRDAI standard: 15 days from policy-receipt (sourced from regulatory config). */
    val WINDOW_DAYS: Int get() = Irdai.FREE_LOOK_DAYS

    /** Days in a policy year (integer 365 for the engine; matches the renewal/illustration math). */
    private const val DAYS_PER_YEAR: Int = 365

    /**
     * Is [requestedAt] within the customer's free-look window? The window opens at
     * `policy.issuedAt` and lasts [WINDOW_DAYS] days.
     */
    fun isWithinWindow(policy: Policy, requestedAt: Instant): Boolean {
        val days = daysBetween(policy.issuedAt, requestedAt)
        return days in 0..WINDOW_DAYS
    }

    /**
     * Compute the refund if the customer cancels within the free-look window. When called
     * outside the window this returns `withinWindow = false` with `refundAmount = Money.ZERO`
     * (the breakdown is then informational only).
     */
    fun refund(
        policy: Policy,
        requestedAt: Instant,
        totalPaid: Money,
        stampDuty: Money = Money.ZERO,
        medicalExamExpense: Money = Money.ZERO,
        adminCharges: Money = Money.ZERO,
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
                withinWindow = false,
            )
        }

        // Proportional risk premium = totalPaid × (daysEnjoyed / tenureDays), rounded half-even
        // to the nearest paisa by Money's Double × operator.
        val riskPremium = if (tenureDays > 0) {
            totalPaid * (daysEnjoyed.toDouble() / tenureDays.toDouble())
        } else {
            Money.ZERO
        }

        val refund = totalPaid - riskPremium - stampDuty - medicalExamExpense - adminCharges
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
            withinWindow = true,
        )
    }

    private fun daysBetween(start: Instant, end: Instant): Int {
        val ms = end.toEpochMilliseconds() - start.toEpochMilliseconds()
        return (ms / (24L * 60 * 60 * 1000)).toInt()
    }
}
