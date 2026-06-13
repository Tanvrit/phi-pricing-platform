package com.rate.sdk.quoting.handler

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.QuoteRepository
import com.rate.core.rating.ports.QuoteSummary
import com.rate.core.rating.ports.RatingPort
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.sdk.quoting.event.QuoteEvent
import com.rate.sdk.quoting.event.QuoteEventSink

/**
 * Orchestrates quoting: price a [QuoteRequest] through the injected [RatingPort] (never the
 * concrete PricingEngine — that single cross-feature edge is inverted via the core PORT) and
 * persist the priced result through the core [QuoteRepository] (Mongo-backed actual lives in
 * server-persistence).
 *
 * Pure orchestration — no IO of its own beyond the two injected PORTs, so it runs identically
 * on the server and (where a RatingPort is available) offline on the client.
 *
 * The handler keeps to the rating contract's Double-throughout convention: [QuoteResult] stays
 * Double; Money conversion happens only at display/document assembly (see [com.rate.sdk.quoting.model.Quote]
 * and the doc builders).
 */
class QuoteHandler(
    private val rating: RatingPort,
    private val repository: QuoteRepository,
    private val events: QuoteEventSink = QuoteEventSink.NOOP,
) {

    /**
     * Price a request without persisting. The engine itself reports validation problems on the
     * [QuoteResult] (`isValid` / `validationErrors`); this method also surfaces obvious caller
     * mistakes (empty members, non-positive SI) before the engine runs so they fail fast.
     */
    suspend fun calculate(request: QuoteRequest): QuoteResult {
        val preErrors = preValidate(request)
        if (preErrors.isNotEmpty()) {
            return invalidResult(request, preErrors)
        }
        return rating.rate(request)
    }

    /**
     * Price + persist in one step. Returns the saved quote id, or a [DomainError] when the
     * engine rejects the request (we do not persist an unratable quote).
     */
    suspend fun calculateAndSave(request: QuoteRequest, actor: String? = null): AppResult<SavedQuote> {
        val result = calculate(request)
        if (!result.isValid) {
            return AppResult.Err(DomainError.Validation(result.validationErrors))
        }
        val id = repository.saveQuote(request, result)
        val line = lineOf(request)
        events.emit(
            QuoteEvent.QuoteCalculated(
                quoteId = id,
                productLine = line,
                planId = request.planId,
                totalIncludingGst = result.totalIncludingGst,
                actor = actor,
                at = Now.instant(),
            ),
        )
        return AppResult.Ok(SavedQuote(id, request, result, line))
    }

    /** Re-hydrate a saved quote (inputs + priced output) by id. */
    suspend fun get(id: String): AppResult<SavedQuote> {
        val pair = repository.getQuote(id)
            ?: return AppResult.Err(DomainError.NotFound("Quote", id))
        val (request, result) = pair
        return AppResult.Ok(SavedQuote(id, request, result, lineOf(request)))
    }

    /** Paged saved-quote summaries for dashboards (delegates to the repository PORT). */
    suspend fun list(req: PageRequest = PageRequest()): Page<QuoteSummary> =
        repository.listQuoteSummaries(req)

    // ── internals ────────────────────────────────────────────────────────────

    private fun preValidate(request: QuoteRequest): List<String> = buildList {
        if (request.planId.isBlank()) add("planId is required")
        if (request.sumInsured <= 0) add("sumInsured must be positive")
        if (request.members.isEmpty()) add("At least one member is required")
        if (request.zone.isBlank()) add("zone is required")
        if (request.familyType.isBlank()) add("familyType is required")
        if (request.uwLoadingFactor < 0.0) add("uwLoadingFactor cannot be negative")
        if (request.maxDiscountCap < 0.0 || request.maxDiscountCap > 1.0) {
            add("maxDiscountCap must be between 0 and 1")
        }
    }

    /** Group quotes are detected by family-type convention used across the platform. */
    private fun lineOf(request: QuoteRequest): ProductLine =
        if (request.familyType.equals("multi", ignoreCase = true)) ProductLine.GROUP else ProductLine.RETAIL

    private fun invalidResult(request: QuoteRequest, errors: List<String>): QuoteResult =
        QuoteResult(
            requestId = "",
            planId = request.planId,
            basePremiumTotal = 0.0,
            coverBreakdown = emptyList(),
            totalAddons = 0.0,
            uwLoadingAmount = 0.0,
            totalBeforeDiscount = 0.0,
            discountBreakdown = emptyList(),
            totalDiscountAmount = 0.0,
            totalAfterDiscount = 0.0,
            instalmentLoadingAmount = 0.0,
            instalmentPremium = 0.0,
            instalmentCount = 0,
            yearlyBreakdown = emptyList(),
            isValid = false,
            validationErrors = errors,
            calculatedAt = Now.instant(),
        )
}

/** The result of [QuoteHandler.calculateAndSave] / [QuoteHandler.get] — id + inputs + output. */
data class SavedQuote(
    val id: String,
    val request: QuoteRequest,
    val result: QuoteResult,
    val productLine: ProductLine,
)
