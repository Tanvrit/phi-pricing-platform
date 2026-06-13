package com.rate.sdk.audit.repository

/**
 * PORT: hands out the strictly-increasing, gap-free [com.rate.sdk.audit.model.AuditEvent.seq]
 * values that order the hash chain. The genesis event is seq == 1.
 *
 * The implementation (server-persistence) MUST guarantee atomic, contention-safe
 * allocation — e.g. a single-document `findOneAndUpdate({_id:"audit"}, {$inc:{seq:1}})`
 * in Mongo — so two concurrent appends never receive the same seq. This is the
 * serialization point that makes the chain linear.
 */
interface SequenceCounter {
    /** Atomically allocate and return the next sequence number (first call yields 1). */
    suspend fun nextSeq(): Long

    /** The most recently allocated value (0 if none yet). For bootstrap/diagnostics. */
    suspend fun current(): Long
}
