package com.rate.server

import com.rate.domain.engine.PricingEngine
import com.rate.server.audit.AuditEventService
import com.rate.server.database.DatabaseFactory
import com.rate.server.database.repositories.RateDataProviderImpl
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

    // Background cleanup of expired idempotency entries (24h TTL).
    val backgroundScope = CoroutineScope(SupervisorJob())
    idempotencyService.startCleanup(backgroundScope)

    configureRouting(rateDataProvider, pricingEngine, otpService, auditService, idempotencyService)

    // Graceful shutdown: stop background jobs + close DB pool when the JVM is asked to stop.
    Runtime.getRuntime().addShutdownHook(Thread {
        environment.log.info("Shutdown hook: draining connections.")
        backgroundScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    })
}
