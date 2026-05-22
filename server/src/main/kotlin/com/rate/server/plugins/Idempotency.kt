package com.rate.server.plugins

import com.rate.server.security.IdempotencyOutcome
import com.rate.server.security.IdempotencyService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable

@Serializable
internal data class IdempotencyConflictResponse(
    val errorCode: String = "IDEMPOTENCY_CONFLICT",
    val message: String = "Idempotency-Key was previously used with a different request body."
)

/**
 * Wraps a POST handler with Idempotency-Key replay semantics.
 *
 *   - No header → run [handler] as normal.
 *   - Header + new key → run [handler], capture (status, body), store, respond.
 *   - Header + seen key + same body → replay the cached response.
 *   - Header + seen key + different body → respond 409.
 *
 * `routeKey` is what we partition the cache by (e.g. `POST /api/quotes`).
 * `bodyForHashing` is the raw request body, used both to compute the request hash
 * and (typically) passed back to the inner handler.
 *
 * The handler runs against a wrapped response we capture without preventing Ktor
 * from sending it — to keep things dependency-light we re-read the body once.
 */
suspend fun RoutingContext.withIdempotency(
    service: IdempotencyService,
    routeKey: String,
    handler: suspend (rawBody: String) -> Pair<HttpStatusCode, String>
) {
    val key = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
    val raw = call.receiveText()

    if (key == null) {
        // No idempotency requested — execute and respond normally.
        val (status, body) = handler(raw)
        call.respondText(body, contentType = ContentType.Application.Json, status = status)
        return
    }

    val requestHash = IdempotencyService.hashRequest(raw)
    when (val outcome = service.check(key, routeKey, requestHash)) {
        is IdempotencyOutcome.Replay -> {
            call.response.header("Idempotency-Replayed", "true")
            call.respondText(
                outcome.hit.body ?: "",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.fromValue(outcome.hit.status)
            )
        }
        IdempotencyOutcome.Conflict -> {
            call.respond(HttpStatusCode.Conflict, IdempotencyConflictResponse())
        }
        IdempotencyOutcome.Fresh -> {
            val (status, body) = handler(raw)
            service.store(key, routeKey, requestHash, status.value, body)
            call.respondText(body, contentType = ContentType.Application.Json, status = status)
        }
    }
}
