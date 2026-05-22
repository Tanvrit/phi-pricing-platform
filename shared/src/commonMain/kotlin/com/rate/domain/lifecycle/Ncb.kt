package com.rate.domain.lifecycle

import kotlin.math.min

/**
 * No-Claim Bonus (NCB) calculator.
 *
 * IRDAI does not mandate a specific NCB structure — the actuary signs off on the formula.
 * Industry standard for individual health PHI: 5% bonus per claim-free renewal year,
 * cumulative, capped at 50% (effectively 10 years of clean history). When a claim is
 * recorded in a policy year, NCB resets to 0% for the next renewal.
 *
 * D-11 in the audit report: "NCB structure (5%/year cap 50% — confirm with actuary)".
 * The constants below match the spec; the formula isolates them so an actuary sign-off
 * can land in one place without ripple changes.
 */
object Ncb {

    /** Bonus accrued per claim-free policy year. */
    const val RATE_PER_YEAR: Double = 0.05

    /** Hard ceiling regardless of claim-free streak length. */
    const val MAX_RATE: Double = 0.50

    /**
     * Returns the NCB rate (as a fraction, e.g. 0.15 = 15%) given the number of consecutive
     * claim-free policy years immediately prior to the renewal.
     *
     * `claimFreeYears` must be non-negative; the lower bound is asserted because a negative
     * value implies a bookkeeping bug somewhere upstream.
     */
    fun rate(claimFreeYears: Int): Double {
        require(claimFreeYears >= 0) { "claimFreeYears must be >= 0, got $claimFreeYears" }
        return min(MAX_RATE, claimFreeYears * RATE_PER_YEAR)
    }

    /**
     * Applies the NCB rate as a discount on the base renewal premium.
     * Returned amount is the rupees-discount (positive value) the customer saves.
     */
    fun discount(claimFreeYears: Int, basePremium: Double): Double =
        rate(claimFreeYears) * basePremium
}
