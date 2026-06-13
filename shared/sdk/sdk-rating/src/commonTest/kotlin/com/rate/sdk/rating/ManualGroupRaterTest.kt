package com.rate.sdk.rating

import com.rate.core.money.Money
import com.rate.sdk.rating.handler.ManualGroupRater
import com.rate.sdk.rating.model.ManualClaimYear
import com.rate.sdk.rating.model.ManualDemographyRow
import com.rate.sdk.rating.model.ManualGroupRateInput
import com.rate.sdk.rating.model.ManualGroupStrategyMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualGroupRaterTest {

    @Test
    fun sampleManualQuoteReconcilesToBlended() = runTest {
        val r = ManualGroupRater.rate(ManualGroupRateInput.sample())

        assertTrue(r.isValid, "errors: ${r.validationErrors}")
        // 18+12+6 (grade A) + 30+20+10 (grade B) = 96 lives.
        assertEquals(96, r.totalLives)
        assertEquals(2, r.perGrade.size)
        assertEquals("manual", r.strategy)

        // Manual overrides flow straight through (5% discount, 10% loading).
        assertEquals(0.05, r.groupSizeDiscount)
        assertEquals(0.10, r.industryLoading)

        // Per-member allocations reconcile to the blended premium (exact paise).
        val sumMembers = r.perMember.sumOf { it.allocatedPremium.paise }
        assertEquals(r.blendedPremium.paise, sumMembers)

        // Per-grade allocations also reconcile to the blended premium.
        val sumGrades = r.perGrade.sumOf { it.allocatedPremium.paise }
        assertEquals(r.blendedPremium.paise, sumGrades)

        // total = blended + gst, employer-funded by default → employee leg zero.
        assertEquals((r.blendedPremium + r.gst).paise, r.total.paise)
        assertEquals(Money.ZERO, r.allocation.employeeAmount)
    }

    @Test
    fun manualBookPremiumMatchesOperatorTypedRates() = runTest {
        // Single grade, single band: 10 lives × ₹8,000/life = ₹80,000 book premium (no factors).
        val input = ManualGroupRateInput(
            groupConfigId = "GHI_X",
            groupSizeDiscountPct = 0.0,
            industryLoadingPct = 0.0,
            mode = ManualGroupStrategyMode.MANUAL,
            demography = listOf(
                ManualDemographyRow("A", "26-35", 26, lives = 10, sumInsured = 1_000_000L, ratePerLifeRupees = 8_000L),
            ),
        )
        val r = ManualGroupRater.rate(input)
        assertTrue(r.isValid)
        assertEquals(Money.fromRupees(80_000L).paise, r.manualPremium.paise)
        // No size discount / loading / experience → blended == manual.
        assertEquals(r.manualPremium.paise, r.blendedPremium.paise)
    }

    @Test
    fun raisingIndustryLoadingRaisesTotal() = runTest {
        val base = ManualGroupRateInput.sample()
        val low = ManualGroupRater.rate(base.copy(industryLoadingPct = 0.0))
        val high = ManualGroupRater.rate(base.copy(industryLoadingPct = 25.0))

        assertTrue(low.isValid && high.isValid)
        assertTrue(
            high.total.paise > low.total.paise,
            "higher industry loading must raise total: ${low.total.paise} -> ${high.total.paise}",
        )
        assertEquals(0.25, high.industryLoading)
    }

    @Test
    fun experienceModeBlendsTowardClaims() = runTest {
        val input = ManualGroupRateInput.sample().copy(
            mode = ManualGroupStrategyMode.EXPERIENCE,
            expenseRatioPct = 20.0,
            claimYears = listOf(
                ManualClaimYear("FY24", averageLives = 90, earnedPremium = Money.fromRupees(4_000_000L), incurredClaims = Money.fromRupees(2_400_000L), claimCount = 35),
                ManualClaimYear("FY25", averageLives = 96, earnedPremium = Money.fromRupees(4_500_000L), incurredClaims = Money.fromRupees(2_900_000L), claimCount = 45),
            ),
        )
        val r = ManualGroupRater.rate(input)
        assertTrue(r.isValid, "errors: ${r.validationErrors}")
        assertEquals("experience", r.strategy)
        assertTrue(r.experiencePremium.paise > 0L)
        assertTrue(r.credibility in 0.0..1.0)
        // Blended sits between the manual and experience legs (inclusive when Z is 0 or 1).
        val lo = minOf(r.manualPremium.paise, r.experiencePremium.paise)
        val hi = maxOf(r.manualPremium.paise, r.experiencePremium.paise)
        assertTrue(r.blendedPremium.paise in lo..hi)
    }

    @Test
    fun hybridUsesOperatorWeight() = runTest {
        // manualWeightPct = 100 → pure manual: blended == adjusted manual, credibility 0.
        val input = ManualGroupRateInput.sample().copy(
            mode = ManualGroupStrategyMode.HYBRID,
            manualWeightPct = 100.0,
            claimYears = listOf(
                ManualClaimYear("FY25", averageLives = 96, earnedPremium = Money.fromRupees(4_500_000L), incurredClaims = Money.fromRupees(9_000_000L), claimCount = 60),
            ),
        )
        val r = ManualGroupRater.rate(input)
        assertTrue(r.isValid)
        assertEquals("hybrid", r.strategy)
        assertEquals(0.0, r.credibility)
        assertEquals(r.manualPremium.paise, r.blendedPremium.paise)
    }

    @Test
    fun contributorySplitReconciles() = runTest {
        val r = ManualGroupRater.rate(ManualGroupRateInput.sample().copy(employerSharePct = 60.0))
        assertTrue(r.isValid)
        assertEquals(0.6, r.allocation.employerShare)
        assertEquals(
            r.total.paise,
            r.allocation.employerAmount.paise + r.allocation.employeeAmount.paise,
        )
    }

    @Test
    fun emptyDemographyIsInvalid() = runTest {
        val r = ManualGroupRater.rate(ManualGroupRateInput(groupConfigId = "GHI_EMPTY"))
        assertTrue(!r.isValid)
        assertTrue(r.validationErrors.isNotEmpty())
        assertEquals(0, r.totalLives)
    }
}
