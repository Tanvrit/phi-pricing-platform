package com.rate.sdk.quoting.network

import com.rate.core.base.json.AppJson
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.sdk.quoting.handler.doc.ProspectusDocument
import com.rate.sdk.quoting.handler.doc.SalesIllustration
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Ktor-client surface for quoting. The frontend (sdk-ui) and the buy-online journey calculate /
 * save / list quotes and fetch the regulatory docs through this — never the Mongo-backed
 * QuoteRepository or the concrete PricingEngine (those are server-only). Mutations are handled
 * server-side by [com.rate.sdk.quoting.handler.QuoteHandler].
 *
 * Pure-KMP: takes an [HttpClient] (engine supplied per platform by the app shell) + a base URL.
 */
class QuoteApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    /** Price a request without persisting. */
    suspend fun calculate(request: QuoteRequest): CalculateQuoteResponse =
        client.post(url("/api/quotes/calculate")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Price + persist; returns the saved id and the priced result. */
    suspend fun save(request: QuoteRequest): SaveQuoteResponse =
        client.post(url("/api/quotes")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Re-hydrate a saved quote (inputs + priced output). */
    suspend fun get(id: String): SavedQuoteView =
        client.get(url("/api/quotes/$id")).body()

    /** Paged saved-quote summaries for dashboards (wire DTO; map with [QuoteSummaryDto.toCore]). */
    suspend fun list(req: PageRequest = PageRequest()): Page<QuoteSummaryDto> =
        client.post(url("/api/quotes/query")) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    /** Fetch the IRDAI Sales Illustration for a saved quote (tenure ≥ 3 years). */
    suspend fun salesIllustration(id: String): SalesIllustration =
        client.get(url("/api/quotes/$id/illustration")).body()

    /** Fetch the IRDAI Customer Information Sheet for a saved quote. */
    suspend fun cis(request: CisRequest): CustomerInformationSheetResponse =
        client.post(url("/api/quotes/cis")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Fetch the prospectus for a plan. */
    suspend fun prospectus(planId: String): ProspectusDocument =
        client.get(url("/api/quotes/prospectus/$planId")).body()

    /** Fetch every regulatory document available for a saved quote in one call. */
    suspend fun documents(id: String): QuoteDocuments =
        client.get(url("/api/quotes/$id/documents")).body()

    companion object {
        /** Install the frozen [AppJson] config on an HttpClientConfig's ContentNegotiation. */
        fun configureJson(config: HttpClientConfig<*>) {
            config.install(ContentNegotiation) { json(AppJson.json) }
        }
    }
}

/** Alias for the CIS response (the doc itself is the wire body). */
typealias CustomerInformationSheetResponse = com.rate.sdk.quoting.handler.doc.CustomerInformationSheet
