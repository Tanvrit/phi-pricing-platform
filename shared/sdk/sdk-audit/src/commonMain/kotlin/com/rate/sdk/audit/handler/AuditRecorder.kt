package com.rate.sdk.audit.handler

import com.rate.core.base.time.Now
import com.rate.sdk.audit.event.AuditBroadcast
import com.rate.sdk.audit.model.AuditActor
import com.rate.sdk.audit.model.AuditEvent
import com.rate.sdk.audit.model.VerifyResult
import com.rate.sdk.audit.repository.AuditStore
import com.rate.sdk.audit.repository.SequenceCounter

/**
 * Orchestrates the append path: allocate seq → read previous hash → build the chained
 * event → persist → broadcast. Relocated from the old `AuditEventService.record`, but
 * with the JDBC transaction and JDK MessageDigest pushed behind the [AuditStore] /
 * [SequenceCounter] ports and the portable [AuditChain].
 *
 * Pure-KMP: depends only on ports + coroutines, so it compiles and runs on JVM, wasmJs
 * and iOS. The server wires Mongo-backed ports; a client could wire HTTP-backed read
 * ports for verification.
 *
 * Failure policy mirrors the original: [record] never throws — an audit write must not
 * break the business operation it is recording. On failure it returns null.
 */
class AuditRecorder(
    private val store: AuditStore,
    private val sequence: SequenceCounter,
    private val broadcast: AuditBroadcast,
) {

    /**
     * Record one event and return it (or null if the write failed). [payloadJson] is any
     * valid JSON (canonicalized internally); null/blank stores the empty object.
     *
     * NOTE: append must be the single writer; the [SequenceCounter] + a unique-seq
     * constraint in [AuditStore.append] serialize concurrent callers so the chain links
     * correctly. If two appends still race, the store's uniqueness check rejects the
     * loser, which surfaces here as a caught failure → null (the loser should retry).
     */
    suspend fun record(
        action: String,
        entity: String,
        entityId: String? = null,
        payloadJson: String? = null,
        actor: AuditActor = AuditActor.unknown(),
    ): AuditEvent? = try {
        val seq = sequence.nextSeq()
        val prevHash = store.latestHash()
        val event = AuditChain.nextEvent(
            seq = seq,
            prevHash = prevHash,
            action = action,
            entity = entity,
            entityId = entityId,
            actor = actor,
            payloadJson = payloadJson,
            at = Now.instant(),
        )
        val stored = store.append(event)
        broadcast.publish(stored)
        stored
    } catch (t: Throwable) {
        // Swallow: never let an audit write break the recorded operation.
        null
    }

    /**
     * Verify a contiguous slice `[fromSeq, toSeq]` of the persisted chain. Reads via the
     * store, checks math via [AuditChain]. A null bound means open-ended.
     */
    suspend fun verifyChain(fromSeq: Long? = null, toSeq: Long? = null): VerifyResult =
        AuditChain.verifyChain(store.range(fromSeq, toSeq))
}
