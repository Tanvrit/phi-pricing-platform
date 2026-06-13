package com.rate.sdk.rating.handler

import com.rate.core.money.Money
import com.rate.sdk.party.model.group.AgeBandBucket
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.GradeAggregation
import com.rate.sdk.rating.data.ManualGroupRateProvider
import com.rate.sdk.rating.model.GroupQuoteResult
import com.rate.sdk.rating.model.ManualGroupRateInput
import com.rate.sdk.rating.model.ManualGroupStrategyMode
import com.rate.sdk.rating.model.PremiumAllocation
import com.rate.sdk.rating.model.RatingStrategy

/**
 * Drives a fully-MANUAL group quote off an operator-typed [ManualGroupRateInput]. It is a thin
 * orchestration layer over the existing [GroupPricingEngine]: it
 *
 *  1. builds a [CensusAggregation] DIRECTLY from [ManualGroupRateInput.demography] (bypassing
 *     `CensusAggregation.from`, which re-bands lives by age — here the operator already chose
 *     the bands),
 *  2. maps [ManualGroupRateInput.mode] onto the engine's [RatingStrategy], and
 *  3. runs `GroupPricingEngine(ManualGroupRateProvider(input))` so the manual per-life rates,
 *     the manual size-discount and the manual industry loading all flow through the SAME engine
 *     math (size/loading factor → experience blend → GST → employer/employee split).
 *
 * This keeps the manual path a *discriminator over a single engine*, not a parallel calculator.
 */
object ManualGroupRater {

    suspend fun rate(input: ManualGroupRateInput): GroupQuoteResult {
        // Guard: nothing to rate → an invalid result (don't divide by zero downstream).
        if (input.demography.isEmpty() || input.demography.sumOf { it.lives } <= 0) {
            return invalidResult(input, "Demography is empty — add at least one row with lives > 0")
        }

        val aggregation = buildAggregation(input)
        val strategy = strategyFor(input)

        return GroupPricingEngine(ManualGroupRateProvider(input)).rate(
            groupConfigId = input.groupConfigId,
            industryCode = input.industryCode,
            aggregation = aggregation,
            strategy = strategy,
            zone = input.zone,
            gstRate = input.gstRate,
            employerShare = (input.employerSharePct / 100.0).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Roll the operator's demography rows into a [CensusAggregation] WITHOUT re-banding by age.
     * Rows are grouped by grade; within a grade rows that share an [AgeBandBucket.ageBandMinAge]
     * are merged (lives summed) so the engine sees one bucket per band. The grade's rating SI is
     * the modal SI across its rows (ties broken by the larger SI, matching `CensusAggregation`).
     */
    private fun buildAggregation(input: ManualGroupRateInput): CensusAggregation {
        val byGrade: List<GradeAggregation> = input.demography
            .groupBy { it.grade }
            .entries.sortedBy { it.key }
            .map { (grade, rows) ->
                GradeAggregation(
                    grade = grade,
                    totalLives = rows.sumOf { it.lives },
                    sumInsured = modalSumInsured(rows.map { it.sumInsured }),
                    buckets = mergeBuckets(
                        rows.map { AgeBandBucket(it.ageBandLabel, it.ageBandMinAge, it.lives) },
                    ),
                )
            }

        val overallBuckets = mergeBuckets(
            input.demography.map { AgeBandBucket(it.ageBandLabel, it.ageBandMinAge, it.lives) },
        )
        val totalLives = input.demography.sumOf { it.lives }
        val livesWeightedAge =
            if (totalLives <= 0) 0.0
            else input.demography.sumOf { it.ageBandMinAge.toLong() * it.lives }.toDouble() / totalLives

        return CensusAggregation(
            employerPartyRef = input.groupConfigId,
            censusId = "manual",
            totalLives = totalLives,
            byGrade = byGrade,
            overallBuckets = overallBuckets,
            averageAge = livesWeightedAge,
        )
    }

    /** Merge buckets that share a band min-age (sum lives), ordered by ascending min-age. */
    private fun mergeBuckets(buckets: List<AgeBandBucket>): List<AgeBandBucket> =
        buckets.groupBy { it.ageBandMinAge }
            .entries.sortedBy { it.key }
            .map { (minAge, group) ->
                AgeBandBucket(
                    ageBandLabel = group.first().ageBandLabel,
                    ageBandMinAge = minAge,
                    count = group.sumOf { it.count },
                )
            }

    /** Most common SI (ties broken by the larger SI), matching CensusAggregation's choice. */
    private fun modalSumInsured(sis: List<Long>): Long =
        sis.groupingBy { it }.eachCount()
            .entries
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key ?: 0L

    /** Map the operator's [ManualGroupStrategyMode] onto the engine's [RatingStrategy]. */
    private fun strategyFor(input: ManualGroupRateInput): RatingStrategy {
        val history = input.claimYears.map { it.toClaimYear() }
        val expenseRatio = (input.expenseRatioPct / 100.0).coerceIn(0.0, 0.95)
        return when (input.mode) {
            ManualGroupStrategyMode.MANUAL -> RatingStrategy.Manual
            ManualGroupStrategyMode.EXPERIENCE -> RatingStrategy.Experience(
                history = history,
                expenseRatio = expenseRatio,
                credibility = input.credibilityOverridePct?.let { (it / 100.0).coerceIn(0.0, 1.0) },
            )
            ManualGroupStrategyMode.HYBRID -> {
                val manualWeight = (input.manualWeightPct / 100.0).coerceIn(0.0, 1.0)
                RatingStrategy.Hybrid(
                    history = history,
                    manualWeight = manualWeight,
                    experienceWeight = 1.0 - manualWeight,
                    expenseRatio = expenseRatio,
                )
            }
        }
    }

    /** An invalid [GroupQuoteResult] for a guard failure (mirrors the engine's empty path). */
    private fun invalidResult(input: ManualGroupRateInput, error: String): GroupQuoteResult =
        GroupQuoteResult(
            requestId = "GQ-MANUAL-INVALID",
            groupConfigId = input.groupConfigId,
            employerPartyRef = input.groupConfigId,
            censusId = "manual",
            totalLives = 0,
            strategy = input.mode.name.lowercase(),
            perMember = emptyList(),
            perGrade = emptyList(),
            groupSizeDiscount = 0.0,
            industryLoading = 0.0,
            manualPremium = Money.ZERO,
            experiencePremium = Money.ZERO,
            credibility = 0.0,
            blendedPremium = Money.ZERO,
            gstRate = input.gstRate,
            gst = Money.ZERO,
            total = Money.ZERO,
            allocation = PremiumAllocation.employerFunded(Money.ZERO),
            isValid = false,
            validationErrors = listOf(error),
        )
}
