package com.rate.sdk.rating

import com.rate.sdk.party.model.Sex
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusMember
import com.rate.sdk.rating.data.InProcessGroupRateDataProvider
import com.rate.sdk.rating.handler.GroupPricingEngine
import com.rate.sdk.rating.model.ClaimYear
import com.rate.sdk.rating.model.RatingStrategy
import com.rate.core.money.Money
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupPricingEngineTest {

    private val engine = GroupPricingEngine(InProcessGroupRateDataProvider())

    private fun census(n: Int, grade: String = "A", si: Long = 1_000_000L): Census {
        val members = (1..n).map {
            CensusMember(
                empId = "E$it",
                age = 25 + (it % 40),
                gender = if (it % 2 == 0) Sex.FEMALE else Sex.MALE,
                grade = grade,
                sumInsured = si,
            )
        }
        return Census(employerPartyRef = "emp1", lives = n, members = members)
    }

    @Test
    fun manualGroupQuoteReconcilesToTotal() = runTest {
        val r = engine.rate(
            groupConfigId = "GHI_STD",
            industryCode = "IT",
            census = census(120),
            strategy = RatingStrategy.Manual,
        )
        assertTrue(r.isValid, "errors: ${r.validationErrors}")
        assertEquals(120, r.totalLives)
        // per-member allocations reconcile to the blended premium (exact paise).
        val sumMembers = r.perMember.sumOf { it.allocatedPremium.paise }
        assertEquals(r.blendedPremium.paise, sumMembers)
        // 120 lives → 10% size discount table entry.
        assertEquals(0.10, r.groupSizeDiscount)
        // total = blended + gst
        assertEquals((r.blendedPremium + r.gst).paise, r.total.paise)
        // employer-funded by default → employee leg zero.
        assertEquals(Money.ZERO, r.allocation.employeeAmount)
    }

    @Test
    fun industryLoadingRaisesManualPremium() = runTest {
        val it = engine.rate("GHI_STD", "IT", census(60), RatingStrategy.Manual)
        val mining = engine.rate("GHI_STD", "MINING", census(60), RatingStrategy.Manual)
        assertTrue(mining.manualPremium > it.manualPremium)
        assertEquals(0.30, mining.industryLoading)
    }

    @Test
    fun experienceBlendUsesCredibility() = runTest {
        val history = listOf(
            ClaimYear("FY24", averageLives = 100, earnedPremium = Money.fromRupees(5_000_000L), incurredClaims = Money.fromRupees(3_000_000L), claimCount = 40),
            ClaimYear("FY25", averageLives = 110, earnedPremium = Money.fromRupees(5_500_000L), incurredClaims = Money.fromRupees(3_500_000L), claimCount = 50),
        )
        val r = engine.rate(
            groupConfigId = "GHI_STD",
            industryCode = "IT",
            census = census(110),
            strategy = RatingStrategy.Experience(history = history, expenseRatio = 0.20),
        )
        assertTrue(r.isValid)
        assertTrue(r.experiencePremium.paise > 0L)
        assertTrue(r.credibility in 0.0..1.0)
        // blended sits between manual and experience legs (or equals one when Z is 0/1).
        val lo = minOf(r.manualPremium.paise, r.experiencePremium.paise)
        val hi = maxOf(r.manualPremium.paise, r.experiencePremium.paise)
        assertTrue(r.blendedPremium.paise in lo..hi)
    }

    @Test
    fun contributorySplitReconciles() = runTest {
        val r = engine.rate(
            groupConfigId = "GHI_STD",
            industryCode = "IT",
            census = census(80),
            strategy = RatingStrategy.Manual,
            employerShare = 0.6,
        )
        assertEquals(r.total.paise, r.allocation.employerAmount.paise + r.allocation.employeeAmount.paise)
        assertEquals(0.6, r.allocation.employerShare)
    }

    @Test
    fun emptyCensusIsInvalid() = runTest {
        val r = engine.rate("GHI_STD", "IT", census(0), RatingStrategy.Manual)
        assertTrue(!r.isValid)
        assertTrue(r.validationErrors.isNotEmpty())
    }
}
