package com.rate.core.network.client

import com.rate.core.base.json.AppJson
import com.rate.core.network.ApiResponse
import com.rate.core.network.auth.AuthProvider
import com.rate.core.network.auth.NoAuthProvider
import com.rate.core.network.error.ErrorMapping
import com.rate.core.network.error.NetworkError
import com.rate.core.network.error.NetworkException
import com.rate.core.network.retry.RetryPolicy
import com.rate.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/**
 * The shared HTTP client used by BOTH the backend (service-to-service calls) and
 * the frontend (Compose). Wraps a Ktor [HttpClient] over the platform engine and
 * wires the whole stack ONCE:
 *  - ContentNegotiation with the frozen [AppJson.json] (wire contract parity)
 *  - HttpTimeout from [TanvritClientConfig.timeouts]
 *  - optional request/response Logging
 *  - per-request `Authorization: Bearer` from the [AuthProvider] + static defaults
 *  - exponential-backoff [RetryPolicy] on transient faults
 *  - non-2xx → status-aware [NetworkError] (raised as [NetworkException])
 *
 * Typed helpers ([getJson]/[postJson]/[putJson]/[delete]/[getText]/[postMultipart])
 * cover everything the relocated aegis ApiClient + BuyOnlineApiClient need: JSON
 * round-trips, raw text (HTML/Prometheus), multipart upload, query params and
 * per-call headers (e.g. the `X-Aegis-Actor` attribution header).
 *
 * Feature SDKs build their `network/` clients ON TOP of this — they own the routes
 * and DTOs; this owns the transport.
 */
class TanvritClient(
    val config: TanvritClientConfig,
    private val auth: AuthProvider = NoAuthProvider,
    /** Static headers re-read per request (e.g. dynamic actor identity). */
    private val dynamicHeaders: suspend () -> Map<String, String> = { emptyMap() },
) {
    private val retryPolicy = RetryPolicy(config.retry)

    val http: HttpClient = buildHttpClient {
        install(ContentNegotiation) { json(AppJson.json) }

        install(HttpTimeout) {
            connectTimeoutMillis = config.timeouts.connectMillis
            requestTimeoutMillis = config.timeouts.requestMillis
            socketTimeoutMillis = config.timeouts.socketMillis
        }

        if (config.enableLogging) {
            install(Logging) {
                level = when (config.logLevel) {
                    NetworkLogLevel.NONE -> LogLevel.NONE
                    NetworkLogLevel.INFO -> LogLevel.INFO
                    NetworkLogLevel.HEADERS -> LogLevel.HEADERS
                    NetworkLogLevel.BODY -> LogLevel.BODY
                    NetworkLogLevel.ALL -> LogLevel.ALL
                }
            }
        }

        install(DefaultRequest) {
            config.userAgent?.let { header(HttpHeaders.UserAgent, it) }
            config.defaultHeaders.forEach { (k, v) -> header(k, v) }
        }
    }

    // ── Core request primitive ───────────────────────────────────────────────

    /**
     * Execute an HTTP request with auth + retry + status mapping, returning the
     * raw [HttpResponse] on 2xx. Non-2xx is raised as a [NetworkException] so the
     * status-aware error survives to [safeCall]. The auth token and dynamic
     * headers are (re)applied on every (re)try so a refreshed token takes effect.
     */
    suspend fun execute(
        method: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val url = config.resolve(path)
        return retryPolicy.retry(classify = { ErrorMapping.fromThrowable(it) }) {
            // Resolve suspend-sourced values OUTSIDE the (non-suspend) request block,
            // and re-resolve each attempt so a refreshed token / new actor takes effect.
            val bearer = auth.token()?.takeIf { it.isNotBlank() }
            val dynamic = dynamicHeaders()
            val response = http.request(url) {
                this.method = method
                bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                dynamic.forEach { (k, v) -> if (v.isNotBlank()) header(k, v) }
                configure()
            }
            if (!response.status.isSuccess()) {
                val error = ErrorMapping.fromResponse(response)
                if (error is NetworkError.Unauthorized) auth.onUnauthorized()
                // Throw so the RetryPolicy can decide (5xx/429 retried, 4xx not),
                // and safeCall can convert to ApiResponse.Failure preserving status.
                throw NetworkException(error)
            }
            response
        }
    }

    // ── Typed helpers (throwing — wrap in safeCall for ApiResponse) ───────────

    /** GET returning a deserialized [T]. */
    suspend inline fun <reified T> getJson(
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): T = execute(HttpMethod.Get, path) {
        applyQuery(params); applyHeaders(headers)
    }.body()

    /** POST a JSON [body], returning a deserialized [R]. */
    suspend inline fun <reified B, reified R> postJson(
        path: String,
        body: B,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): R = execute(HttpMethod.Post, path) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyQuery(params); applyHeaders(headers)
    }.body()

    /** POST with no request body, returning a deserialized [R]. */
    suspend inline fun <reified R> postEmpty(
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): R = execute(HttpMethod.Post, path) {
        applyQuery(params); applyHeaders(headers)
    }.body()

    /** PUT a JSON [body], returning a deserialized [R]. */
    suspend inline fun <reified B, reified R> putJson(
        path: String,
        body: B,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): R = execute(HttpMethod.Put, path) {
        contentType(ContentType.Application.Json)
        setBody(body)
        applyQuery(params); applyHeaders(headers)
    }.body()

    /** DELETE; body (if any) ignored. Returns the 2xx status code. */
    suspend fun delete(
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): Int = execute(HttpMethod.Delete, path) {
        applyQuery(params); applyHeaders(headers)
    }.status.value

    /** GET raw text (e.g. server-rendered HTML, Prometheus exposition). */
    suspend fun getText(
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): String = execute(HttpMethod.Get, path) {
        applyQuery(params); applyHeaders(headers)
    }.bodyAsText()

    /**
     * Multipart file upload by raw [bytes] + [filename]. Multiplatform — no
     * java.io.File. [partName] is the form field name; [contentType] defaults to
     * the legacy `application/vnd.ms-excel` to match the relocated Excel-import
     * call byte-for-byte.
     */
    suspend inline fun <reified R> postMultipart(
        path: String,
        bytes: ByteArray,
        filename: String,
        partName: String = "file",
        contentType: String = "application/vnd.ms-excel",
        headers: Map<String, String> = emptyMap(),
    ): R = execute(HttpMethod.Post, path) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        partName,
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            append(HttpHeaders.ContentType, contentType)
                        },
                    )
                },
            ),
        )
        applyHeaders(headers)
    }.body()

    fun close() = http.close()
}

// ── Builder DSL helpers (public so inline funcs above can use them) ───────────

/** Append query [params] to the request URL. */
fun HttpRequestBuilder.applyQuery(params: Map<String, String>) {
    if (params.isEmpty()) return
    url { params.forEach { (k, v) -> parameters.append(k, v) } }
}

/** Apply per-call [headers] (blank values skipped). */
fun HttpRequestBuilder.applyHeaders(headers: Map<String, String>) {
    headers.forEach { (k, v) -> if (v.isNotBlank()) header(k, v) }
}
