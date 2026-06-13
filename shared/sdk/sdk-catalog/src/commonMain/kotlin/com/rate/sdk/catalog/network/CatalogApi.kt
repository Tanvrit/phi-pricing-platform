package com.rate.sdk.catalog.network

import com.rate.core.base.json.AppJson
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.Tenure
import io.ktor.client.HttpClient
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
 * Ktor-client READ surface for the catalog. The frontend (sdk-ui) and the buy-online journey
 * read products/sections/covers/add-ons/tenures and resolve zones through this — never the
 * Mongo-backed repositories (those are server-only). Mutations go through admin routes handled
 * server-side by [com.rate.sdk.catalog.handler.CatalogHandler].
 *
 * Pure-KMP: takes an [HttpClient] (engine supplied per platform by the app shell) + a base URL.
 */
class CatalogApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    /** Full catalog tree for a product code (sections + covers + add-ons + tenures). */
    suspend fun productCatalog(productCode: String): ProductCatalogView =
        client.get(url("/api/catalog/products/$productCode")).body()

    suspend fun listProducts(req: PageRequest = PageRequest()): Page<Product> =
        client.post(url("/api/catalog/products/query")) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun listCovers(req: PageRequest = PageRequest()): Page<Cover> =
        client.post(url("/api/catalog/covers/query")) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun getCover(code: String): Cover =
        client.get(url("/api/catalog/covers/$code")).body()

    suspend fun listAddOns(productLine: String = "RETAIL"): List<AddOn> =
        client.get(url("/api/catalog/addons")) { parameter("productLine", productLine) }.body()

    suspend fun listTenures(productLine: String = "RETAIL"): List<Tenure> =
        client.get(url("/api/catalog/tenures")) { parameter("productLine", productLine) }.body()

    /** Resolve the rating zone for a pincode (buy-online location step). */
    suspend fun resolveZone(pincode: String): ZoneLookupResponse =
        client.get(url("/api/catalog/zone")) { parameter("pincode", pincode) }.body()

    companion object {
        /** Install the frozen [AppJson] config on an HttpClientConfig's ContentNegotiation. */
        fun configureJson(config: io.ktor.client.HttpClientConfig<*>) {
            config.install(ContentNegotiation) { json(AppJson.json) }
        }
    }
}
