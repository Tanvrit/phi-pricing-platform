package com.rate.sdk.ingestion.network

import com.rate.core.base.json.AppJson
import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.ingestion.model.ImportSummary
import com.rate.sdk.ingestion.model.RateMeta
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Ktor-client surface for the ADMIN ingestion console. The operator UI (sdk-ui) drives rate-version
 * management and CSV catalog re-imports through this — never the Mongo-backed repositories (those
 * are server-only). The actual file upload + Excel(POI) parsing happens server-side; this client
 * triggers it and reads the resulting [ImportSummary] / version history.
 *
 * Pure-KMP: takes an [HttpClient] (engine supplied per platform by the app shell) + a base URL.
 * Mirrors the sdk-catalog/sdk-quoting client pattern exactly (same [AppJson] config).
 */
class IngestionApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    /** The version the engine currently reads for [productLine] (null until first activation). */
    suspend fun activeVersion(productLine: ProductLine = ProductLine.RETAIL): RateMeta? =
        client.get(url("/api/ingestion/rate/active")) {
            parameter("productLine", productLine.name)
        }.body()

    /** Full version history (newest first) for the admin version picker. */
    suspend fun listVersions(productLine: ProductLine = ProductLine.RETAIL): List<RateMeta> =
        client.get(url("/api/ingestion/rate/versions")) {
            parameter("productLine", productLine.name)
        }.body()

    /** Promote a previously-imported version to active (the engine snapshot reloads server-side). */
    suspend fun activateVersion(request: ActivateVersionRequest): RateMeta =
        client.post(url("/api/ingestion/rate/activate")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * Upload a CSV catalog source bundle for (re-)seeding. The body is the parsed CSV texts; the
     * server parses with [com.rate.sdk.ingestion.handler.CsvCatalogParser] and seeds via
     * [com.rate.sdk.ingestion.handler.CatalogSeeder]. Returns the [ImportSummary].
     */
    suspend fun seedCatalog(request: SeedCatalogRequest): ImportSummary =
        client.post(url("/api/ingestion/catalog/seed")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    companion object {
        /** Install the frozen [AppJson] config on an HttpClientConfig's ContentNegotiation. */
        fun configureJson(config: HttpClientConfig<*>) {
            config.install(ContentNegotiation) { json(AppJson.json) }
        }
    }
}
