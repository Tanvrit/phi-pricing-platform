package com.rate.aegis.business.calculator.desktop.api

import com.rate.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.io.File

class ApiClient(private val baseUrl: String = "http://localhost:9090") {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
            })
        }
        install(Logging) { level = LogLevel.NONE }
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

    // ── Import ────────────────────────────────────────────────────────────

    suspend fun seedBuiltinData(): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/seed").body()

    suspend fun uploadExcel(file: File): Map<String, JsonElement> =
        http.post("$baseUrl/api/import/upload") {
            setBody(MultiPartFormDataContent(formData {
                append("file", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    append(HttpHeaders.ContentType, "application/vnd.ms-excel")
                })
            }))
        }.body()

    fun close() = http.close()
}
