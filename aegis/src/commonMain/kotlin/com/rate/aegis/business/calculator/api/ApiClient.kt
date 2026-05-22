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

    // ── Import (seed only — file upload is JVM-side, see jvmMain extension) ──

    suspend fun seedBuiltinData(): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/seed").body()

    fun close() = http.close()
}
