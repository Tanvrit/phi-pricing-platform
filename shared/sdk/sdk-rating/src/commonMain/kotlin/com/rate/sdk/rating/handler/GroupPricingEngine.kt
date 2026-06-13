package com.rate.sdk.rating.handler

import com.rate.core.money.Money
import com.rate.core.money.sumMoney
import com.rate.core.money.toMoney
import com.rate.core.rating.ports.GroupRateDataProvider
import com.rate.core.regulatory.Gst
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.model.group.GradeAggregation
import com.rate.sdk.rating.model.GradePremiumLine
import com.rate.sdk.rating.model.GroupQuoteResult
import com.rate.sdk.rating.model.MemberPremiumLine
import com.rate.sdk.rating.model.PremiumAllocation
import com.rate.sdk.rating.model.RatingStrategy
import kotlin.math.min
import kotlin.math.sqrt

/**
 * GROUP rating engine. Reuses the retail actuarial tables via [GroupRateDataProvider]
 * (book/manual rates per age-band) and layers on the group-specific factors:
 *
 *  1. **Manual (book) premium** — for every (grade × age-band) census bucket, look up the
 *     per-life base premium and multiply by the bucket head-count. This reuses the SAME
 *     `getBasePremium` lookup the retail [PricingEngine] uses, keeping GROUP a discriminator
 *     rather than a parallel rate tree.
 *  2. **Group-size discount** — larger groups get a volume discount ([GroupRateDataProvider.groupSizeDiscount]).
 *  3. **Industry loading** — riskier industries get a loading ([GroupRateDataProvider.industryLoading]).
 *  4. **Experience blending** — when the [RatingStrategy] supplies claims history, blend the
 *     burning-cost premium with the manual rate by credibility (classical √-rule, or an
 *     underwriter-set weight for [RatingStrategy.Hybrid]).
 *  5. **Premium allocation** — split the final total employer/employee for contributory groups.
 *
 * The retail engine's Double parity is irrelevant here: book rates are converted to [Money]
 * at the bucket boundary and all subsequent group math is exact paise.
 */
