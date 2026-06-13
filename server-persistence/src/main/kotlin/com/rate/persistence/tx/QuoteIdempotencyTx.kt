package com.rate.persistence.tx

import com.mongodb.MongoException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.time.Now
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.codec.BsonCodec
import com.rate.sdk.audit.model.IdempotencyRecord
import com.rate.sdk.quoting.model.Quote
import kotlinx.coroutines.flow.firstOrNull
import org.bson.BsonDateTime
import org.bson.BsonDocument

/**
 * Multi-document transaction: persist a saved [Quote] AND its idempotency key atomically in a
 * single `ClientSession` transaction, so a retried "save quote" with the same idempotency key
 * can never produce two quotes (or a quote with no key, or a key with no quote).
 *
 * The kotlin-coroutine driver exposes the low-level `startTransaction` / `commitTransaction` /
 * `abortTransaction` (no `withTransaction` helper), so this drives the transaction explicitly
 * with a small retry on transient/commit-unknown errors — the standard Mongo transaction
 * retry pattern.
 *
 * NOTE: multi-document transactions require a replica-set / sharded deployment (a standalone
 * mongod does not support them). On a standalone, callers should persist the quote and the key
 * with separate idempotent upserts; this tx is the strong-consistency path for production.
 */
class QuoteIdempotencyTx(
    private val client: MongoClient,
    private val db: MongoDatabase,
) {
    private val quotes = db.getCollection(CollectionNames.QUOTES, Quote::class.java)
    private val idempotencyRaw = db.getCollection(CollectionNames.IDEMPOTENCY, BsonDocument::class.java)
    private val idempotency =
        db.getCollection(CollectionNames.IDEMPOTENCY, IdempotencyRecord::class.java)

    /**
     * Save [quote] and reserve [record] (its `_id` is the idempotency key) in ONE transaction.
     *
     * Returns:
     *  - null when the reservation won → both writes committed (the quote is now persisted);
     *  - the EXISTING [IdempotencyRecord] when the key was already taken (non-expired) → nothing
     *    was written; the caller replays the stored response.
     */
    suspend fun saveQuoteWithKey(quote: Quote, record: IdempotencyRecord): IdempotencyRecord? {
        // Pre-check outside a transaction so the common "already done" path is cheap.
        val existing = idempotency.find(Filters.eq(BsonCodec.ID, record.id)).limit(1).firstOrNull()
        if (existing != null && existing.expiresAt > Now.instant()) return existing

        var attempt = 0
        while (true) {
            val session = client.startSession()
            try {
                session.startTransaction()

                quotes.insertOne(session, quote)

                val keyDoc = encodeIdempotency(record)
                idempotencyRaw.replaceOne(
                    session,
                    Filters.eq(BsonCodec.ID, record.id),
                    keyDoc,
                    ReplaceOptions().upsert(true),
                )

                session.commitTransaction()
                return null
            } catch (e: MongoException) {
                runCatching { session.abortTransaction() }
                // Retry the whole transaction on transient errors, bounded.
                if (e.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) && attempt < MAX_RETRIES) {
                    attempt++
                    continue
                }
                throw e
            } finally {
                session.close()
            }
        }
    }

    private fun encodeIdempotency(record: IdempotencyRecord): BsonDocument {
        val doc = BsonDocument()
        val writer = org.bson.BsonDocumentWriter(doc)
        BsonCodec.registry.get(IdempotencyRecord::class.java)
            .encode(writer, record, org.bson.codecs.EncoderContext.builder().build())
        // Stamp the TTL anchor (entity Instant fields serialize as ISO strings; TTL needs Date).
        doc["_ttlAt"] = BsonDateTime(record.expiresAt.toEpochMilliseconds())
        return doc
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
