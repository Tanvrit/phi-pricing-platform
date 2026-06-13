package com.rate.server.routes

import com.rate.core.auth.rbac.Scope
import com.rate.core.base.json.AppJson
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.requireScope
import com.rate.sdk.audit.model.AuditEvent
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.collect

/**
 * Audit read + verify + live-stream endpoints. RELOCATED from the monolith's inline routes in
 * `Routing.kt`, now reading the pure sdk-audit chain via [ServerAuditService]. The hand-rolled SSE
 * wire format is preserved (no extra Ktor SSE artifact) and the chain-verify is gated by
 * `audit.read`.
 *
 * Paths kept backward-compatible:
 *   GET /api/audit/events   — newest-first list (optionally ?actor=)
 *   GET /api/audit/verify   — chain integrity walk (?fromSeq=&toSeq=)
 *   GET /api/audit/stream   — SSE live feed
 */
fun Route.auditRoutes(audit: ServerAuditService) {
    val json = AppJson.json

    get("/api/audit/events") {
        if (!requireScope(Scope.AUDIT_READ)) return@get
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
        val actor = call.request.queryParameters["actor"]?.takeIf { it.isNotBlank() }
        call.respond(audit.listEvents(limit = limit, actor = actor))
    }

    get("/api/audit/verify") {
        if (!requireScope(Scope.AUDIT_READ)) return@get
        val from = call.request.queryParameters["fromSeq"]?.toLongOrNull()
        val to = call.request.queryParameters["toSeq"]?.toLongOrNull()
        call.respond(audit.verifyChain(from, to))
    }

    // ── SSE live stream ──────────────────────────────────────────────────────
    // Subscribers get every event persisted AFTER they connect; they bootstrap history via
    // /api/audit/events first. Hand-rolled `data: <json>\n\n` framing avoids a Ktor SSE artifact.
    get("/api/audit/stream") {
        if (!requireScope(Scope.AUDIT_READ)) return@get
        val eventStream = ContentType("text", "event-stream")
        call.response.headers.append("Cache-Control", "no-cache, no-transform")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondBytesWriter(contentType = eventStream) {
            writeStringUtf8(": connected\n\n")
            flush()
            try {
                audit.stream().collect { event: AuditEvent ->
                    val payload = json.encodeToString(AuditEvent.serializer(), event)
                    val frame = buildString {
                        append("id: ").append(event.seq).append('\n')
                        append("event: audit\n")
                        append("data: ").append(payload).append("\n\n")
                    }
                    writeStringUtf8(frame)
                    flush()
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlin.coroutines.cancellation.CancellationException("client disconnected")
            }
        }
    }
}
