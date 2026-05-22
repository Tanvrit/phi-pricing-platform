package com.rate.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable

/**
 * Stable error envelope returned to API clients. Stack traces NEVER appear in this
 * payload — they're only in server logs. Clients can branch on `errorCode`.
 */
@Serializable
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val requestId: String? = null
)

fun Application.configureHTTP() {
    val allowedOrigins = environment.config
        .propertyOrNull("security.corsAllowedOrigins")
        ?.getString()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("http://localhost:9090")

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
        // Replace the prior `anyHost()` with an explicit allowlist.
        // Production must override this via the CORS_ALLOWED_ORIGINS env var.
        allowedOrigins.forEach { origin ->
            val (scheme, hostPort) = splitOrigin(origin)
            val (host, port) = splitHostPort(hostPort)
            if (port != null) allowHost("$host:$port", schemes = listOf(scheme))
            else allowHost(host, schemes = listOf(scheme))
        }
    }

    // Security headers applied to every response. Adds defense-in-depth against
    // clickjacking, MIME sniffing, mixed content, and referrer leakage.
    intercept(ApplicationCallPipeline.Plugins) {
        val h = call.response.headers
        h.appendIfAbsent("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        h.appendIfAbsent("X-Content-Type-Options", "nosniff")
        h.appendIfAbsent("X-Frame-Options", "DENY")
        h.appendIfAbsent("Referrer-Policy", "no-referrer")
        h.appendIfAbsent("Permissions-Policy", "geolocation=(), camera=(), microphone=()")
        // Cache by default: no PII routes will gain explicit cache headers later.
        h.appendIfAbsent("Cache-Control", "no-store")
        // Correlate logs with responses.
        val rid = call.request.headers["X-Request-Id"] ?: generateRequestId()
        h.appendIfAbsent("X-Request-Id", rid)
        call.attributes.put(REQUEST_ID_KEY, rid)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            call.application.environment.log.info("[$rid] 400 ${call.request.local.uri}: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", cause.message ?: "Bad request", rid)
            )
        }
        exception<NoSuchElementException> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            call.application.environment.log.info("[$rid] 404 ${call.request.local.uri}: ${cause.message}")
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NOT_FOUND", cause.message ?: "Not found", rid)
            )
        }
        exception<Throwable> { call, cause ->
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            // Full stack trace goes to the logs; never to the client.
            call.application.environment.log.error("[$rid] 500 ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Something went wrong on our end.", rid)
            )
        }
    }
}

/** Exposed for the request log plugin, audit service, and route handlers. */
val REQUEST_ID_KEY = AttributeKey<String>("requestId")
/** Populated by auth interceptors (Phase 2 JWT). For now this stays null. */
val ACTOR_SUBJECT_KEY = AttributeKey<String>("actorSubject")

private fun ResponseHeaders.appendIfAbsent(name: String, value: String) {
    if (this[name] == null) append(name, value, safeOnly = false)
}

private fun splitOrigin(origin: String): Pair<String, String> {
    val idx = origin.indexOf("://")
    return if (idx > 0) origin.substring(0, idx) to origin.substring(idx + 3)
    else "http" to origin
}

private fun splitHostPort(hp: String): Pair<String, Int?> {
    val idx = hp.indexOf(':')
    return if (idx > 0) hp.substring(0, idx) to hp.substring(idx + 1).toIntOrNull()
    else hp to null
}

private fun generateRequestId(): String =
    "req-${System.currentTimeMillis().toString(16)}-${(0..0xFFFF).random().toString(16).padStart(4, '0')}"
