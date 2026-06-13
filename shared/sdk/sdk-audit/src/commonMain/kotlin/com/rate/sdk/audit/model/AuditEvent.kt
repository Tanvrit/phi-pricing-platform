package com.rate.sdk.audit.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One immutable, hash-chained record in the append-only audit log.
 *
 * This is a TRANSACTIONAL entity (not admin-CRUD), so it implements [BaseDataClass]
 * directly — never edited, never soft-deleted in normal operation; integrity is
 * proven by the hash chain rather than by versioning.
 *
 * Chain semantics:
 *  - [seq] is a strictly increasing, gap-free sequence assigned at append time
 *    (genesis = 1) by the [com.rate.sdk.audit.repository.SequenceCounter] port.
 *  - [prevHash] is the [hash] of the event at `seq - 1` (or
 *    [com.rate.sdk.audit.handler.AuditChain.GENESIS] for the first event).
 *  - [hash] = sha256(prevHash + canonical(this)), where `canonical` is the
 *    deterministic serialization produced by [com.rate.sdk.audit.handler.AuditCanonicalizer].
 *    Because the canonicalizer and SHA-256 are pure-KMP, a client can recompute and
 *    verify any server-emitted hash bit-for-bit.
 *
 * [payloadJson] holds the already-canonicalized JSON of the action's payload (a string,
 * not a JsonElement, so the stored bytes ARE the hashed bytes — no re-serialization
 * ambiguity). Use [com.rate.sdk.audit.handler.AuditCanonicalizer.canonicalizeJson] to
 * produce it before constructing the event.
 */
@Serializable
data class AuditEvent(
    @SerialName("_id") override val id: String = newId(),
    /** Strictly increasing, gap-free chain position. Genesis = 1. */
    @SerialName("seq") val seq: Long,
    /** Verb, e.g. "plan.published", "quote.created", "import.uploaded". */
    @SerialName("action") val action: String,
    /** Resource type the action targeted, e.g. "plan", "quote", "audit_chain". */
    @SerialName("entity") val entity: String,
    /** Id of the affected resource, when applicable. */
    @SerialName("entityId") val entityId: String? = null,
    /** Who performed the action. */
    @SerialName("actor") val actor: AuditActor = AuditActor.unknown(),
    /**
     * Canonical JSON of the action payload (already deterministic). May be the empty
     * object "{}" when there is no payload. These exact bytes feed the hash.
     */
    @SerialName("payloadJson") val payloadJson: String = "{}",
    /** [hash] of the previous event, or GENESIS for the first event. */
    @SerialName("prevHash") val prevHash: String,
    /** sha256(prevHash + canonical(this)). */
    @SerialName("hash") val hash: String,
    /** Wall-clock instant the event occurred (distinct from createdAt for clarity). */
    @SerialName("at") val at: Instant = Now.instant(),

    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass
