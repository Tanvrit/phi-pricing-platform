package com.rate.sdk.rating.model

import com.rate.core.money.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which leg(s) of the group rate the underwriter wants the
 * [com.rate.sdk.rating.handler.ManualGroupRater] to compute.
 *
 * - [MANUAL]     : pure book/manual premium off the per-life rates the operator types per row.
 * - [EXPERIENCE] : credibility-blend the manual rate with the burning cost of the
 *                  supplied [ManualGroupRateInput.claimYears]. Z is computed from claim count
 *                  (classical √-rule) unless [ManualGroupRateInput.credibilityOverridePct] is set.
 * - [HYBRID]     : explicit weighted blend (operator sets [ManualGroupRateInput.manualWeightPct]).
 */
@Serializable
enum class ManualGroupStrategyMode {
    @SerialName("manual") MANUAL,
    @SerialName("experience") EXPERIENCE,
    @SerialName("hybrid") HYBRID,
}

/**
 * One operator-typed claims-experience year for the EXPERIENCE/HYBRID legs. Mirrors the
 * engine-facing [ClaimYear] but is the *input* shape the UI binds to (rupee amounts as plain
 * [Money], a free-text [label] so the operator can name the year "FY24" / "2023-24" / "Yr 1").
 */
@Serializable
data class ManualClaimYear(
    /** Free-text year label the operator types, e.g. "FY24" / "2023-24". */
    @SerialName("label") val label: String,
    /** Average lives covered that year (denominator for per-life cost; informational). */
    @SerialName("averageLives") val averageLives: Int = 0,
    /** Total premium earned that year. */
    @SerialName("earnedPremium") val earnedPremium: Money,
    /** Paid + outstanding (IBNR-inclusive) incurred claims that year. */
    @SerialName("incurredClaims") val incurredClaims: Money,
    /** Number of claims that year (drives √-rule credibility). */
    @SerialName("claimCount") val claimCount: Int = 0,
) {
    /** Map this operator row into the engine-facing [ClaimYear]. */
    fun toClaimYear(): ClaimYear = ClaimYear(
        year = label,
        averageLives = averageLives,
        earnedPremium = earnedPremium,
        incurredClaims = incurredClaims,
        claimCount = claimCount,
    )
}

/**
 * One row of the operator-typed demography grid: a (grade × age-band) bucket whose per-life
 * book rate the underwriter enters directly. The [com.rate.sdk.rating.handler.ManualGroupRater]
 * builds a CensusAggregation from these rows and serves [ratePerLifeRupees] back to the engine
 * as the manual base premium — no rate table is consulted.
 */
@Serializable
data class ManualDemographyRow(
    /** Benefit grade label, e.g. "A" / "Executive". */
    @SerialName("grade") val grade: String,
    /** Display label for the age band, e.g. "26-35". */
    @SerialName("ageBandLabel") val ageBandLabel: String,
    /** Band minimum age — the key the engine passes to getBasePremium. */
    @SerialName("ageBandMinAge") val ageBandMinAge: Int,
    /** Head-count of lives in this bucket. */
    @SerialName("lives") val lives: Int,
    /** Sum insured for this grade (rupees). */
    @SerialName("sumInsured") val sumInsured: Long,
    /** Operator-typed per-life manual/book premium for this bucket (rupees/year). */
    @SerialName("ratePerLifeRupees") val ratePerLifeRupees: Long,
)

/**
 * The fully operator-controlled input for a MANUAL group quote. Every group factor that the
 * table-backed [com.rate.sdk.rating.handler.GroupPricingEngine] would normally *look up* is here
 * a value the underwriter types:
 *
 * - per-life book rates → [demography] rows ([ManualDemographyRow.ratePerLifeRupees])
 * - group-size discount  → [groupSizeDiscountPct]  (a manual override, NOT a head-count table)
 * - industry loading     → [industryLoadingPct]    (a manual override, NOT an industry table)
 *
 * The [mode] selects which blending leg runs; EXPERIENCE/HYBRID additionally consume
 * [claimYears] / [expenseRatioPct] / [credibilityOverridePct] / [manualWeightPct].
 */
