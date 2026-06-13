package com.rate.server.plugins

import com.rate.core.base.time.Now
import com.rate.server.metrics.Metrics
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.sdk.audit.model.IdempotencyRecord
import com.rate.sdk.audit.repository.IdempotencyStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Serializable
internal data class IdempotencyConflictResponse(
    val errorCode: String = "IDEMPOTENCY_CONFLICT",
    val message: String = "Idempotency-Key was previously used with a different request body.",
)

/**
 * Wraps a POST handler with Idempotency-Key replay semantics over the sdk-audit [IdempotencyStore]
 * PORT (Mongo `_id`-collision = "seen before", TTL index = eviction). RELOCATED from the monolith's
 * `withIdempotency` (Postgres-backed `IdempotencyService`) — same four-way behaviour:
 *
 *   - No header → run [handler] as normal (no caching).
 *   - Header + new key → run [handler], capture (status, body), store, respond.
 *   - Header + seen key + same body → replay the cached response.
 *   - Header + seen key + different body → 409.
 *
 * [scope] partitions the cache by route (e.g. `POST /api/quotes`). The body hash is keyed via the
 * app secret ([HmacIdempotencyHasher]) so a caller cannot precompute it. [handler] receives the raw
 * body so it re-uses the already-read text (Ktor's request body is single-shot).
 */
suspend fun RoutingContext.withIdempotency(
    store: IdempotencyStore,
    hasher: HmacIdempotencyHasher,
    scope: String,
    ttl: Duration = 24.hours,
    handler: suspend (rawBody: String) -> Pair<HttpStatusCode, String>,
) {
    val key = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
    val raw = call.receiveText()

    if (key == null) {
        val (status, body) = handler(raw)
        call.respondText(body, contentType = ContentType.Application.Json, status = status)
        return
    }

    val requestHash = hasher.hash(raw)
    val reservation = IdempotencyRecord.forResult(
        key = key,
        scope = scope,
        requestHash = requestHash,
        responseJson = "{}",
        statusCode = 0,
        expiresAt = Now.instant() + ttl,
    )

    when (val existing = store.putIfAbsent(reservation)) {
        null -> {
            // We won the reservation — run the handler then persist its response so a retry with
            // the same key replays this exact outcome. If the handler throws, the reservation is
            // dropped so the operation can be re-attempted cleanly (we do NOT cache a 5xx).
            Metrics.recordIdempotentNew()
            val (status, body) = try {
                handler(raw)
            } catch (t: Throwable) {
                runCatching { store.complete(key, "{}", 0) } // best-effort; TTL will evict the stub
                throw t
            }
            store.complete(key, body, status.value)
            call.respondText(body, contentType = ContentType.Application.Json, status = status)
        }
        else -> {
            if (existing.scope != scope || existing.requestHash != requestHash) {
                Metrics.recordIdempotentConflict()
                call.respond(HttpStatusCode.Conflict, IdempotencyConflictResponse())
            } else {
                Metrics.recordIdempotentReplay()
                call.response.header("Idempotency-Replayed", "true")
                val status = if (existing.statusCode in 100..599) {
                    HttpStatusCode.fromValue(existing.statusCode)
                } else {
                    HttpStatusCode.OK
                }
                call.respondText(existing.responseJson, contentType = ContentType.Application.Json, status = status)
            }
        }
    }
}
