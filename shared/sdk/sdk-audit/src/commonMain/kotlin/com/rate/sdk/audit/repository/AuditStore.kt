package com.rate.sdk.audit.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.audit.model.AuditEvent

/**
 * PORT: append-only persistence for the hash-chained audit log. Mongo-backed actual
 * lives in server-persistence; read paths may also be consumed by the client via Ktor.
 *
 * This is NOT a [com.rate.core.base.repository.ConfigRepository]: audit events are
 * transactional and immutable — no update / softDelete / publish. The only mutation is
 * [append], and it MUST be the single writer so the chain stays linear.
 */
interface AuditStore {

    /**
     * Append [event] verbatim (its [AuditEvent.seq]/[AuditEvent.prevHash]/[AuditEvent.hash]
     * are already resolved by [com.rate.sdk.audit.handler.AuditChain.nextEvent]). The
     * implementation enforces uniqueness on `seq` so a duplicate allocation cannot create
     * a fork. Returns the stored event.
     */
    suspend fun append(event: AuditEvent): AuditEvent

    /** The hash of the highest-seq event, or [com.rate.sdk.audit.handler.AuditChain.GENESIS] if empty. */
    suspend fun latestHash(): String

    /** The highest allocated seq currently persisted (0 if empty). */
    suspend fun latestSeq(): Long

    /** Fetch a single event by its seq position, or null. */
    suspend fun getBySeq(seq: Long): AuditEvent?

    /**
     * Paged listing ordered by seq. [PageRequest.filter] may carry `action` / `entity` /
     * `entityId` / `actor` equality narrowing (interpreted by the implementation);
     * default ordering is by `seq` (use [PageRequest.sort] to flip to newest-first).
     */
    suspend fun listBySeq(req: PageRequest = PageRequest()): Page<AuditEvent>

    /**
     * Contiguous slice `[fromSeq, toSeq]` (both inclusive) in ascending seq order — the
     * natural input to [com.rate.sdk.audit.handler.AuditChain.verifyChain]. A null bound
     * means "open" (from the start / to the end).
     */
    suspend fun range(fromSeq: Long? = null, toSeq: Long? = null): List<AuditEvent>
}
