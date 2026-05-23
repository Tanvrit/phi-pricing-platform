package com.rate.server.database.repositories

import com.rate.domain.model.BuyOnlineSessionState
import com.rate.server.database.tables.BuyOnlineSessionTable
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

/**
 * Server-internal projection of a persisted buyonline session row, carrying both
 * the deserialised [BuyOnlineSessionState] and the DB-side [createdAt]/[updatedAt]
 * ISO-8601 timestamps. We keep this DTO server-only (not in :shared) because the
 * client never needs the raw state — the route layer redacts to the wire DTO
 * before responding. Timestamps are ISO strings rather than `Instant` so the
 * route layer can drop them straight into the redacted DTO with no extra
 * formatting.
 */
data class SessionWithTimestamps(
    val state: BuyOnlineSessionState,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Persists [BuyOnlineSessionState] for the customer save+resume flow. The client
 * holds the session id (passed via `?session=` URL parameter); the server only
 * stores the latest snapshot and returns it on demand. We upsert by trying an
 * `UPDATE` first and falling back to `INSERT` if zero rows were touched — this
 * keeps the code portable (no `ON CONFLICT` syntax variation between databases).
 */
class BuyOnlineSessionRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun save(state: BuyOnlineSessionState) {
        newSuspendedTransaction {
            val now = Clock.System.now()
            val payload = json.encodeToString(state)
            val updated = BuyOnlineSessionTable.update({ BuyOnlineSessionTable.sessionId eq state.sessionId }) {
                it[stateJson] = payload
                it[updatedAt] = now
            }
            if (updated == 0) {
                BuyOnlineSessionTable.insert {
                    it[sessionId] = state.sessionId
                    it[stateJson] = payload
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    suspend fun load(sessionId: String): BuyOnlineSessionState? =
        newSuspendedTransaction {
            BuyOnlineSessionTable.selectAll()
                .where { BuyOnlineSessionTable.sessionId eq sessionId }
                .firstOrNull()
                ?.let { json.decodeFromString<BuyOnlineSessionState>(it[BuyOnlineSessionTable.stateJson]) }
        }

    /**
     * Returns up to [limit] most-recently-updated session snapshots for operator
     * analytics (funnel + drop-off). The projection includes BOTH the DB-side
     * `created_at` and `updated_at` timestamps so callers can compute
     * time-to-completion stats — the embedded [BuyOnlineSessionState] only
     * carries the client's last-saved `updatedAtIso`, which can drift from the
     * server's view of "first seen" / "last touched".
     *
     * The recent-N window is fine for analytics scale today; if it stops being
     * so we'd add a `(updated_at)` index and a date-range filter.
     */
    suspend fun listSessions(limit: Int = 500): List<SessionWithTimestamps> =
        newSuspendedTransaction {
            BuyOnlineSessionTable
                .selectAll()
                .orderBy(BuyOnlineSessionTable.updatedAt, SortOrder.DESC)
                .limit(limit)
                .map {
                    SessionWithTimestamps(
                        state = json.decodeFromString<BuyOnlineSessionState>(it[BuyOnlineSessionTable.stateJson]),
                        createdAt = it[BuyOnlineSessionTable.createdAt].toString(),
                        updatedAt = it[BuyOnlineSessionTable.updatedAt].toString(),
                    )
                }
        }
}
