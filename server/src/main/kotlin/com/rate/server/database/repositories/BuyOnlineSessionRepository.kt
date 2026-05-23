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
     * analytics (funnel + drop-off). No filter / projection — callers aggregate
     * client-side. The recent-N window is fine for analytics scale today; if it
     * stops being so we'd add a `(updated_at)` index and a date-range filter.
     */
    suspend fun listSessions(limit: Int = 500): List<BuyOnlineSessionState> =
        newSuspendedTransaction {
            BuyOnlineSessionTable
                .selectAll()
                .orderBy(BuyOnlineSessionTable.updatedAt, SortOrder.DESC)
                .limit(limit)
                .map { json.decodeFromString<BuyOnlineSessionState>(it[BuyOnlineSessionTable.stateJson]) }
        }
}