@Serializable
data class ManualGroupRateInput(
    /** Config/scheme id this quote prices against (also used as the employer party ref). */
    @SerialName("groupConfigId") val groupConfigId: String,
    /** Optional human client name (display only; defaults to [groupConfigId] if blank). */
    @SerialName("clientName") val clientName: String = "",
    /** Industry code — informational only here (loading is the manual [industryLoadingPct]). */
    @SerialName("industryCode") val industryCode: String = "",
    /** Rating zone label (passed through to the engine). */
    @SerialName("zone") val zone: String = "Pan India",
    /** GST rate as a fraction (0.18 = 18%). */
    @SerialName("gstRate") val gstRate: Double = 0.18,
    /** Employer-paid share of the total, 0..100 (%). */
    @SerialName("employerSharePct") val employerSharePct: Double = 100.0,

    /** MANUAL OVERRIDE: group-size discount the operator types, 0..100 (%). */
    @SerialName("groupSizeDiscountPct") val groupSizeDiscountPct: Double = 0.0,
    /** MANUAL OVERRIDE: industry loading the operator types, 0..(+) (%). */
    @SerialName("industryLoadingPct") val industryLoadingPct: Double = 0.0,

    /** Which blending leg to run. */
    @SerialName("mode") val mode: ManualGroupStrategyMode = ManualGroupStrategyMode.MANUAL,

    // ── EXPERIENCE / HYBRID inputs ────────────────────────────────────────────
    /** Claims history for the EXPERIENCE/HYBRID legs (ignored for MANUAL). */
    @SerialName("claimYears") val claimYears: List<ManualClaimYear> = emptyList(),
    /** Expense ratio grossing burning cost up to office premium, 0..100 (%). */
    @SerialName("expenseRatioPct") val expenseRatioPct: Double = 20.0,
    /** Optional manual credibility override Z, 0..100 (%); null ⇒ engine computes via √-rule. */
    @SerialName("credibilityOverridePct") val credibilityOverridePct: Double? = null,
    /** HYBRID only: weight on the manual leg, 0..100 (%); experience weight is 100 − this. */
    @SerialName("manualWeightPct") val manualWeightPct: Double = 50.0,

    /** Operator-typed demography grid (grade × age-band buckets with per-life book rates). */
    @SerialName("demography") val demography: List<ManualDemographyRow> = emptyList(),
) {
    /** Display name — [clientName] if set, else the [groupConfigId]. */
    val displayName: String get() = clientName.ifBlank { groupConfigId }

    companion object {
        /**
         * A realistic 2-grade × 3-band example the UI can preload as a starting point:
         * Grade A (₹10L SI) executives and Grade B (₹5L SI) staff across three age bands,
         * a manual 5% size discount + 10% industry loading, employer-funded.
         */
        fun sample(): ManualGroupRateInput = ManualGroupRateInput(
            groupConfigId = "GHI_STD",
            clientName = "Acme Technologies Pvt Ltd",
            industryCode = "IT",
            zone = "Pan India",
            gstRate = 0.18,
            employerSharePct = 100.0,
            groupSizeDiscountPct = 5.0,
            industryLoadingPct = 10.0,
            mode = ManualGroupStrategyMode.MANUAL,
            demography = listOf(
                ManualDemographyRow("A", "26-35", 26, lives = 18, sumInsured = 1_000_000L, ratePerLifeRupees = 9_500L),
                ManualDemographyRow("A", "36-45", 36, lives = 12, sumInsured = 1_000_000L, ratePerLifeRupees = 13_200L),
                ManualDemographyRow("A", "46-55", 46, lives = 6, sumInsured = 1_000_000L, ratePerLifeRupees = 19_800L),
                ManualDemographyRow("B", "26-35", 26, lives = 30, sumInsured = 500_000L, ratePerLifeRupees = 5_400L),
                ManualDemographyRow("B", "36-45", 36, lives = 20, sumInsured = 500_000L, ratePerLifeRupees = 7_600L),
                ManualDemographyRow("B", "46-55", 46, lives = 10, sumInsured = 500_000L, ratePerLifeRupees = 11_300L),
            ),
        )
    }
}
