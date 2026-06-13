package com.rate.sdk.quoting

import com.rate.core.base.error.AppResult
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.QuoteRepository
import com.rate.core.rating.ports.QuoteSummary
import com.rate.core.rating.ports.RatingPort
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import com.rate.core.rating.ports.model.YearBreakdown
import com.rate.sdk.quoting.event.QuoteEvent
import com.rate.sdk.quoting.event.QuoteEventSink
import com.rate.sdk.quoting.handler.QuoteHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun sampleRequest(
    planId: String = "PHI_BASIC",
    sumInsured: Long = 1_000_000L,
    members: List<Member> = listOf(Member(1, 35, "self")),
) = QuoteRequest(
    planId = planId,
    primaryAge = 35,
    sumInsured = sumInsured,
    familyType = "1A",
    zone = "Zone 1",
    tenure = Tenure.THREE_YEARS,
    paymentMode = PaymentMode.ANNUAL,
    paymentTenure = Tenure.ONE_YEAR,
    members = members,
)

private fun sampleResult(planId: String = "PHI_BASIC", valid: Boolean = true) = QuoteResult(
    requestId = "req-1",
    planId = planId,
    basePremiumTotal = 30_000.0,
    coverBreakdown = emptyList(),
    totalAddons = 3_000.0,
    uwLoadingAmount = 0.0,
    totalBeforeDiscount = 33_000.0,
    discountBreakdown = emptyList(),
    totalDiscountAmount = 3_300.0,
    totalAfterDiscount = 29_700.0,
    instalmentLoadingAmount = 0.0,
    instalmentPremium = 29_700.0,
    instalmentCount = 1,
    yearlyBreakdown = listOf(
        YearBreakdown(1, 35, "31 - 35", 10_000.0, emptyMap(), 9_900.0),
        YearBreakdown(2, 36, "36 - 40", 10_000.0, emptyMap(), 9_900.0),
        YearBreakdown(3, 37, "36 - 40", 10_000.0, emptyMap(), 9_900.0),
    ),
    isValid = valid,
    gstRate = 0.18,
    gstAmount = 5_346.0,
    totalIncludingGst = 35_046.0,
    rateTableVersion = "test-v1",
    calculatedAt = Now.instant(),
)

private class FakeRatingPort(private val result: QuoteResult) : RatingPort {
    var rateCalls = 0
    override suspend fun rate(request: QuoteRequest): QuoteResult {
        rateCalls++
        return result
    }
}

private class FakeQuoteRepository : QuoteRepository {
    val saved = mutableMapOf<String, Pair<QuoteRequest, QuoteResult>>()
    override suspend fun saveQuote(request: QuoteRequest, result: QuoteResult): String {
        val id = "Q${saved.size + 1}"
        saved[id] = request to result
        return id
    }
    override suspend fun getQuote(id: String): Pair<QuoteRequest, QuoteResult>? = saved[id]
    override suspend fun listQuoteSummaries(req: PageRequest): Page<QuoteSummary> =
        Page(
            items = saved.entries.map { (id, p) ->
                QuoteSummary(id, Now.instant(), p.first, p.second.totalIncludingGst, p.second.isValid)
            },
            total = saved.size.toLong(),
            page = req.page,
            size = req.size,
        )
}

class QuoteHandlerTest {

    @Test
    fun calculate_delegates_to_rating_port() = runTest {
        val port = FakeRatingPort(sampleResult())
        val handler = QuoteHandler(port, FakeQuoteRepository())
        val result = handler.calculate(sampleRequest())
        assertEquals(1, port.rateCalls)
        assertTrue(result.isValid)
        assertEquals(35_046.0, result.totalIncludingGst)
    }

    @Test
    fun calculate_preValidates_before_engine() = runTest {
        val port = FakeRatingPort(sampleResult())
        val handler = QuoteHandler(port, FakeQuoteRepository())
        val result = handler.calculate(sampleRequest(sumInsured = 0L))
        assertEquals(0, port.rateCalls) // engine never called
        assertFalse(result.isValid)
        assertTrue(result.validationErrors.any { it.contains("sumInsured") })
    }

    @Test
    fun calculateAndSave_persists_and_emits_event() = runTest {
        val port = FakeRatingPort(sampleResult())
        val repo = FakeQuoteRepository()
        val events = mutableListOf<QuoteEvent>()
        val handler = QuoteHandler(port, repo, QuoteEventSink { events += it })

        val saved = handler.calculateAndSave(sampleRequest(), actor = "op-1")
        assertTrue(saved is AppResult.Ok)
        val ok = (saved as AppResult.Ok).value
        assertEquals(1, repo.saved.size)
        assertTrue(repo.saved.containsKey(ok.id))
        assertEquals(1, events.size)
        assertTrue(events.first() is QuoteEvent.QuoteCalculated)
    }

    @Test
    fun calculateAndSave_does_not_persist_invalid() = runTest {
        val port = FakeRatingPort(sampleResult(valid = false))
        val repo = FakeQuoteRepository()
        val handler = QuoteHandler(port, repo)
        val saved = handler.calculateAndSave(sampleRequest())
        assertTrue(saved is AppResult.Err)
        assertEquals(0, repo.saved.size)
    }

    @Test
    fun get_rehydrates_saved_quote() = runTest {
        val port = FakeRatingPort(sampleResult())
        val repo = FakeQuoteRepository()
        val handler = QuoteHandler(port, repo)
        val id = (handler.calculateAndSave(sampleRequest()) as AppResult.Ok).value.id
        val fetched = handler.get(id)
        assertTrue(fetched is AppResult.Ok)
        assertEquals("PHI_BASIC", (fetched as AppResult.Ok).value.request.planId)
    }

    @Test
    fun get_missing_returns_notFound() = runTest {
        val handler = QuoteHandler(FakeRatingPort(sampleResult()), FakeQuoteRepository())
        assertTrue(handler.get("nope") is AppResult.Err)
    }
}