class GroupPricingEngine(
    private val data: GroupRateDataProvider,
    private val retail: PricingEngine? = null,
) {

    /**
     * Rate a group from its [census] under the given [config][groupConfigId] / [industryCode]
     * and [strategy]. [employerShare] (0..1) splits the final total employer/employee.
     *
     * @param zone the rating zone key the book tables use (same string keys as retail).
     */
    suspend fun rate(
        groupConfigId: String,
        industryCode: String,
        census: Census,
        strategy: RatingStrategy = RatingStrategy.Manual,
        zone: String = "Pan India",
        gstRate: Double = Gst.DEFAULT_RATE,
        employerShare: Double = 1.0,
    ): GroupQuoteResult {
        val aggregation = CensusAggregation.from(
            employerPartyRef = census.employerPartyRef,
            censusId = census.id,
            members = census.members,
        )
        return rate(
            groupConfigId = groupConfigId,
            industryCode = industryCode,
            aggregation = aggregation,
            strategy = strategy,
            zone = zone,
            gstRate = gstRate,
            employerShare = employerShare,
        )
    }

    /** Rate directly from a pre-computed census [aggregation] (grade × age-band roll-up). */
    suspend fun rate(
        groupConfigId: String,
        industryCode: String,
        aggregation: CensusAggregation,
        strategy: RatingStrategy,
        zone: String,
        gstRate: Double,
        employerShare: Double,
    ): GroupQuoteResult {
        val errors = mutableListOf<String>()
        if (aggregation.totalLives <= 0) errors += "Census has no lives to rate"
        if (employerShare !in 0.0..1.0) errors += "Employer share $employerShare must be in 0.0..1.0"
        if (errors.isNotEmpty()) {
            return emptyResult(groupConfigId, aggregation, strategy, errors)
        }

        // ── 1. Manual book premium per (grade × age-band) bucket ──────────────
        val perMemberLines = mutableListOf<MemberPremiumLine>()
        for (grade in aggregation.byGrade) {
            for (bucket in grade.buckets) {
                val perLifeInr = data.getBasePremium(
                    planId = groupConfigId,
                    familyType = "1A",                 // group lives priced per-life (non-floater unit)
                    zone = zone,
                    ageBandMinAge = bucket.ageBandMinAge,
                    sumInsured = grade.sumInsured,
                )
                val ratePerLife = perLifeInr.toMoney()
                val bookPremium = ratePerLife * bucket.count
                perMemberLines += MemberPremiumLine(
                    grade = grade.grade,
                    ageBandLabel = bucket.ageBandLabel,
                    ageBandMinAge = bucket.ageBandMinAge,
                    lives = bucket.count,
                    sumInsured = grade.sumInsured,
                    ratePerLife = ratePerLife,
                    bookPremium = bookPremium,
                    allocatedPremium = bookPremium, // overwritten after group factors
                )
            }
        }
        val manualPremium = perMemberLines.map { it.bookPremium }.sumMoney()

        // ── 2/3. Group-size discount + industry loading ───────────────────────
        val sizeDiscount = data.groupSizeDiscount(aggregation.totalLives).coerceIn(0.0, 1.0)
        val industryLoading = data.industryLoading(industryCode).coerceAtLeast(0.0)
        // factor = (1 - discount) × (1 + loading), applied to the manual book premium.
        val manualFactor = (1.0 - sizeDiscount) * (1.0 + industryLoading)
        val adjustedManual = manualPremium * manualFactor

        // ── 4. Experience blending ────────────────────────────────────────────
        val (experiencePremium, credibility) = experience(strategy, aggregation.totalLives, adjustedManual)
        val blendedPremium = blend(adjustedManual, experiencePremium, credibility)

        // ── 5. Spread the blended premium back onto buckets/grades proportionally ─
        val scale: Double =
            if (manualPremium.isZero()) 0.0 else blendedPremium.toRupees() / manualPremium.toRupees()
        val scaledMemberLines = scaleLines(perMemberLines, scale, blendedPremium)
        val perGradeLines = rollUpGrades(aggregation, scaledMemberLines, employerShare)

        // ── 6. GST + total + allocation ───────────────────────────────────────
        val gst = blendedPremium * gstRate
        val total = blendedPremium + gst
        val allocation = PremiumAllocation.of(total, employerShare)

        return GroupQuoteResult(
            requestId = generateId(),
            groupConfigId = groupConfigId,
            employerPartyRef = aggregation.employerPartyRef,
            censusId = aggregation.censusId,
            totalLives = aggregation.totalLives,
            strategy = strategyLabel(strategy),
            perMember = scaledMemberLines,
            perGrade = perGradeLines,
            groupSizeDiscount = sizeDiscount,
            industryLoading = industryLoading,
            manualPremium = adjustedManual,
            experiencePremium = experiencePremium,
            credibility = credibility,
            blendedPremium = blendedPremium,
            gstRate = gstRate,
            gst = gst,
            total = total,
            allocation = allocation,
            isValid = true,
        )
    }

    // ── Experience rating ─────────────────────────────────────────────────────

    /** Returns (experiencePremium, credibility). Manual ⇒ (0, 0). */
    private fun experience(
        strategy: RatingStrategy,
        totalLives: Int,
        manual: Money,
    ): Pair<Money, Double> = when (strategy) {
        is RatingStrategy.Manual -> Money.ZERO to 0.0

        is RatingStrategy.Experience -> {
            val expenseRatio = strategy.expenseRatio.coerceIn(0.0, 0.95)
            val incurred = strategy.history.map { it.incurredClaims }.sumMoney()
            // Burning cost grossed up for expenses → office premium.
            val burningCost = incurred * (1.0 / (1.0 - expenseRatio))
            val claims = strategy.history.sumOf { it.claimCount }
            val z = strategy.credibility?.coerceIn(0.0, 1.0)
                ?: squareRootCredibility(claims, strategy.fullCredibilityClaims)
            burningCost to z
        }

        is RatingStrategy.Hybrid -> {
            val expenseRatio = strategy.expenseRatio.coerceIn(0.0, 0.95)
            val incurred = strategy.history.map { it.incurredClaims }.sumMoney()
            val burningCost = incurred * (1.0 / (1.0 - expenseRatio))
            val wm = strategy.manualWeight.coerceAtLeast(0.0)
            val we = strategy.experienceWeight.coerceAtLeast(0.0)
            val z = if (wm + we <= 0.0) 0.0 else we / (wm + we)
            burningCost to z
        }
    }

    /** Classical square-root partial credibility: Z = min(1, √(n / N_full)). */
    private fun squareRootCredibility(claims: Int, fullStandard: Int): Double {
        if (claims <= 0 || fullStandard <= 0) return 0.0
        return min(1.0, sqrt(claims.toDouble() / fullStandard.toDouble()))
    }

    /** Blend = Z·experience + (1-Z)·manual. */
    private fun blend(manual: Money, experience: Money, z: Double): Money {
        val zc = z.coerceIn(0.0, 1.0)
        return experience * zc + manual * (1.0 - zc)
    }

    // ── Allocation / roll-up helpers ───────────────────────────────────────────

    /**
     * Scale each bucket's allocated premium by [scale] and assign the rounding remainder to
     * the largest bucket so the per-member allocations reconcile exactly to [blendedTotal].
     */
    private fun scaleLines(
        lines: List<MemberPremiumLine>,
        scale: Double,
        blendedTotal: Money,
    ): List<MemberPremiumLine> {
        if (lines.isEmpty()) return lines
        val scaled = lines.map { it.copy(allocatedPremium = it.bookPremium * scale) }
        val sum = scaled.map { it.allocatedPremium }.sumMoney()
        val remainder = blendedTotal - sum
        if (remainder.isZero()) return scaled
        // Largest-book bucket absorbs the remainder for an exact reconciliation.
        val idx = scaled.indices.maxBy { scaled[it].bookPremium.paise }
        return scaled.mapIndexed { i, line ->
            if (i == idx) line.copy(allocatedPremium = line.allocatedPremium + remainder) else line
        }
    }

    private fun rollUpGrades(
        aggregation: CensusAggregation,
        memberLines: List<MemberPremiumLine>,
        employerShare: Double,
    ): List<GradePremiumLine> {
        val byGrade: Map<String, GradeAggregation> = aggregation.byGrade.associateBy { it.grade }
        return memberLines.groupBy { it.grade }
            .entries.sortedBy { it.key }
            .map { (grade, lines) ->
                val book = lines.map { it.bookPremium }.sumMoney()
                val allocated = lines.map { it.allocatedPremium }.sumMoney()
                GradePremiumLine(
                    grade = grade,
                    totalLives = byGrade[grade]?.totalLives ?: lines.sumOf { it.lives },
                    sumInsured = byGrade[grade]?.sumInsured ?: lines.firstOrNull()?.sumInsured ?: 0L,
                    bookPremium = book,
                    allocatedPremium = allocated,
                    allocation = PremiumAllocation.of(allocated, employerShare),
                )
            }
    }

    private fun emptyResult(
        groupConfigId: String,
        aggregation: CensusAggregation,
        strategy: RatingStrategy,
        errors: List<String>,
    ) = GroupQuoteResult(
        requestId = generateId(),
        groupConfigId = groupConfigId,
        employerPartyRef = aggregation.employerPartyRef,
        censusId = aggregation.censusId,
        totalLives = aggregation.totalLives,
        strategy = strategyLabel(strategy),
        perMember = emptyList(),
        perGrade = emptyList(),
        groupSizeDiscount = 0.0,
        industryLoading = 0.0,
        manualPremium = Money.ZERO,
        experiencePremium = Money.ZERO,
        credibility = 0.0,
        blendedPremium = Money.ZERO,
        gst = Money.ZERO,
        total = Money.ZERO,
        allocation = PremiumAllocation.employerFunded(Money.ZERO),
        isValid = false,
        validationErrors = errors,
    )

    private fun strategyLabel(strategy: RatingStrategy): String = when (strategy) {
        is RatingStrategy.Manual -> "manual"
        is RatingStrategy.Experience -> "experience"
        is RatingStrategy.Hybrid -> "hybrid"
    }

    private fun generateId(): String {
        val ms = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val rnd = kotlin.random.Random.nextInt(0, 0xFFFF)
        return "GQ-${ms.toString(16).uppercase()}-${rnd.toString(16).uppercase().padStart(4, '0')}"
    }
}
