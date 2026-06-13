package com.rate.server

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.auth.di.authModule
import com.rate.persistence.base.MongoBootstrap
import com.rate.persistence.config.MongoConfig
import com.rate.persistence.di.persistenceModule
import com.rate.persistence.rating.RateTableCache
import com.rate.server.audit.ServerAuditService
import com.rate.server.di.serverModule
import com.rate.server.routes.seedOwnerUserIfEmpty
import com.rate.server.security.PasswordHasher
import com.rate.sdk.catalog.repository.UserRepository
import com.rate.server.plugins.configureHTTP
import com.rate.server.plugins.configureRequestLog
import com.rate.server.plugins.configureRouting
import com.rate.server.plugins.configureSerialization
import com.rate.server.security.AppSecrets
import com.rate.sdk.audit.di.auditModule
import com.rate.sdk.catalog.di.catalogModule
import com.rate.sdk.ingestion.di.ingestionModule
import com.rate.sdk.party.di.partyModule
import com.rate.sdk.policy.di.policyModule
import com.rate.sdk.proposal.di.proposalModule
import com.rate.sdk.quoting.di.quotingModule
import com.rate.sdk.rating.di.ratingModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin

/**
 * Server boot, captured at class init so `/api/admin/config` can report uptime.
 */
val startedAtIso: String = Clock.System.now().toString()

fun main(args: Array<String>): Unit = EngineMain.main(args)

/**
 * The single application module (referenced from `application.conf`). The re-arch's app shell:
 *
 *  1. read SECRETS + Mongo config from the environment (fail-fast — the ONLY place secrets live);
 *  2. `startKoin` over serverModule + persistenceModule + auth + EVERY sdk feature module — this
 *     binds every pure-KMP handler to its Mongo-backed PORT actual;
 *  3. run [MongoBootstrap] (idempotent index creation + load the ACTIVE rate-table snapshot into
 *     the in-RAM engine so the first quote is warm);
 *  4. install plugins (serialization via AppJson, HTTP+CORS+StatusPages, PII-masked request log);
 *  5. mount the routes (per-feature groups + the generic admin-CRUD factory);
 *  6. start background jobs (audit chain verifier) and register graceful shutdown.
 */
fun Application.module() {
    // ── 1. Secrets + Mongo config (fail-fast) ────────────────────────────────
    val devProfile = System.getenv(AppSecrets.ENV_DEV_PROFILE)?.equals("true", ignoreCase = true) ?: false
    val secrets = AppSecrets.fromEnv(requireSecrets = !devProfile)
    val mongoConfig = MongoConfig.fromEnv(requireUri = !devProfile)

    // ── 2. Koin: all sdk feature modules, THEN persistence, THEN app ─────────
    // Module ORDER matters: Koin 4.0 allows override (last definition wins). The sdk feature
    // modules (e.g. sdk-rating) register IN-PROCESS fallbacks for RateDataProvider /
    // GroupRateDataProvider so they compile/run offline; loading `persistenceModule` AFTER them
    // makes the Mongo-backed actuals win on the server. `serverModule` binds app-only types (no
    // conflicts) and goes last.
    val koinModules = listOf(
        authModule(),
        auditModule(),
        ratingModule,
        quotingModule(),
        catalogModule(),
        partyModule,
        proposalModule(),
        policyModule,
        ingestionModule(),
        persistenceModule(mongoConfig),
        serverModule(secrets),
    )
    install(Koin) { modules(koinModules) }

    // ── 3. Mongo bootstrap (indexes + warm rate-table snapshot) ──────────────
    val db: MongoDatabase = get()
    val rateCache: RateTableCache = get()
    runBlocking {
        val activeVersion = runCatching { MongoBootstrap.run(db, rateCache) }
            .getOrElse { t ->
                log.warn("MongoBootstrap incomplete (continuing): ${t.message}")
                "unloaded"
            }
        log.info("MongoBootstrap done: activeRateVersion={}", activeVersion)

        // Seed a single OWNER console user (owner@rate.local / "owner123") when none exist, so a
        // fresh deployment is immediately loginable. Idempotent: a no-op once any user exists.
        runCatching { seedOwnerUserIfEmpty(get<UserRepository>(), get<PasswordHasher>()) }
            .onFailure { log.warn("Owner-user seed skipped: ${it.message}") }
    }

    // ── 4. Plugins ───────────────────────────────────────────────────────────
    configureSerialization()
    configureHTTP(corsAllowedOrigins())
    configureRequestLog()

    // ── 5. Routes ─────────────────────────────────────────────────────────────
    configureRouting(startedAtIso)

    // ── 6. Background jobs + graceful shutdown ───────────────────────────────
    val backgroundScope = CoroutineScope(SupervisorJob())
    val audit: ServerAuditService = get()
    val verifyInterval = System.getenv("AUDIT_VERIFY_INTERVAL_HOURS")?.toIntOrNull() ?: 6
    audit.startPeriodicVerify(backgroundScope, intervalHours = verifyInterval)

    monitor.subscribe(io.ktor.server.application.ApplicationStopping) {
        log.info("Shutdown: cancelling background jobs.")
        backgroundScope.coroutineContext[Job]?.cancel()
    }
}

/** CORS allowlist — env override (CORS_ALLOWED_ORIGINS) else the conf default else localhost. */
private fun Application.corsAllowedOrigins(): List<String> =
    (System.getenv("CORS_ALLOWED_ORIGINS")
        ?: environment.config.propertyOrNull("security.corsAllowedOrigins")?.getString())
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("http://localhost:9090", "http://localhost:9092")
