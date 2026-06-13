package com.rate.core.auth.session

import kotlinx.datetime.Instant

/**
 * PORT for session persistence with TTL semantics. The actual lives in the server app
 * (in-memory now, Mongo with a TTL index later). The store is responsible for not
 * returning expired sessions from [get]/[findByRefreshHash] (it may lazily filter or
 * rely on a TTL index) and for [purgeExpired] housekeeping.
 *
 * Relocated from the server's `BuyOnlineSessionRepository` save/load + the OTP token's
 * implicit session lifetime: hoisted to a clean TTL'd port so refresh + revocation
 * have a real home.
 */
interface SessionStore {

    /** Create or replace a session by its [SessionRecord.id]. */
    suspend fun put(record: SessionRecord)

    /** Active (non-revoked, non-expired) session for [id], or null. */
    suspend fun get(id: String): SessionRecord?

    /** Resolve a refresh handle (by its SHA-256 hash) to its active session, or null. */
    suspend fun findByRefreshHash(refreshTokenHash: String): SessionRecord?

    /** Persist a mutation (e.g. set [SessionRecord.revoked] or extend [SessionRecord.expiresAt]). */
    suspend fun update(record: SessionRecord)

    /** Mark a single session revoked (immediate logout). Returns true if it existed. */
    suspend fun revoke(id: String): Boolean

    /** Revoke every active session for a subject (force-logout-everywhere). Returns count revoked. */
    suspend fun revokeAllForSubject(subject: String): Int

    /** Drop sessions whose [SessionRecord.expiresAt] is at/before [now]. Returns count removed. */
    suspend fun purgeExpired(now: Instant): Int
}
