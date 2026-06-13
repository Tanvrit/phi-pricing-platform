package com.rate.server.security

import com.rate.core.auth.session.SessionRecord
import com.rate.core.auth.session.SessionStore
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process [SessionStore] actual with TTL semantics. The contract was relocated to core-auth
 * from the monolith's implicit "OTP-token lifetime" + `BuyOnlineSessionRepository`; this binding
 * gives refresh + revocation a real (single-instance) home. A multi-instance deployment swaps in
 * the Mongo `sessions`-backed actual (TTL index on `expiresAt`) behind the same PORT.
 */
class InMemorySessionStore : SessionStore {

    private val byId = ConcurrentHashMap<String, SessionRecord>()
    private val byRefreshHash = ConcurrentHashMap<String, String>() // refreshHash -> sessionId

    override suspend fun put(record: SessionRecord) {
        byId[record.id] = record
        record.refreshTokenHash?.let { byRefreshHash[it] = record.id }
    }

    override suspend fun get(id: String): SessionRecord? =
        byId[id]?.takeIf { it.isActive() }

    override suspend fun findByRefreshHash(refreshTokenHash: String): SessionRecord? {
        val id = byRefreshHash[refreshTokenHash] ?: return null
        return byId[id]?.takeIf { it.isActive() }
    }

    override suspend fun update(record: SessionRecord) {
        byId[record.id] = record
        record.refreshTokenHash?.let { byRefreshHash[it] = record.id }
    }

    override suspend fun revoke(id: String): Boolean {
        val existing = byId[id] ?: return false
        byId[id] = existing.copy(revoked = true)
        return true
    }

    override suspend fun revokeAllForSubject(subject: String): Int {
        var count = 0
        byId.values.filter { it.subject == subject && !it.revoked }.forEach {
            byId[it.id] = it.copy(revoked = true)
            count++
        }
        return count
    }

    override suspend fun purgeExpired(now: Instant): Int {
        val expired = byId.values.filter { it.isExpired(now) }
        expired.forEach { rec ->
            byId.remove(rec.id)
            rec.refreshTokenHash?.let { byRefreshHash.remove(it) }
        }
        return expired.size
    }
}
