package com.rate.server.plugins

import com.rate.domain.engine.PricingEngine
import com.rate.domain.repository.RateDataProvider
import com.rate.server.audit.AuditEventService
import com.rate.server.database.DatabaseFactory
import com.rate.server.database.repositories.*
import com.rate.server.metrics.Metrics
import com.rate.server.routes.*
import com.rate.server.security.IdempotencyService
import com.rate.server.security.OtpService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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
    idempotencyService: IdempotencyService
) {
    val quoteRepo = QuoteRepositoryImpl()
    val planRepo  = PlanRepositoryImpl()

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

        // ── Audit chain verification (Phase 2: gate behind auditor role later) ─
        get("/api/audit/verify") {
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
            call.respond(auditService.listEvents(limit))
        }

        quoteRoutes(rateDataProvider, quoteRepo, auditService, idempotencyService)
        planRoutes(planRepo, auditService)
        prospectusRoutes(planRepo)
        coverRoutes()
        discountRoutes(rateDataProvider)
        importRoutes(auditService, idempotencyService)
        buyOnlineRoutes(otpService, pricingEngine, auditService, idempotencyService)
    }
}
