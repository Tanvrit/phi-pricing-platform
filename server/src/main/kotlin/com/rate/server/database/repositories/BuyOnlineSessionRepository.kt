package com.rate.server.database.repositories

import com.rate.domain.model.BuyOnlineSessionState
import com.rate.server.database.tables.BuyOnlineSessionTable
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
}
