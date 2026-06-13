package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.audit.handler.AuditChain
import com.rate.sdk.audit.model.AuditEvent
import com.rate.sdk.audit.repository.AuditStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for the append-only, hash-chained [AuditStore].
 *
 * [append] writes the already-resolved event verbatim; the UNIQUE index on `seq`
 * ([com.rate.persistence.base.IndexBootstrap]) is the safety net that makes a duplicate seq
 * allocation impossible to persist (no chain fork). The seq itself is allocated upstream by the
 * [com.rate.persistence.base.SeqCounter] before [AuditChain.nextEvent] stamps the hash, so this
 * store never invents ordering — it only persists and reads back.
 *
 * Reads expose the chain by seq for verification ([range] feeds [AuditChain.verifyChain]).
 */
class AuditStoreImpl(db: MongoDatabase) :
    MongoRepository<AuditEvent>(db, CollectionNames.AUDIT_EVENTS, AuditEvent::class.java),
    AuditStore {

    override suspend fun append(event: AuditEvent): AuditEvent {
        // Insert verbatim — envelope already set by the event; uniqueness on seq is enforced
        // by the unique index (a duplicate throws, surfacing the fork attempt).
        collection.insertOne(event)
        return event
    }

    override suspend fun latestHash(): String =
        topBySeq()?.hash ?: AuditChain.GENESIS

    override suspend fun latestSeq(): Long = topBySeq()?.seq ?: 0L

    private suspend fun topBySeq(): AuditEvent? =
        collection.find().sort(Sorts.descending("seq")).limit(1).firstOrNull()

    override suspend fun getBySeq(seq: Long): AuditEvent? =
        collection.find(Filters.eq("seq", seq)).limit(1).firstOrNull()

    override suspend fun listBySeq(req: PageRequest): Page<AuditEvent> {
        val clauses = mutableListOf(notDeleted)
        // Optional equality narrowing on the audit-specific filter keys.
        listOf("action", "entity", "entityId", "actor").forEach { key ->
            req.filter[key]?.takeIf { it.isNotBlank() }?.let { value ->
                clauses += if (key == "actor") Filters.eq("actor.subject", value) else Filters.eq(key, value)
            }
        }
        val filter = Filters.and(clauses)
        val sort = if (req.sort.isEmpty()) Sorts.ascending("seq") else buildSort(req)
        val total = collection.countDocuments(filter)
        val items = collection.find(filter)
            .sort(sort)
            .skip(req.page.coerceAtLeast(0) * req.size.coerceAtLeast(1))
            .limit(req.size.coerceAtLeast(1))
            .toList()
        return Page(items, total, req.page, req.size)
    }

    override suspend fun range(fromSeq: Long?, toSeq: Long?): List<AuditEvent> {
        val clauses = mutableListOf<org.bson.conversions.Bson>(notDeleted)
        fromSeq?.let { clauses += Filters.gte("seq", it) }
        toSeq?.let { clauses += Filters.lte("seq", it) }
        return collection.find(Filters.and(clauses))
            .sort(Sorts.ascending("seq"))
            .toList()
    }
}
