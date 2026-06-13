package com.rate.sdk.rating.model

import com.rate.core.money.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Split of a GROUP premium between the employer and the employee (contributory groups).
 * Many GHI schemes have the employer fund the base (employee-only) cover while employees
 * top-up for dependents; this captures that share so the employer billing and the employee
 * payroll-deduction figures are both derivable from the same quote.
 *
 * `employerShare` is the fraction (0..1) the employer pays of the [total]; the engine
 * derives the two [Money] legs from it so they always sum back to [total] (the larger
 * rounding remainder is assigned to the employer leg).
 */
@Serializable
data class PremiumAllocation(
    @SerialName("total") val total: Money,
    @SerialName("employerShare") val employerShare: Double,
    @SerialName("employerAmount") val employerAmount: Money,
    @SerialName("employeeAmount") val employeeAmount: Money,
) {
    init {
        require(employerShare in 0.0..1.0) { "employerShare must be in 0.0..1.0, was $employerShare" }
    }

    companion object {
        /**
         * Split [total] so the employer pays [employerShare] of it. The employee leg is the
         * exact remainder (`total - employerAmount`) so the two legs reconcile to [total]
         * to the paisa regardless of rounding.
         */
        fun of(total: Money, employerShare: Double): PremiumAllocation {
            val share = employerShare.coerceIn(0.0, 1.0)
            val employer = total * share
            val employee = total - employer
            return PremiumAllocation(
                total = total,
                employerShare = share,
                employerAmount = employer,
                employeeAmount = employee,
            )
        }

        /** Fully employer-funded (non-contributory) split. */
        fun employerFunded(total: Money): PremiumAllocation = of(total, 1.0)
    }
}
