package com.rate.sdk.rating.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a GROUP premium is derived. Sealed because the three approaches need different inputs
 * and the [com.rate.sdk.rating.handler.GroupPricingEngine] dispatches on the concrete type.
 *
 * - [Manual]      : book-rate (per-life age-band) pricing off the retail rate tables —
 *                   the only option for a fresh group with no claims history.
 * - [Experience]  : burning-cost from the group's own [ClaimYear] history, credibility-blended
 *                   against the manual rate. Used at renewal when enough data exists.
 * - [Hybrid]      : an explicit weighted blend of manual & experience rates (e.g. partial
 *                   credibility set by the underwriter rather than computed).
 */
@Serializable
sealed class RatingStrategy {

    /** Pure book-rate manual pricing. Industry loading still applies. */
    @Serializable
    @SerialName("manual")
    data object Manual : RatingStrategy()

    /**
     * Experience rating off [history]. [expenseRatio] grosses burning cost up to office
     * premium (premium = claims / (1 - expenseRatio)). [credibility] (Z, 0..1) blends the
     * experience premium with the manual book rate: `Z·experience + (1-Z)·manual`. When
     * [credibility] is null the engine computes it from total claim count via the classical
     * `√(n / full-credibility-standard)` square-root rule.
     */
    @Serializable
    @SerialName("experience")
    data class Experience(
        @SerialName("history") val history: List<ClaimYear>,
        @SerialName("expenseRatio") val expenseRatio: Double = 0.20,
        @SerialName("credibility") val credibility: Double? = null,
        /** Claim-count standard for full (Z=1.0) credibility (classical λ ≈ 1082). */
        @SerialName("fullCredibilityClaims") val fullCredibilityClaims: Int = 1082,
    ) : RatingStrategy()

    /**
     * Underwriter-set blend: [manualWeight] of the book rate + [experienceWeight] of the
     * experience rate (weights are normalised). Requires [history] for the experience leg.
     */
    @Serializable
    @SerialName("hybrid")
    data class Hybrid(
        @SerialName("history") val history: List<ClaimYear>,
        @SerialName("manualWeight") val manualWeight: Double = 0.5,
        @SerialName("experienceWeight") val experienceWeight: Double = 0.5,
        @SerialName("expenseRatio") val expenseRatio: Double = 0.20,
    ) : RatingStrategy()
}
