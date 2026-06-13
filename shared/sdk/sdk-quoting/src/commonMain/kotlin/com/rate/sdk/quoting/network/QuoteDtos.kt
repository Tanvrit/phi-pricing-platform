package com.rate.sdk.quoting.network

import com.rate.core.rating.ports.QuoteSummary
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.sdk.quoting.handler.doc.CustomerInformationSheet
import kotlinx.datetime.Instant
import com.rate.sdk.quoting.handler.doc.GroupBenefitSchedule
import com.rate.sdk.quoting.handler.doc.ProspectusDocument
import com.rate.sdk.quoting.handler.doc.SalesIllustration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the quoting client surface. The rating contract types ([QuoteRequest] /
 * [QuoteResult]) and the saved [com.rate.sdk.quoting.model.Quote] are already wire-serializable
 * (they ARE the JSON contract); these DTOs only wrap composed request/response views that don't
 * map 1:1 to a single core type.
 */

/** Response of POST /api/quotes/calculate — the priced result for a request (not persisted). */
@Serializable
data class CalculateQuoteResponse(
    @SerialName("result") val result: QuoteResult,
)

/** Response of POST /api/quotes (calculate + save) — the saved id plus the priced result. */
@Serializable
data class SaveQuoteResponse(
    @SerialName("quoteId") val quoteId: String,
    @SerialName("result") val result: QuoteResult,
)

/** Re-hydrated saved quote (inputs + priced output) returned by GET /api/quotes/{id}. */
@Serializable
data class SavedQuoteView(
    @SerialName("quoteId") val quoteId: String,
    @SerialName("request") val request: QuoteRequest,
    @SerialName("result") val result: QuoteResult,
)

/** Request to render the IRDAI Customer Information Sheet for a saved quote / proposal. */
@Serializable
data class CisRequest(
    @SerialName("quoteId") val quoteId: String,
    @SerialName("proposalNumber") val proposalNumber: String,
    @SerialName("customerName") val customerName: String,
    @SerialName("customerMobile") val customerMobile: String,
)

/**
 * Wire-serializable mirror of core's `QuoteSummary` (which is a plain — non-`@Serializable` —
 * data class, by design, since it is an in-process port projection). The client list endpoint
 * returns `Page<QuoteSummaryDto>`; map to/from the core type with [toCore] / [fromCore].
 */
@Serializable
data class QuoteSummaryDto(
    @SerialName("id") val id: String,
    @SerialName("createdAt") val createdAt: Instant,
    @SerialName("request") val request: QuoteRequest,
    @SerialName("totalIncludingGst") val totalIncludingGst: Double,
    @SerialName("isValid") val isValid: Boolean = true,
) {
    fun toCore(): QuoteSummary = QuoteSummary(id, createdAt, request, totalIncludingGst, isValid)

    companion object {
        fun fromCore(s: QuoteSummary): QuoteSummaryDto =
            QuoteSummaryDto(s.id, s.createdAt, s.request, s.totalIncludingGst, s.isValid)
    }
}

/** Bundle of the regulatory documents the client can fetch/preview for a quote. */
@Serializable
data class QuoteDocuments(
    @SerialName("salesIllustration") val salesIllustration: SalesIllustration? = null,
    @SerialName("cis") val cis: CustomerInformationSheet? = null,
    @SerialName("prospectus") val prospectus: ProspectusDocument? = null,
    @SerialName("groupBenefitSchedule") val groupBenefitSchedule: GroupBenefitSchedule? = null,
)
