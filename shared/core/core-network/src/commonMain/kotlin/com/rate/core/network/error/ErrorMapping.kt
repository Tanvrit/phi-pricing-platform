package com.rate.core.network.error

import com.rate.core.base.error.DomainError
import com.rate.core.base.json.AppJson
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Translates the wire world into our error types:
 *  - a non-2xx [HttpResponse]  → [NetworkError] (status-aware)
 *  - a thrown engine exception → [NetworkError] (timeout/connectivity/TLS/…)
 *  - any [NetworkError]        → core-base [DomainError] (so handlers stay uniform)
 *
 * Body parsing is best-effort and defensive: the server's error envelope
 * (`{errorCode, scope, identity, errors:[...], message}`) is mined for useful
 * fields when present, but a plain-text or empty body never breaks the mapping.
 */
object ErrorMapping {

    /**
     * Build a [NetworkError] from a non-success response. Reads the body once
     * (caller must not have consumed it). Safe to call only when
     * `response.status` is NOT success.
     */
    suspend fun fromResponse(response: HttpResponse): NetworkError {
        if (response.status.isSuccess()) {
            return NetworkError.Unknown("fromResponse called on a 2xx response")
        }
        val status = response.status.value
        val statusText = response.status.description
        val body = runCatching { response.bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() }
        return fromStatus(status, statusText, body)
    }

    /** Status-aware mapping without an [HttpResponse] in hand (e.g. from a cached error). */
    fun fromStatus(status: Int, statusText: String = "", body: String? = null): NetworkError {
        val scope = extractField(body, "scope")
        val message = extractField(body, "message")
            ?: extractField(body, "error")
            ?: statusText.ifBlank { "HTTP $status" }
        return when (status) {
            401 -> NetworkError.Unauthorized(message, body)
            403 -> NetworkError.Forbidden(message, scope, body)
            404 -> NetworkError.NotFound(message, body)
            409 -> NetworkError.Conflict(message, body)
            400, 422 -> NetworkError.Validation(message, extractErrors(body), body)
            else -> NetworkError.Http(status, statusText, body)
        }
    }

    /**
     * Map an exception thrown by the engine / plugins to a [NetworkError].
     * A [NetworkException] passes its wrapped error straight through.
     */
    fun fromThrowable(t: Throwable): NetworkError = when (t) {
        is NetworkException -> t.networkError
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException -> NetworkError.Timeout(t.message ?: "Request timed out", t)
        else -> {
            val msg = t.message.orEmpty().lowercase()
            when {
                msg.contains("ssl") || msg.contains("tls") ||
                    msg.contains("certificate") || msg.contains("pin") ->
                    NetworkError.Tls(t.message ?: "TLS error", t)
                msg.contains("timeout") || msg.contains("timed out") ->
                    NetworkError.Timeout(t.message ?: "Request timed out", t)
                msg.contains("connect") || msg.contains("unreachable") ||
                    msg.contains("refused") || msg.contains("reset") ||
                    msg.contains("host") || msg.contains("network") ->
                    NetworkError.Connectivity(t.message ?: "Network unreachable", t)
                msg.contains("serializ") || msg.contains("deserial") ||
                    msg.contains("json") || msg.contains("decode") ->
                    NetworkError.Serialization(t.message ?: "Failed to decode response", t)
                else -> NetworkError.Unknown(t.message ?: "Unexpected network error", t)
            }
        }
    }

    /**
     * Bridge to core-base. Lets callers that already pattern-match on
     * [DomainError] treat transport failures uniformly. Connectivity/timeout/TLS
     * collapse to [DomainError.Internal] (a retryable infra fault); status errors
     * map to their semantic DomainError twin.
     */
    fun toDomainError(error: NetworkError): DomainError = when (error) {
        is NetworkError.Unauthorized -> DomainError.Unauthorized(error.message)
        is NetworkError.Forbidden -> DomainError.Forbidden(error.message)
        is NetworkError.NotFound -> DomainError.NotFound("resource", error.body ?: "")
        is NetworkError.Conflict -> DomainError.Conflict(error.message)
        is NetworkError.Validation ->
            DomainError.Validation(error.errors.ifEmpty { listOf(error.message) })
        is NetworkError.Http -> DomainError.Internal(error.message, error.body)
        is NetworkError.Connectivity -> DomainError.Internal(error.message, error.cause?.message)
        is NetworkError.Timeout -> DomainError.Internal(error.message, error.cause?.message)
        is NetworkError.Tls -> DomainError.Internal(error.message, error.cause?.message)
        is NetworkError.Serialization -> DomainError.Internal(error.message, error.cause?.message)
        is NetworkError.Unknown -> DomainError.Internal(error.message, error.cause?.message)
    }

    // ── body mining (best-effort, never throws) ──────────────────────────────

    private fun extractField(body: String?, key: String): String? {
        val b = body?.trim() ?: return null
        if (!b.startsWith("{")) return null
        return runCatching {
            AppJson.json.parseToJsonElement(b).jsonObject[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun extractErrors(body: String?): List<String> {
        val b = body?.trim() ?: return emptyList()
        if (!b.startsWith("{")) return emptyList()
        return runCatching {
            AppJson.json.parseToJsonElement(b).jsonObject["errors"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
        }.getOrElse { emptyList() }
    }
}
