package com.rate.sdk.proposal.repository

import com.rate.sdk.proposal.model.BuyOnlineSessionState
import kotlinx.datetime.Instant

/**
 * Persistence PORT for buy-online save+resume sessions ([BuyOnlineSessionState]) with TTL
 * semantics. Relocated from the monolith's `BuyOnlineSessionRepository` (save/load/listSessions)
 * — hoisted to a clean TTL'd port so resume links expire (the monolith's resume link was "valid
 * for 30 days"; the store enforces that with a TTL index in the Mongo actual).
 *
 * The session id IS the resume bearer (sent via the `?session=` URL param; there is no auth on
 * these endpoints by design — mirrors how payment-aggregator resume links work). The store is
 * responsible for not returning expired sessions from [load] and for [purgeExpired] housekeeping.
 *
 * The MongoDB-backed actual (with a TTL index on the snapshot's updatedAt) lives in
 * `server-persistence`; this is a pure contract.
 */
interface SessionRepository {

    /** Upsert the snapshot by its [BuyOnlineSessionState.sessionId]. */
    suspend fun save(state: BuyOnlineSessionState)

    /** Latest non-expired snapshot for a session id, or null (also null once TTL has elapsed). */
    suspend fun load(sessionId: String): BuyOnlineSessionState?

    /**
     * Most recently updated sessions for the operator funnel/analytics (server-side redaction is
     * applied at the route layer, never here). Capped by [limit].
     */
    suspend fun listSessions(limit: Int = 500): List<BuyOnlineSessionState>

    /** Drop sessions whose snapshot is older than [olderThan]. Returns count removed. */
    suspend fun purgeExpired(olderThan: Instant): Int
}
