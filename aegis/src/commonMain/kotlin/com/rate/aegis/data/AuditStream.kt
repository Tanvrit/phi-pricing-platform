package com.rate.aegis.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Live audit-event stream factory.
 *
 * Returns a [Flow] of newly-recorded audit events when the platform can support
 * a Server-Sent-Events connection to `GET /api/audit/stream`, or `null` when it
 * can't — in which case callers stay on polling against `/api/audit/events`.
 *
 * Platform actuals:
 *  - **WASM**: opens a browser-native `EventSource`. Events are pushed into a
 *    JS-side global queue and drained by a Kotlin coroutine every ~250 ms. We
 *    can't pass a Kotlin lambda across the `js(...)` boundary as a callback in
 *    Kotlin/Wasm (closures don't survive that bridge), so the
 *    drain-from-shared-memory bridge is the simplest thing that works.
 *  - **JVM**: returns `null`. Wiring `ktor-client-sse` would require a
 *    transitive Ktor minor-version bump; the polling path on the desktop
 *    operator binary is fine at 5–30 s for now.
 *
 * Surfaces that consume this MUST keep their polling [androidx.compose.runtime.LaunchedEffect]
 * intact — it acts as both the JVM path and the recovery path on transient SSE
 * disconnect (browser tab backgrounding, proxy timeout, server restart).
 */
expect fun openAuditStream(baseUrl: String): Flow<AuditEventDto>?

/**
 * Wire-format mirror of the server's `AuditEventRow`. Kept inside `:aegis/data`
 * (rather than reusing the one in `ApiClient.kt`) so it can be `@Serializable`
 * without dragging the HTTP client into platform-specific source sets.
 *
 * Field names are byte-identical to the server payload; deserialisation is by
 * name. Optional fields default to null so older payloads still hydrate.
 */
@Serializable
data class AuditEventDto(
    val id: Long,
    val eventAt: String,
    val action: String,
    val resourceType: String,
    val resourceId: String? = null,
    val actorSubject: String? = null,
    val actorRole: String? = null,
    val requestId: String? = null,
    val payloadJson: String? = null,
    val prevHash: String? = null,
    val thisHash: String,
)
