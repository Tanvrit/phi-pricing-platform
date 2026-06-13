package com.rate.server.plugins

import com.rate.core.base.error.DomainError
import com.rate.core.base.error.DomainException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.ResponseHeaders
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable

/**
 * Stable error envelope returned to API clients. Stack traces NEVER appear here — only in logs.
 * Clients branch on [errorCode].
 */
@Serializable
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val requestId: String? = null,
    val details: List<String> = emptyList(),
)

/** Request-id attribute, set by the request-id interceptor; read by logging + handlers. */
val REQUEST_ID_KEY = AttributeKey<String>("requestId")

/**
 * Best-effort actor subject for the access log, set by the request-id interceptor from the
 * `X-Aegis-Actor` header (cheap, no token verification on the hot path). Route handlers that need
 * the VERIFIED subject/role use [com.rate.server.auth.auditActor] instead, which decodes the bearer.
 */
val ACTOR_SUBJECT_KEY = AttributeKey<String>("actorSubject")

/**
 * HTTP cross-cutting concerns: CORS (env allowlist), security headers, request-id correlation, and
 * [StatusPages] error mapping. The big change from the monolith is that [DomainException] (the
 * core/sdk error type) is mapped to the right HTTP status here — handlers stay transport-agnostic
 * and just throw/`raise()` a [DomainError].
 *
 * CORS origins come from `CORS_ALLOWED_ORIGINS` (comma-separated) — NEVER `anyHost()` in prod.
 */
fun Application.configureHTTP(corsAllowedOrigins: List<String>) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Request-Id")
        allowHeader("X-Aegis-Actor")
        allowHeader("Idempotency-Key")
        exposeHeader("Idempotency-Replayed")
        val origins = corsAllowedOrigins.ifEmpty { listOf("http://localhost:9090") }
        origins.forEach { origin ->
            val (scheme, hostPort) = splitOrigin(origin)
            val (host, port) = splitHostPort(hostPort)
            if (port != null) allowHost("$host:$port", schemes = listOf(scheme))
            else allowHost(host, schemes = listOf(scheme))
        }
    }

    // Security headers + request-id correlation on every response.
    intercept(ApplicationCallPipeline.Plugins) {
        val h = call.response.headers
        h.appendIfAbsent("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        h.appendIfAbsent("X-Content-Type-Options", "nosniff")
        h.appendIfAbsent("X-Frame-Options", "DENY")
        h.appendIfAbsent("Referrer-Policy", "no-referrer")
        h.appendIfAbsent("Permissions-Policy", "geolocation=(), camera=(), microphone=()")
        h.appendIfAbsent("Cache-Control", "no-store")
        val rid = call.request.headers["X-Request-Id"] ?: generateRequestId()
        h.appendIfAbsent("X-Request-Id", rid)
        call.attributes.put(REQUEST_ID_KEY, rid)

        // Cheap actor breadcrumb for the access log (no token verification on the hot path).
        call.request.headers["X-Aegis-Actor"]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { call.attributes.put(ACTOR_SUBJECT_KEY, it) }
    }

    install(StatusPages) {
        // Domain errors from core/sdk handlers → mapped HTTP status + stable envelope.
        exception<DomainException> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val (status, code, details) = mapDomainError(cause.error)
            if (status.value >= 500) {
                call.application.environment.log.error("[$rid] ${status.value} ${call.request.uri}", cause)
            } else {
                call.application.environment.log.info("[$rid] ${status.value} ${call.request.uri}: ${cause.error.msg}")
            }
            call.respond(status, ErrorResponse(code, cause.error.msg, rid, details))
        }
        exception<IllegalArgumentException> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            call.application.environment.log.info("[$rid] 400 ${call.request.uri}: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Bad request", rid))
        }
        exception<NoSuchElementException> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            call.application.environment.log.info("[$rid] 404 ${call.request.uri}: ${cause.message}")
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", cause.message ?: "Not found", rid))
        }
        exception<Throwable> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            call.application.environment.log.error("[$rid] 500 ${call.request.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Something went wrong on our end.", rid))
        }
    }
}

/** Map a transport-agnostic [DomainError] to (HTTP status, error code, detail list). */
fun mapDomainError(error: DomainError): Triple<HttpStatusCode, String, List<String>> = when (error) {
    is DomainError.Validation -> Triple(HttpStatusCode.UnprocessableEntity, "VALIDATION_FAILED", error.errors)
    is DomainError.NotFound -> Triple(HttpStatusCode.NotFound, "NOT_FOUND", emptyList())
    is DomainError.Conflict -> Triple(HttpStatusCode.Conflict, "CONFLICT", emptyList())
    is DomainError.Unauthorized -> Triple(HttpStatusCode.Unauthorized, "UNAUTHORIZED", emptyList())
    is DomainError.Forbidden -> Triple(HttpStatusCode.Forbidden, "FORBIDDEN", emptyList())
    is DomainError.Internal -> Triple(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", emptyList())
}

private fun ResponseHeaders.appendIfAbsent(name: String, value: String) {
    if (this[name] == null) append(name, value, safeOnly = false)
}

private fun splitOrigin(origin: String): Pair<String, String> {
    val idx = origin.indexOf("://")
    return if (idx > 0) origin.substring(0, idx) to origin.substring(idx + 3) else "http" to origin
}

private fun splitHostPort(hp: String): Pair<String, Int?> {
    val idx = hp.indexOf(':')
    return if (idx > 0) hp.substring(0, idx) to hp.substring(idx + 1).toIntOrNull() else hp to null
}

private fun generateRequestId(): String =
    "req-${System.currentTimeMillis().toString(16)}-${(0..0xFFFF).random().toString(16).padStart(4, '0')}"
