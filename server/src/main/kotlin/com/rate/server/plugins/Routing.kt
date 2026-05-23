package com.rate.server.plugins

import com.rate.domain.engine.PricingEngine
import com.rate.domain.repository.RateDataProvider
import com.rate.server.audit.AuditEventRow
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.database.DatabaseFactory
import com.rate.server.database.repositories.*
import com.rate.server.email.EmailSender
import com.rate.server.email.FileSystemEmailSender
import com.rate.server.metrics.Metrics
import java.io.File
import com.rate.server.routes.*
import com.rate.server.security.IdempotencyService
import com.rate.server.security.OtpService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class HealthResponse(val status: String, val service: String, val check: String? = null)

@Serializable
internal data class AuditVerifyResponse(
    val ok: Boolean,
    val rowsChecked: Int,
    val breakAtId: Long? = null,
    val reason: String? = null
)

fun Application.configureRouting(
    rateDataProvider: RateDataProvider,
    pricingEngine: PricingEngine,
    otpService: OtpService,
    auditService: AuditEventService,
    idempotencyService: IdempotencyService,
    emailSender: EmailSender
) {
    val quoteRepo   = QuoteRepositoryImpl()
    val planRepo    = PlanRepositoryImpl()
    val sessionRepo = BuyOnlineSessionRepository()

    routing {
        // ── Liveness: cheap, no dependencies — answers "is the JVM up?" ──────
        get("/health") {
            call.respond(HealthResponse(status = "ok", service = "rate-calculator"))
        }
        get("/health/live") {
            call.respond(HealthResponse(status = "ok", service = "rate-calculator", check = "live"))
        }
        // ── Readiness: confirms DB connectivity. K8s uses this to gate traffic. ─
        get("/health/ready") {
            val ds = DatabaseFactory.dataSource
            if (ds == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(status = "down", service = "rate-calculator", check = "db: not-initialised")
                )
                return@get
            }
            try {
                ds.connection.use { conn ->
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT 1").use { rs ->
                            rs.next()
                        }
                    }
                }
                call.respond(HealthResponse(status = "ok", service = "rate-calculator", check = "db"))
            } catch (t: Throwable) {
                call.application.environment.log.warn("readiness probe failed: ${t.message}")
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(status = "down", service = "rate-calculator",
                        check = "db: ${t.message ?: "unreachable"}")
                )
            }
        }

        // ── Prometheus scrape target ─────────────────────────────────────────
        get("/metrics") {
            call.respondText(Metrics.render(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
        }

        // ── Audit chain verification (admin/auditor only) ────────────────────
        get("/api/audit/verify") {
            if (!requireScope("audit.verify")) return@get
            val from = call.request.queryParameters["fromId"]?.toLongOrNull()
            val to   = call.request.queryParameters["toId"]?.toLongOrNull()
            val r = auditService.verifyChain(from, to)
            call.respond(AuditVerifyResponse(r.ok, r.rowsChecked, r.breakAtId, r.reason))
        }

        // ── Audit log listing (newest-first; for Aegis AUDIT surface) ────────
        // Cap at 500 rows to avoid pulling unbounded payloads; default 100 keeps
        // the wire small for the common dashboard refresh case.
        get("/api/audit/events") {
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100)
                .coerceIn(1, 500)
            // Optional narrowing by actor subject — lets MyAuditEvents and
            // future role-scoped views ask the server for just one identity's
            // trail instead of pulling the global feed and filtering client-side.
            // Blank values are coerced to null so `?actor=` (empty) behaves
            // identically to omitting the param.
            val actor = call.request.queryParameters["actor"]?.takeIf { it.isNotBlank() }
            call.respond(auditService.listEvents(limit = limit, actor = actor))
        }

        // ── Server-Sent Events live stream of audit rows ─────────────────────
        // Subscribers get every row persisted AFTER they connect; they're
        // expected to bootstrap historical state via `GET /api/audit/events`
        // first, then attach here. The polling endpoint above stays as the
        // fallback for environments where SSE isn't available (proxies that
        // buffer, ancient browsers, etc.).
        //
        // We deliberately implement the SSE wire format by hand on top of
        // `respondBytesWriter` rather than pulling in `ktor-server-sse`:
        // adding that artifact would force a transitive Ktor minor-version
        // bump and the format is trivial (`data: <json>\n\n` + optional
        // `event:`/`id:` lines). The endpoint is fully testable via
        // `curl -N http://host:9090/api/audit/stream`.
        get("/api/audit/stream") {
            val eventStream = ContentType("text", "event-stream")
            // Disable proxy buffering; some reverse proxies (nginx, ALB) hold
            // a streaming response until N bytes accumulate, which would defeat
            // sub-second propagation. `X-Accel-Buffering: no` is the nginx
            // hint; Cache-Control reinforces it for general intermediaries.
            call.response.headers.append("Cache-Control", "no-cache, no-transform")
            call.response.headers.append("X-Accel-Buffering", "no")
            call.respondBytesWriter(contentType = eventStream) {
                // Initial comment line forces the response headers to flush and
                // tells the client "you're connected" without polluting the
                // event channel. Per the SSE spec lines starting with ':' are
                // comments and ignored by EventSource.
                writeStringUtf8(": connected\n\n")
                flush()
                try {
                    auditService.stream()
                        .onCompletion {
                            // Best-effort: close gracefully when the upstream
                            // flow ends. Channel close is handled by the
                            // `respondBytesWriter` machinery on return.
                        }
                        .collect { row: AuditEventRow ->
                            val payload = Json.encodeToString(AuditEventRow.serializer(), row)
                            // Wire format: `id:<n>\nevent:audit\ndata:<json>\n\n`.
                            // JSON is single-line (no embedded newlines from the
                            // serializer's default config), so we don't need to
                            // split `data:` across multiple lines.
                            val frame = buildString {
                                append("id: ").append(row.id).append('\n')
                                append("event: audit\n")
                                append("data: ").append(payload).append("\n\n")
                            }
                            writeStringUtf8(frame)
                            flush()
                        }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Client disconnected. Let the cancellation propagate so the
                    // response channel closes cleanly — not an error.
                    throw kotlin.coroutines.cancellation.CancellationException("client disconnected")
                }
            }
        }

        // ── Idempotency cache diagnostic (admin/auditor only) ────────────────
        // Read-only listing of recent idempotency-key entries so operators can
        // debug client retry behaviour without dropping to SQL. Gated by the
        // existing `audit.verify` scope — admin operators with chain-integrity
        // access already see this kind of diagnostic, so a separate scope would
        // just be noise. Cap mirrors `/api/audit/events`.
        get("/api/audit/idempotency") {
            if (!requireScope("audit.verify")) return@get
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100)
                .coerceIn(1, 500)
            call.respond(idempotencyService.listRecent(limit))
        }

        quoteRoutes(rateDataProvider, quoteRepo, auditService, idempotencyService)
        planRoutes(planRepo, auditService)
        prospectusRoutes(planRepo)
        coverRoutes()
        discountRoutes(rateDataProvider)
        importRoutes(auditService, idempotencyService)
        buyOnlineRoutes(otpService, pricingEngine, auditService, idempotencyService, sessionRepo, emailSender)
        operatorRoutes()

        // Admin diagnostics: list the Phase-1 filesystem outbox so operators
        // can verify the "Email me" flow without ssh'ing to the server. The
        // dir is sourced from the live `FileSystemEmailSender` so any operator
        // who instantiates the sender with a non-default path gets the right
        // listing for free; if we're running with a future non-filesystem
        // sender we fall back to the standard `~/.aegis/outbox` path so the
        // endpoint stays functional during the transition.
        val outboxDir: File = (emailSender as? FileSystemEmailSender)?.outboxDir
            ?: File(System.getProperty("user.home"), ".aegis/outbox")
        adminRoutes(outboxDir)
    }
}
