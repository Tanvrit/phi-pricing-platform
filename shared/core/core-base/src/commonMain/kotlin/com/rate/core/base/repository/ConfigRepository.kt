package com.rate.core.base.repository

import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest

/**
 * The ONE generic admin-CRUD contract. Every admin-manageable config entity gets a
 * repository that extends this, so internal users can list/search/create/update/
 * soft-delete/restore/publish ANY entity from the frontend with identical wiring.
 *
 * Implementations (MongoDB-backed) live in `server-persistence`; the same interface is
 * consumed by handlers (server) and — for read paths — by the client via Ktor.
 *
 * Mutations enforce optimistic concurrency on [ConfigEntity.v] and stamp the actor.
 * Draft/publish keeps live rating safe: edits land as DRAFT, [publishDraft] swaps atomically.
 */
interface ConfigRepository<T : ConfigEntity> {
    suspend fun list(req: PageRequest = PageRequest()): Page<T>
    suspend fun get(id: String): T?
    suspend fun create(entity: T, actor: String?): T
    /** Optimistic update — fails with Conflict if [expectedV] != stored v. */
    suspend fun update(entity: T, expectedV: Long, actor: String?): T
    suspend fun softDelete(id: String, actor: String?): Boolean
    suspend fun restore(id: String, actor: String?): Boolean
    /** Promote a DRAFT to PUBLISHED, atomically retiring the version it [ConfigEntity.draftOf]. */
    suspend fun publishDraft(draftId: String, actor: String?): T
    /** Bulk import/seed (idempotent upsert by id). Returns count written. */
    suspend fun bulkUpsert(entities: List<T>, actor: String?): Int
}
