package com.rate.sdk.audit.handler

import com.rate.sdk.audit.crypto.sha256Hex
import com.rate.sdk.audit.model.AuditActor
import com.rate.sdk.audit.model.AuditEvent
import com.rate.sdk.audit.model.VerifyResult
import kotlinx.datetime.Instant

/**
 * Pure-KMP hash-chain primitives. No IO, no Mongo — these compute and verify hashes
 * identically on client and server. The actual persistence (assigning [AuditEvent.seq],
 * reading the previous hash, inserting) is the responsibility of an
 * [com.rate.sdk.audit.repository.AuditStore] implementation in server-persistence; this
 * object only provides the deterministic math so any party can independently re-derive
 * and verify the chain.
 *
 * Relocated from `server/.../audit/AuditEventService` with the JDK-specific
 * MessageDigest replaced by the portable [sha256Hex] expect/actual and the bespoke
 * canonical serialization extracted into [AuditCanonicalizer].
 */
object AuditChain {

    /** Sentinel previous-hash for the very first event (seq == 1). */
    const val GENESIS: String = "GENESIS"

    /**
     * The hash of an event given the previous event's hash and the event's canonical
     * pre-image. `buildHash(prev, canonical) = sha256(prev + canonical)`.
     */
    fun buildHash(prevHash: String, canonical: String): String =
        sha256Hex(prevHash + canonical)

    /** Convenience: compute the hash an event SHOULD carry, from its own fields. */
    fun hashFor(event: AuditEvent): String =
        buildHash(event.prevHash, AuditCanonicalizer.canonicalPreimage(event))

    /**
     * Assemble the next event in the chain. Callers (the AuditStore append path) pass the
     * already-resolved [seq] and [prevHash]; this stamps the hash and returns a complete,
     * immutable [AuditEvent]. The payload is canonicalized here so the stored bytes ARE
     * the hashed bytes.
     */
    fun nextEvent(
        seq: Long,
        prevHash: String,
        action: String,
        entity: String,
        entityId: String?,
        actor: AuditActor,
        payloadJson: String?,
        at: Instant,
    ): AuditEvent {
        val canonicalPayload = AuditCanonicalizer.canonicalizeJson(payloadJson)
        // Build a provisional event (hash placeholder) so the canonicalizer sees the
        // canonical payload, then compute and attach the real hash.
        val provisional = AuditEvent(
            seq = seq,
            action = action,
            entity = entity,
            entityId = entityId,
            actor = actor,
            payloadJson = canonicalPayload,
            prevHash = prevHash,
            hash = "",
            at = at,
        )
        val hash = buildHash(prevHash, AuditCanonicalizer.canonicalPreimage(provisional))
        return provisional.copy(hash = hash)
    }

    /**
     * Walk [events] (MUST already be ordered by ascending [AuditEvent.seq]) and confirm:
     *  - each event's [AuditEvent.hash] re-computes from its content + [AuditEvent.prevHash];
     *  - each [AuditEvent.prevHash] equals the previous event's hash (genesis = GENESIS);
     *  - [AuditEvent.seq] increases by exactly one, with no gaps.
     *
     * Returns a [VerifyResult]; the first failure short-circuits with [VerifyResult.breakAtSeq].
     * An empty list verifies trivially as ok.
     */
    fun verifyChain(events: List<AuditEvent>): VerifyResult {
        if (events.isEmpty()) return VerifyResult.ok(0)

        var expectedPrevHash: String? = null // null = "accept whatever the slice starts with"
        var expectedSeq: Long? = null
        var checked = 0

        for (event in events) {
            // Sequence continuity (only enforced between consecutive events in the slice).
            if (expectedSeq != null && event.seq != expectedSeq) {
                return VerifyResult.broken(
                    eventsChecked = checked,
                    breakAtSeq = event.seq,
                    reason = "seq gap: expected=$expectedSeq got=${event.seq}",
                )
            }
            // prevHash linkage.
            if (expectedPrevHash != null && event.prevHash != expectedPrevHash) {
                return VerifyResult.broken(
                    eventsChecked = checked,
                    breakAtSeq = event.seq,
                    reason = "prevHash mismatch: expected=$expectedPrevHash got=${event.prevHash}",
                )
            }
            // Recompute this event's hash.
            val recomputed = hashFor(event)
            if (recomputed != event.hash) {
                return VerifyResult.broken(
                    eventsChecked = checked,
                    breakAtSeq = event.seq,
                    reason = "hash mismatch at seq=${event.seq}",
                )
            }
            expectedPrevHash = event.hash
            expectedSeq = event.seq + 1
            checked++
        }
        return VerifyResult.ok(checked)
    }
}
