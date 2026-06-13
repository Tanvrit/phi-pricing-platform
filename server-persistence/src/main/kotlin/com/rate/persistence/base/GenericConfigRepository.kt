package com.rate.persistence.base

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.error.DomainError
import com.rate.core.base.error.raise
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.repository.ConfigRepository
import com.rate.core.base.time.Now
import com.rate.persistence.codec.BsonCodec
import kotlinx.coroutines.flow.firstOrNull
import org.bson.BsonDateTime
import org.bson.BsonString

/**
 * Generic MongoDB implementation of the core admin-CRUD [ConfigRepository] for ANY
 * [ConfigEntity]. Almost every catalog/config repository PORT (Plan, Cover, Section, …, plus
 * the whole GROUP config tree) is just `class XRepoImpl(db) : GenericConfigRepository<X>(...),
 * XRepository` — one collection name, zero bespoke query code.
 *
 * It layers the [ConfigEntity] semantics on top of [MongoRepository]:
 *  - [create] / [update] stamp the actor into `createdBy`/`updatedBy`;
 *  - [update] enforces optimistic concurrency on `v` (raises [DomainError.Conflict] on drift);
 *  - [publishDraft] atomically promotes a DRAFT to PUBLISHED, retiring the version it
 *    `draftOf`s — keeping live rating safe (an in-progress edit never breaks the published one);
 *  - [bulkUpsert] is an idempotent by-id seed/import.
 *
 * `open` so the rare repository that needs an extra lookup (e.g. a unique-field finder) can
 * subclass and add it without re-implementing CRUD.
 */
open class GenericConfigRepository<T : ConfigEntity>(
    db: MongoDatabase,
    collectionName: String,
    entityClass: Class<T>,
    /** Human-readable entity name used in error messages. */
    private val entityName: String,
) : MongoRepository<T>(db, collectionName, entityClass), ConfigRepository<T> {

    override suspend fun list(req: PageRequest): Page<T> = paged(req)

    override suspend fun get(id: String): T? = findById(id)

    override suspend fun create(entity: T, actor: String?): T {
        val doc = toBson(entity)
        // Stamp creator/updater (config-only fields) at the document level.
        if (actor != null) {
            doc["createdBy"] = org.bson.BsonString(actor)
            doc["updatedBy"] = org.bson.BsonString(actor)
        }
        // Ensure first-write envelope is sane regardless of caller-supplied values.
        val now = BsonString(Now.instant().toString())
        doc["createdAt"] = now
        doc["updatedAt"] = now
        doc["v"] = org.bson.BsonInt64(1L)
        doc["isDeleted"] = org.bson.BsonBoolean(false)
        raw.insertOne(doc)
        return findByIdIncludingDeleted(idOf(entity))
            ?: DomainError.Internal("create($entityName) failed to read back").raise()
    }

    override suspend fun update(entity: T, expectedV: Long, actor: String?): T {
        // Apply the version-checked replace from the base, then stamp updatedBy.
        val replaced = replaceWithVersionCheck(entity, expectedV, entityName)
        if (actor != null) {
            raw.updateOne(byId(idOf(entity)), Updates.set("updatedBy", actor))
        }
        return findByIdIncludingDeleted(idOf(replaced)) ?: replaced
    }

    override suspend fun softDelete(id: String, actor: String?): Boolean {
        val ok = softDelete(id)
        if (ok && actor != null) raw.updateOne(byId(id), Updates.set("updatedBy", actor))
        return ok
    }

    override suspend fun restore(id: String, actor: String?): Boolean {
        val ok = restore(id)
        if (ok && actor != null) raw.updateOne(byId(id), Updates.set("updatedBy", actor))
        return ok
    }

    /**
     * Promote the DRAFT [draftId] to [EntityStatus.PUBLISHED] and atomically retire the
     * PUBLISHED entity it was `draftOf`. Both writes go through the same logical step; if the
     * draft has no `draftOf` it simply flips to PUBLISHED (first publish of a brand-new entity).
     */
    override suspend fun publishDraft(draftId: String, actor: String?): T {
        val draft = findByIdIncludingDeleted(draftId)
            ?: DomainError.NotFound(entityName, draftId).raise()
        if (draft.status != EntityStatus.DRAFT) {
            DomainError.Conflict("$entityName '$draftId' is not a DRAFT (status=${draft.status})").raise()
        }
        val now = BsonString(Now.instant().toString())

        // Retire the published parent (if any).
        draft.draftOf?.let { publishedId ->
            val updates = buildList {
                add(Updates.set("status", EntityStatus.RETIRED.name))
                add(Updates.set("updatedAt", now))
                add(Updates.inc("v", 1L))
                if (actor != null) add(Updates.set("updatedBy", actor))
            }
            raw.updateOne(byId(publishedId), Updates.combine(updates))
        }

        // Promote the draft (drop the draftOf pointer — it is now the live version).
        val promote = buildList {
            add(Updates.set("status", EntityStatus.PUBLISHED.name))
            add(Updates.unset("draftOf"))
            add(Updates.set("updatedAt", now))
            add(Updates.inc("v", 1L))
            if (actor != null) add(Updates.set("updatedBy", actor))
        }
        raw.updateOne(byId(draftId), Updates.combine(promote))
        return findByIdIncludingDeleted(draftId)
            ?: DomainError.Internal("publishDraft($entityName) failed to read back").raise()
    }

    override suspend fun bulkUpsert(entities: List<T>, actor: String?): Int {
        var written = 0
        for (entity in entities) {
            val doc = toBson(entity)
            val id = doc.get(BsonCodec.ID) ?: continue
            val existing = raw.find(Filters.eq(BsonCodec.ID, id)).limit(1).firstOrNull()
            val now = BsonString(Now.instant().toString())
            doc["updatedAt"] = now
            if (existing != null) {
                existing.get("createdAt")?.let { doc["createdAt"] = it }
                val storedV = existing.get("v")?.asNumber()?.longValue() ?: 0L
                doc["v"] = org.bson.BsonInt64(storedV + 1L)
            }
            if (actor != null) {
                doc["updatedBy"] = org.bson.BsonString(actor)
                if (existing == null) doc["createdBy"] = org.bson.BsonString(actor)
            }
            raw.replaceOne(Filters.eq(BsonCodec.ID, id), doc, ReplaceOptions().upsert(true))
            written++
        }
        return written
    }

    private fun idOf(entity: T): String = entity.id
}
