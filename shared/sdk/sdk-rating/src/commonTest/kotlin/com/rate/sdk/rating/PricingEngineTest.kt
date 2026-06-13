package com.rate.sdk.rating

import com.rate.core.rating.ports.RatingPort
import com.rate.core.rating.ports.model.CoverParam
import com.rate.core.rating.ports.model.CoverSelection
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.rating.data.InProcessRateDataProvider
import com.rate.sdk.rating.handler.PricingEngine
import com.rate.sdk.rating.model.CoverIds
import com.rate.sdk.rating.repository.RateDataRenewalProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingEngineTest {

    private val engine = PricingEngine(InProcessRateDataProvider())

    private fun baseRequest(
        covers: List<CoverSelection> = emptyList(),
        uw: Double = 0.0,
        tenure: Tenure = Tenure.ONE_YEAR,
    ) = QuoteRequest(
        planId = "PHI_BASIC",
        primaryAge = 35,
        sumInsured = 2_500_000L,
        familyType = "2A",
        zone = "Zone 1",
        tenure = tenure,
        paymentMode = PaymentMode.ANNUAL,
        paymentTenure = tenure,
        members = listOf(
            Member(1, 35, "self", "M"),
            Member(2, 33, "spouse", "F"),
        ),
        selectedCovers = covers,
        uwLoadingFactor = uw,
    )

    @Test
    fun baseQuoteIsValidAndPositive() = runTest {
        val r = engine.calculate(baseRequest())
        assertTrue(r.isValid, "expected valid: ${r.validationErrors}")
        assertTrue(r.basePremiumTotal > 0.0)
        assertTrue(r.totalIncludingGst > r.totalBeforeDiscount)
        assertEquals(0.18, r.gstRate)
    }

    @Test
    fun ratingPortRouteEqualsCalculate() = runTest {
        val port: RatingPort = engine
        val viaPort = port.rate(baseRequest())
        val viaCalc = engine.calculate(baseRequest())
        assertEquals(viaCalc.basePremiumTotal, viaPort.basePremiumTotal)
    }

    @Test
    fun discountCapNeverExceedsPlanCap() = runTest {
        // Stack all cover-pass discounts to try to exceed the 30% cap.
        val covers = listOf(
            CoverSelection(CoverIds.SMART_SELECT),
            CoverSelection(CoverIds.CO_PAY, CoverParam(param1 = "10%")),
        )
        val r = engine.calculate(baseRequest(covers = covers))
        assertTrue(r.isValid, "expected valid: ${r.validationErrors}")
        val cap = r.totalBeforeDiscount * 0.30
        assertTrue(
            kotlin.math.abs(r.totalDiscountAmount) <= cap + 0.01,
            "discount ${r.totalDiscountAmount} exceeded cap $cap",
        )
    }

    @Test
    fun uwFactorIsClampedAndNoted() = runTest {
        val r = engine.calculate(baseRequest(uw = 10.0))
        assertTrue(r.isValid)
        assertTrue(r.validationErrors.any { it.contains("clamped") })
    }

    @Test
    fun invalidFamilyTypeYieldsInvalidResult() = runTest {
        val r = engine.calculate(baseRequest().copy(familyType = "ZZZ"))
        assertTrue(!r.isValid)
        assertTrue(r.validationErrors.isNotEmpty())
    }

    @Test
    fun renewalAdapterReturnsAgeBandedBase() = runTest {
        val data = InProcessRateDataProvider()
        val renewal = RateDataRenewalProvider(data)
        val y1 = renewal.baseFor("PHI_BASIC", 35, 2_500_000L, "2A", "Zone 1")
        val y2 = renewal.baseFor("PHI_BASIC", 46, 2_500_000L, "2A", "Zone 1")
        assertTrue(y1 > 0.0)
        assertTrue(y2 > y1, "older life should cost more: $y2 vs $y1")
    }
}
