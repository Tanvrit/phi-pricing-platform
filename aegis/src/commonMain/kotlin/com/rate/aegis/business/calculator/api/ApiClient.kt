package com.rate.aegis.business.calculator.api

import com.rate.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * Operator-side HTTP client for the BUSINESS calculator. Multiplatform-friendly —
 * no explicit engine declared so Ktor auto-discovers (CIO on JVM, JS on WASM).
 *
 * The Excel upload endpoint that used to live here required `java.io.File` and so
 * was JVM-only; it has been split out into `ApiClient.uploadExcel()` as a JVM-only
 * extension in `:aegis/jvmMain` so the rest of the client can run in the browser.
 */
class ApiClient(val baseUrl: String = "http://localhost:9090") {

    val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
            })
        }
    }

    // ── Covers ────────────────────────────────────────────────────────────

    suspend fun getCovers(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/covers").body()

    suspend fun getAgeBands(): List<AgeBand> =
        http.get("$baseUrl/api/covers/age-bands").body()

    suspend fun getFamilyTypes(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/covers/family-types").body()

    suspend fun getSumInsureds(): Map<String, JsonElement> =
        http.get("$baseUrl/api/covers/sum-insureds").body()

    // ── Plans ─────────────────────────────────────────────────────────────

    suspend fun getPlans(): List<Plan> =
        http.get("$baseUrl/api/plans").body()

    suspend fun getPlan(id: String): Plan =
        http.get("$baseUrl/api/plans/$id").body()

    suspend fun savePlan(plan: Plan): Plan {
        val resp = http.post("$baseUrl/api/plans") {
            contentType(ContentType.Application.Json)
            setBody(plan)
        }
        return resp.body()
    }

    suspend fun deletePlan(id: String) {
        http.delete("$baseUrl/api/plans/$id")
    }

    // ── Quotes ────────────────────────────────────────────────────────────

    suspend fun calculate(request: QuoteRequest): QuoteResult =
        http.post("$baseUrl/api/quotes/calculate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun saveQuote(request: QuoteRequest): Map<String, JsonElement> =
        http.post("$baseUrl/api/quotes") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun listQuotes(limit: Int = 50): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/quotes?limit=$limit").body()

    // ── Audit ─────────────────────────────────────────────────────────────

    /**
     * Returns the newest [limit] audit events as raw JSON maps so the Aegis
     * surface can parse fields itself (matches the pattern of [listQuotes]).
     * Server caps the limit at 500 regardless of what we send.
     */
    suspend fun getAuditEvents(limit: Int = 100): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/audit/events?limit=$limit").body()

    // ── Discounts ─────────────────────────────────────────────────────────

    /**
     * Returns the discount catalogue with server-resolved rates. Same
     * parse-on-client shape as [listQuotes] / [getAuditEvents] so the Aegis
     * surface can pick fields without dragging a serialization schema across
     * the wire.
     */
    suspend fun getDiscounts(): List<Map<String, JsonElement>> =
        http.get("$baseUrl/api/discounts").body()

    // ── Import (seed only — file upload is JVM-side, see jvmMain extension) ──

    suspend fun seedBuiltinData(): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/seed").body()

    fun close() = http.close()
}
