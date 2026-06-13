package com.rate.sdk.audit.repository

import com.rate.sdk.audit.model.IdempotencyRecord

/**
 * PORT: once-only-execution store keyed by an idempotency key (which IS the record's
 * `_id`). Backs "retry-safe" mutations (proposal submit, payment capture) where a
 * duplicate request must replay the first result rather than re-run the side effect.
 *
 * Mongo-backed actual lives in server-persistence and relies on a unique `_id` insert +
 * a TTL index on `expiresAt` for eviction.
 */
interface IdempotencyStore {

    /**
     * Atomically insert [record] IF its key is free.
     *  - Returns null when the insert won (caller may proceed with the real operation,
     *    then call [complete] to fill the stored response).
     *  - Returns the EXISTING (non-expired) record when the key was already taken — the
     *    caller replays that record's response instead of re-executing.
     *
     * Implementations treat an expired existing record as absent (overwrite/reuse).
     */
    suspend fun putIfAbsent(record: IdempotencyRecord): IdempotencyRecord?

    /** Fetch a stored record by key, or null if absent/expired. */
    suspend fun get(key: String): IdempotencyRecord?

    /**
     * Update a previously-reserved record with the finished response payload/status.
     * Used by the "reserve then complete" pattern when the response isn't known at
     * [putIfAbsent] time. Returns the stored record.
     */
    suspend fun complete(key: String, responseJson: String, statusCode: Int): IdempotencyRecord

    /** Best-effort removal of expired records (no-op where a TTL index handles it). */
    suspend fun purgeExpired(): Int
}
