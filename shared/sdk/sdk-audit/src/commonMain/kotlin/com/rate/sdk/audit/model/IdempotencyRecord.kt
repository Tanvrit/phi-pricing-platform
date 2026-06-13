package com.rate.sdk.audit.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Records the result of a once-only operation so a retried request with the same
 * idempotency key returns the original outcome instead of re-executing the side effect
 * (e.g. a double-submitted proposal or payment).
 *
 * TRANSACTIONAL [BaseDataClass]: the document `_id` is the idempotency key itself, so an
 * insert collision IS the "already processed" signal — see
 * [com.rate.sdk.audit.repository.IdempotencyStore.putIfAbsent].
 *
 * [responseJson] is the canonical JSON of the first response, replayed verbatim on
 * retries. [expiresAt] drives TTL eviction (Mongo TTL index in server-persistence);
 * after expiry the key may be reused.
 */
@Serializable
data class IdempotencyRecord(
    /** The idempotency key IS the primary key — collisions mean "seen before". */
    @SerialName("_id") override val id: String,
    /** Scope/route the key applies to, e.g. "proposal.submit". */
    @SerialName("scope") val scope: String,
    /** Hash of the request body, to detect key-reuse with a different payload. */
    @SerialName("requestHash") val requestHash: String,
    /** Canonical JSON of the stored response, replayed on retry. */
    @SerialName("responseJson") val responseJson: String = "{}",
    /** HTTP-ish status code of the stored response (200, 201, …). */
    @SerialName("statusCode") val statusCode: Int = 200,
    /** Instant after which this key may be re-used; drives TTL eviction. */
    @SerialName("expiresAt") val expiresAt: Instant,

    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    companion object {
        /** Convenience factory that mints an [id] when the caller has no external key. */
        fun forResult(
            key: String = newId(),
            scope: String,
            requestHash: String,
            responseJson: String,
            statusCode: Int,
            expiresAt: Instant,
        ): IdempotencyRecord = IdempotencyRecord(
            id = key,
            scope = scope,
            requestHash = requestHash,
            responseJson = responseJson,
            statusCode = statusCode,
            expiresAt = expiresAt,
        )
    }
}
