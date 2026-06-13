package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.time.Now
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.persistence.codec.BsonCodec
import com.rate.sdk.proposal.model.BuyOnlineSessionState
import com.rate.sdk.proposal.repository.SessionRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.bson.BsonDateTime
import org.bson.BsonString

/**
 * Mongo actual for [SessionRepository] — buy-online save+resume snapshots with TTL.
 *
 * The session id IS the resume bearer ([BuyOnlineSessionState.sessionId], mirrored to `_id`).
 * On every [save] the repo stamps a `_ttlAt` BSON-Date field = now; a TTL index on `_ttlAt`
 * ([com.rate.persistence.base.IndexBootstrap.SESSION_TTL_SECONDS] = 30 days) lets the database
 * expire an abandoned resume link 30 days after its last write — so [load] naturally returns
 * null once the window elapses (the document is gone). A dedicated BSON-Date field is required
 * because the entity's own Instant fields serialize as ISO strings (which TTL indexes ignore).
 *
 * Relocated from the monolith's `BuyOnlineSessionRepository` (save/load/listSessions) with the
 * 30-day expiry made a real, server-enforced TTL rather than an app convention.
 */
class SessionRepositoryImpl(db: MongoDatabase) :
    MongoRepository<BuyOnlineSessionState>(
        db, CollectionNames.SESSIONS, BuyOnlineSessionState::class.java,
    ),
    SessionRepository {

    override suspend fun save(state: BuyOnlineSessionState) {
        // Encode + stamp the envelope (createdAt-preserving) AND the TTL anchor.
        val incoming = toBson(state)
        val id = incoming.get(BsonCodec.ID)
        val existing = if (id != null) {
            raw.find(Filters.eq(BsonCodec.ID, id)).limit(1).firstOrNull()
        } else {
            null
        }
        val now = Now.instant().toEpochMilliseconds()
        val doc = incoming.clone()
        doc["updatedAt"] = BsonString(Now.instant().toString())
        doc["_ttlAt"] = BsonDateTime(now)
        existing?.get("createdAt")?.let { doc["createdAt"] = it }
        if (existing != null) {
            val storedV = existing.get("v")?.asNumber()?.longValue() ?: 0L
            doc["v"] = org.bson.BsonInt64(storedV + 1L)
        }
        raw.replaceOne(Filters.eq(BsonCodec.ID, id), doc, ReplaceOptions().upsert(true))
    }

    override suspend fun load(sessionId: String): BuyOnlineSessionState? = findById(sessionId)

    override suspend fun listSessions(limit: Int): List<BuyOnlineSessionState> =
        collection.find(notDeleted)
            .sort(Sorts.descending("updatedAt"))
            .limit(limit.coerceIn(1, 5000))
            .toList()

    override suspend fun purgeExpired(olderThan: Instant): Int {
        // Belt-and-braces alongside the TTL index — delete snapshots last touched before the
        // cutoff. `updatedAt` is an ISO-8601 string; UTC ISO strings sort chronologically.
        val result = raw.deleteMany(Filters.lt("updatedAt", olderThan.toString()))
        return result.deletedCount.toInt()
    }
}
