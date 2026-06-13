package com.rate.persistence.repository

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.time.Now
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.persistence.codec.BsonCodec
import com.rate.sdk.audit.model.IdempotencyRecord
import com.rate.sdk.audit.repository.IdempotencyStore
import org.bson.BsonDateTime
import org.bson.BsonString

/**
 * Mongo actual for the once-only-execution [IdempotencyStore].
 *
 * The idempotency key IS the document `_id`, so a duplicate insert collides — that collision is
 * exactly the "already processed" signal. [putIfAbsent] races on insert:
 *  - insert wins → returns null (caller runs the real op, then [complete]s the response);
 *  - insert collides with a NON-expired record → returns it (caller replays the response);
 *  - insert collides with an EXPIRED record → overwrites it and wins (returns null).
 *
 * A `_ttlAt` BSON-Date field (= the record's `expiresAt`) is stamped on write so the TTL index
 * evicts the document at expiry (the entity's own `expiresAt` is an ISO string, which TTL
 * indexes ignore).
 */
class IdempotencyStoreImpl(db: MongoDatabase) :
    MongoRepository<IdempotencyRecord>(db, CollectionNames.IDEMPOTENCY, IdempotencyRecord::class.java),
    IdempotencyStore {

    override suspend fun putIfAbsent(record: IdempotencyRecord): IdempotencyRecord? {
        val now = Now.instant()
        // Fast path: read an existing record; if present and not expired, replay it.
        val existing = findByIdIncludingDeleted(record.id)
        if (existing != null && existing.expiresAt > now) {
            return existing
        }
        // Either absent or expired → (over)write our reservation. Use the unique _id to
        // detect the race where two callers both saw "absent".
        return try {
            val doc = toBson(record)
            doc["_ttlAt"] = BsonDateTime(record.expiresAt.toEpochMilliseconds())
            if (existing != null) {
                // Expired record present — replace it (we win the reservation).
                raw.replaceOne(Filters.eq(BsonCodec.ID, record.id), doc)
            } else {
                raw.insertOne(doc)
            }
            null
        } catch (e: MongoWriteException) {
            // Lost the insert race — another caller reserved first; replay theirs.
            findByIdIncludingDeleted(record.id)
        }
    }

    override suspend fun get(key: String): IdempotencyRecord? {
        val record = findByIdIncludingDeleted(key) ?: return null
        return if (record.expiresAt > Now.instant()) record else null
    }

    override suspend fun complete(key: String, responseJson: String, statusCode: Int): IdempotencyRecord {
        raw.updateOne(
            byId(key),
            Updates.combine(
                Updates.set("responseJson", responseJson),
                Updates.set("statusCode", statusCode),
                Updates.set("updatedAt", BsonString(Now.instant().toString())),
            ),
        )
        return findByIdIncludingDeleted(key)
            ?: error("idempotency record '$key' missing after complete()")
    }

    override suspend fun purgeExpired(): Int {
        // The TTL index handles eviction; this is a best-effort manual sweep (ISO-string
        // comparison on the entity field, chronologically correct for UTC ISO-8601).
        val result = raw.deleteMany(Filters.lt("expiresAt", Now.instant().toString()))
        return result.deletedCount.toInt()
    }
}
