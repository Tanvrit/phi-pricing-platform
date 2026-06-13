package com.rate.sdk.policy.handler

import com.rate.core.regulatory.Irdai
import kotlin.math.min

/**
 * No-Claim Bonus (NCB) calculator.
 *
 * IRDAI does not mandate a specific NCB structure — the actuary signs off on the formula.
 * Industry standard for individual health PHI: 5% bonus per claim-free renewal year,
 * cumulative, capped at 50% (effectively 10 years of clean history). When a claim is
 * recorded in a policy year, NCB resets to 0% for the next renewal.
 *
 * The constants are sourced from [Irdai] (`NCB_STEP` / `NCB_MAX`) so an actuary sign-off
 * lands in one regulatory place without ripple changes here.
 */
object Ncb {

    /** Bonus accrued per claim-free policy year (IRDAI-config default). */
    val RATE_PER_YEAR: Double get() = Irdai.NCB_STEP

    /** Hard ceiling regardless of claim-free streak length (IRDAI-config default). */
    val MAX_RATE: Double get() = Irdai.NCB_MAX

    /**
     * Returns the NCB rate (as a fraction, e.g. 0.15 = 15%) given the number of consecutive
     * claim-free policy years immediately prior to the renewal. A negative input implies a
     * bookkeeping bug upstream and is rejected.
     */
    fun rate(claimFreeYears: Int): Double {
        require(claimFreeYears >= 0) { "claimFreeYears must be >= 0, got $claimFreeYears" }
        return min(MAX_RATE, claimFreeYears * RATE_PER_YEAR)
    }

    /**
     * Applies the NCB rate as a discount on the base renewal premium. Returns the
     * rupees-discount (positive) the customer saves.
     */
    fun discount(claimFreeYears: Int, basePremium: Double): Double =
        rate(claimFreeYears) * basePremium
}
