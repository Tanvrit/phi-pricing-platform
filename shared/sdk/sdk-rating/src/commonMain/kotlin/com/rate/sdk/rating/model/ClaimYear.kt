package com.rate.sdk.rating.model

import com.rate.core.money.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One policy-year of GROUP claims experience, used by the experience-rated path of
 * [RatingStrategy]. Insurers price a group renewal off its past loss ratios: the
 * burning-cost premium is `incurredClaims / (1 - expenseRatio)` projected forward.
 *
 * Monetary fields are [Money] (paise) — the Double rate-table parity boundary lives only
 * inside the retail [com.rate.sdk.rating.handler.PricingEngine]; group experience figures are
 * exact rupee amounts supplied by the employer/insurer, so they are Money from the start.
 */
@Serializable
data class ClaimYear(
    /** Policy year label, e.g. "FY24" or the 1-based ordinal. */
    @SerialName("year") val year: String,
    /** Average number of lives covered that year (denominator for per-life cost). */
    @SerialName("averageLives") val averageLives: Int,
    /** Total premium collected for the year. */
    @SerialName("earnedPremium") val earnedPremium: Money,
    /** Paid + outstanding (IBNR-inclusive) claims for the year. */
    @SerialName("incurredClaims") val incurredClaims: Money,
    /** Number of claims (for frequency analysis / credibility). */
    @SerialName("claimCount") val claimCount: Int = 0,
) {
    /** Incurred claims ÷ earned premium. 0.0 when no premium was earned. */
    val lossRatio: Double
        get() = if (earnedPremium.isZero()) 0.0 else incurredClaims.toRupees() / earnedPremium.toRupees()

    /** Average incurred claim cost per life for the year. */
    val claimsPerLife: Money
        get() = if (averageLives <= 0) Money.ZERO else incurredClaims / averageLives
}
