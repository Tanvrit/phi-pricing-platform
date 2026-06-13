package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.QuoteRepository
import com.rate.core.rating.ports.QuoteSummary
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.quoting.model.Quote
import com.rate.sdk.quoting.model.RetailQuote

/**
 * Mongo actual for the core [QuoteRepository] PORT, backed by the sealed [Quote] entity
 * (RetailQuote / GroupQuote) so saved quotes round-trip with the `_class` discriminator and
 * are listable on dashboards.
 *
 * The core PORT speaks (QuoteRequest, QuoteResult); since that signature carries no product
 * line, [saveQuote] wraps them in a [RetailQuote] (the default line). The richer GROUP path
 * persists a [GroupQuote] via [saveQuoteEntity]. The saved `_id` mirrors `result.requestId`,
 * preserving the monolith's behaviour (the request id IS the quote id).
 *
 * [listQuoteSummaries] reads the indexed documents newest-first and projects each into the
 * lightweight [QuoteSummary] (relocated dashboard projection).
 */
class QuoteRepositoryImpl(db: MongoDatabase) :
    MongoRepository<Quote>(db, CollectionNames.QUOTES, Quote::class.java),
    QuoteRepository {

    override suspend fun saveQuote(request: QuoteRequest, result: QuoteResult): String {
        val quote = RetailQuote(
            id = result.requestId,
            request = request,
            result = result,
        )
        upsertEntity(quote)
        return quote.id
    }

    /** GROUP/richer save path — persists any concrete [Quote] subtype verbatim (idempotent). */
    suspend fun saveQuoteEntity(quote: Quote): Quote {
        upsertEntity(quote)
        return quote
    }

    override suspend fun getQuote(id: String): Pair<QuoteRequest, QuoteResult>? =
        findById(id)?.let { it.request to it.result }

    override suspend fun listQuoteSummaries(req: PageRequest): Page<QuoteSummary> {
        val page = paged(req)
        return Page(
            items = page.items.map { it.toSummary() },
            total = page.total,
            page = page.page,
            size = page.size,
        )
    }

    private fun Quote.toSummary(): QuoteSummary = QuoteSummary(
        id = id,
        createdAt = createdAt,
        request = request,
        totalIncludingGst = result.totalIncludingGst,
        isValid = result.isValid,
    )
}
