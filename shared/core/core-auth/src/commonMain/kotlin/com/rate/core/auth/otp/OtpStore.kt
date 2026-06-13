package com.rate.core.auth.otp

import kotlinx.datetime.Instant

/**
 * PORT for OTP persistence keyed by `<mobile>:<purpose>`. The actual lives in the
 * server app (in-memory map now, Mongo `otp_records` later). This contract carries
 * only put/get/delete + the rolling-window send counter; all crypto (code minting,
 * SHA-256 hashing, constant-time compare) and policy enforcement (TTL, max attempts,
 * rate limit thresholds) live in the OTP handler/service in the app layer.
 *
 * Relocated from server `OtpService`'s in-memory `ConcurrentHashMap<String, OtpRecord>`
 * + `sendBuckets`: the storage concern is hoisted to this port so the verification
 * algorithm can be unit-tested against a fake store.
 */
interface OtpStore {

    /** Upsert the record for its [OtpRecord.storeKey], replacing any prior code. */
    suspend fun put(record: OtpRecord)

    /** Latest non-expired-or-not record for the mobile/purpose pair, or null. */
    suspend fun get(mobile: String, purpose: OtpPurpose): OtpRecord?

    /** Replace the stored record (e.g. to bump [OtpRecord.attempts]). */
    suspend fun update(record: OtpRecord)

    /** Drop the record (on success, expiry, or attempt-exhaustion). */
    suspend fun delete(mobile: String, purpose: OtpPurpose)

    /**
     * Count sends recorded for this mobile/purpose since [since]; used by the handler
     * to enforce the per-mobile-per-hour rate limit. The actual records each send
     * timestamp (a bucket list or an indexed `sentAt` column).
     */
    suspend fun countSendsSince(mobile: String, purpose: OtpPurpose, since: Instant): Int

    /** Record that a send happened at [at] for rate-limit accounting. */
    suspend fun recordSend(mobile: String, purpose: OtpPurpose, at: Instant)
}
