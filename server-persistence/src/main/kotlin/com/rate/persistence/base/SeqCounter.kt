package com.rate.persistence.base

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.sdk.audit.repository.SequenceCounter
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

/**
 * Atomic, contention-safe sequence allocator backed by a single counter document per
 * [name] in [CollectionNames.SEQ_COUNTERS]:
 *
 * ```
 * { _id: "<name>", seq: <Long> }
 * ```
 *
 * [nextSeq] does an upserting `findOneAndUpdate({_id:name}, {$inc:{seq:1}}, returnAfter)`,
 * which is a single atomic server-side operation — two concurrent callers can never receive
 * the same value. This is the serialization point that keeps the audit hash chain linear
 * (see [com.rate.sdk.audit.repository.SequenceCounter]).
 *
 * It implements the audit [SequenceCounter] PORT directly (constructed with the audit
 * counter name) and is reused for any other monotonic id need via [nextSeq].
 */
class SeqCounter(
    private val db: MongoDatabase,
    private val counterName: String = AUDIT_COUNTER,
) : SequenceCounter {

    private val collection = db.getCollection<Document>(CollectionNames.SEQ_COUNTERS)

    override suspend fun nextSeq(): Long = nextSeq(counterName)

    override suspend fun current(): Long = current(counterName)

    /** Allocate the next value for an arbitrary named counter (first call yields 1). */
    suspend fun nextSeq(name: String): Long {
        val updated = collection.findOneAndUpdate(
            Filters.eq("_id", name),
            Updates.inc("seq", 1L),
            FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
        )
        return updated?.get("seq")?.let { (it as Number).toLong() } ?: 1L
    }

    /** Most recently allocated value for [name], or 0 if the counter has never been used. */
    suspend fun current(name: String): Long {
        val doc = collection.find(Filters.eq("_id", name)).firstOrNull()
        return doc?.get("seq")?.let { (it as Number).toLong() } ?: 0L
    }

    companion object {
        /** The counter name backing the audit hash chain's gap-free seq. */
        const val AUDIT_COUNTER = "audit"
    }
}
