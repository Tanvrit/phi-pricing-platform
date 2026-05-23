package com.rate.server.security

import com.rate.server.database.tables.IdempotencyKeyTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours

/** A previously cached response keyed by Idempotency-Key, returned verbatim on replay. */
data class IdempotencyHit(
    val status: Int,
    val body: String?
)

/**
 * Wire-format row for `GET /api/audit/idempotency`. Mirrors the columns of
 * [com.rate.server.database.tables.IdempotencyKeyTable]; `expiresAt` is derived
 * from `createdAt + TTL` since the table only stores the creation instant.
 * `firstResponseBytes` is the byte length of the cached body (0 if null) and
 * exists purely as a debugging hint — operators can spot pathologically large
 * cached payloads without having to dump the body itself.
 */
@Serializable
data class IdempotencyEntry(
    val key: String,
    val routeKey: String,
    val requestHash: String,
    val responseStatus: Int,
    val createdAt: String,
    val expiresAt: String,
    val firstResponseBytes: Int
)

/** Outcome of a `check()` call. */
sealed class IdempotencyOutcome {
    /** Key not seen — caller should process normally and then `store(...)`. */
    object Fresh : IdempotencyOutcome()
    /** Key seen, same request body — replay the cached response. */
    data class Replay(val hit: IdempotencyHit) : IdempotencyOutcome()
    /** Key seen but with a *different* request body — reject (409). */
    object Conflict : IdempotencyOutcome()
}

/**
 * Postgres-backed idempotency cache with a 24h TTL.
 *
 * A caller flow looks like:
 *   1. Read `Idempotency-Key` header. If absent → process normally.
 *   2. Compute `requestHash = sha256(canonical-body)`.
 *   3. `check(key, route, requestHash)`:
 *        - Fresh    → run the handler, then `store(...)`.
 *        - Replay   → respond with the cached status+body.
 *        - Conflict → 409 ("key reused with different body").
 *   4. A background coroutine purges entries older than 24h.
 */
class IdempotencyService {

    private val log = LoggerFactory.getLogger(IdempotencyService::class.java)

    suspend fun check(key: String, route: String, requestHash: String): IdempotencyOutcome =
        newSuspendedTransaction {
            val row = IdempotencyKeyTable
                .selectAll()
                .where { IdempotencyKeyTable.key eq key }
                .firstOrNull() ?: return@newSuspendedTransaction IdempotencyOutcome.Fresh

            val storedHash = row[IdempotencyKeyTable.requestHash]
            val storedRoute = row[IdempotencyKeyTable.route]
            if (storedRoute != route || storedHash != requestHash) {
                IdempotencyOutcome.Conflict
            } else {
                IdempotencyOutcome.Replay(
                    IdempotencyHit(
                        status = row[IdempotencyKeyTable.responseStatus],
                        body   = row[IdempotencyKeyTable.responseBody]
                    )
                )
            }
        }

    suspend fun store(
        key: String,
        route: String,
        requestHash: String,
        status: Int,
        body: String?
    ) {
        try {
            newSuspendedTransaction {
                IdempotencyKeyTable.insert {
                    it[IdempotencyKeyTable.key]            = key
                    it[IdempotencyKeyTable.route]          = route
                    it[IdempotencyKeyTable.requestHash]    = requestHash
                    it[IdempotencyKeyTable.responseStatus] = status
                    it[IdempotencyKeyTable.responseBody]   = body
                    it[IdempotencyKeyTable.createdAt]      = Clock.System.now()
                }
            }
        } catch (t: Throwable) {
            // Common cause: concurrent inserts on the same key. Safe to ignore — the
            // first writer wins, the second will replay on its next attempt.
            log.debug("idempotency.store skipped for key={} route={}: {}", key, route, t.message)
        }
    }

    /**
     * Newest-first snapshot of the idempotency cache for the audit/diagnostic
     * surface. Read-only — does NOT include `responseBody` (which can be large
     * and may carry response PII); operators get a size hint instead. The
     * caller is responsible for clamping [limit]; this method enforces a hard
     * upper bound regardless to avoid pulling unbounded payloads on a typo.
     */
    suspend fun listRecent(limit: Int = 100): List<IdempotencyEntry> =
        newSuspendedTransaction {
            val capped = limit.coerceIn(1, 500)
            IdempotencyKeyTable
                .selectAll()
                .orderBy(IdempotencyKeyTable.createdAt, SortOrder.DESC)
                .limit(capped)
                .map { row ->
                    val created = row[IdempotencyKeyTable.createdAt]
                    val body = row[IdempotencyKeyTable.responseBody]
                    IdempotencyEntry(
                        key                = row[IdempotencyKeyTable.key],
                        routeKey           = row[IdempotencyKeyTable.route],
                        requestHash        = row[IdempotencyKeyTable.requestHash],
                        responseStatus     = row[IdempotencyKeyTable.responseStatus],
                        createdAt          = created.toString(),
                        expiresAt          = created.plus(TTL).toString(),
                        firstResponseBytes = body?.toByteArray(Charsets.UTF_8)?.size ?: 0
                    )
                }
        }

    /** Background cleanup loop — purges entries older than the TTL every hour. */
    fun startCleanup(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                runCatching {
                    val cutoff = Clock.System.now().minus(TTL)
                    newSuspendedTransaction {
                        val deleted = IdempotencyKeyTable.deleteWhere {
                            createdAt less cutoff
                        }
                        if (deleted > 0) log.info("idempotency.cleanup purged $deleted entries")
                    }
                }.onFailure { log.warn("idempotency.cleanup failed: ${it.message}") }
                delay(1.hours)
            }
        }
    }

    companion object {
        val TTL = 24.hours

        /** Stable SHA-256 of the request body, used to spot key-reuse-with-different-body. */
        fun hashRequest(body: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(body.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
