package com.rate.server

import com.rate.domain.engine.PricingEngine
import com.rate.server.audit.AuditEventService
import com.rate.server.database.DatabaseFactory
import com.rate.server.database.repositories.RateDataProviderImpl
import com.rate.server.email.EmailSender
import com.rate.server.email.FileSystemEmailSender
import com.rate.server.metrics.Metrics
import com.rate.server.plugins.configureHTTP
import com.rate.server.plugins.configureRequestLog
import com.rate.server.plugins.configureRouting
import com.rate.server.plugins.configureSerialization
import com.rate.server.security.IdempotencyService
import com.rate.server.security.OtpService
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Captured once at JVM boot (class init), surfaced verbatim by
 * `/api/admin/config` so the Aegis "Server config" diagnostic card can
 * show how long this server process has been running. Top-level so the
 * routes package can import it directly without threading it through DI.
 */
val startedAt: Instant = Clock.System.now()

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    DatabaseFactory.dataSource?.let { Metrics.bindHikari(it) }

    configureSerialization()
    configureHTTP()
    // Request log + metrics. Installed after HTTP so X-Request-Id is already set.
    configureRequestLog()

    // Shared dependencies wired once and passed into routes (lightweight DI).
    val otpTokenSecret = environment.config.propertyOrNull("security.otpTokenSecret")?.getString()
        ?: error("OTP_TOKEN_SECRET env var is required.")
    val otpService = OtpService(otpTokenSecret)
    val rateDataProvider = RateDataProviderImpl()
    val pricingEngine = PricingEngine(rateDataProvider)
    val auditService = AuditEventService()
    val idempotencyService = IdempotencyService()
    // Phase-1 email: writes `.eml` files under ~/.aegis/outbox. Phase-2 will
    // swap in an SMTP/SES-backed implementation behind the same interface.
    val emailSender: EmailSender = FileSystemEmailSender()

    // Background cleanup of expired idempotency entries (24h TTL).
    val backgroundScope = CoroutineScope(SupervisorJob())
    idempotencyService.startCleanup(backgroundScope)

    // Periodic server-side audit-chain integrity walker. Writes its result
    // back into the chain so tamper / corruption shows up in the regular
    // audit feed (audit.chain_verified / audit.chain_broken) even when
    // nobody opens Aegis to click "Re-verify". Default 6h; override with
    // AUDIT_VERIFY_INTERVAL_HOURS for staging / soak tests.
    val verifyInterval = System.getenv("AUDIT_VERIFY_INTERVAL_HOURS")?.toIntOrNull() ?: 6
    auditService.startPeriodicVerify(backgroundScope, intervalHours = verifyInterval)

    configureRouting(rateDataProvider, pricingEngine, otpService, auditService, idempotencyService, emailSender)

    // Graceful shutdown: stop background jobs + close DB pool when the JVM is asked to stop.
    Runtime.getRuntime().addShutdownHook(Thread {
        environment.log.info("Shutdown hook: draining connections.")
        backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    })
}
