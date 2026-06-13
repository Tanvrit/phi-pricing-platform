package com.rate.server.security

import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpRecord
import com.rate.core.auth.otp.OtpStore
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process [OtpStore] actual — the storage half of the OTP flow whose POLICY + crypto live in
 * sdk-proposal's pure `OtpHandler`. RELOCATED from the monolith's `OtpService` in-memory
 * `ConcurrentHashMap<String, OtpRecord>` + `sendBuckets`; the handler now owns the algorithm, this
 * class owns only put/get/delete + the rolling send-window counter.
 *
 * Single-instance correctness only (matches the monolith). A horizontally-scaled deployment swaps
 * this binding for a Mongo `otp_records`-backed actual (TTL index on `expiresAt`) in
 * server-persistence — behind the same [OtpStore] PORT, so no caller changes.
 */
class InMemoryOtpStore : OtpStore {

    private val records = ConcurrentHashMap<String, OtpRecord>()
    private val sendLog = ConcurrentHashMap<String, MutableList<Instant>>()

    override suspend fun put(record: OtpRecord) {
        records[record.storeKey] = record
    }

    override suspend fun get(mobile: String, purpose: OtpPurpose): OtpRecord? =
        records["$mobile:$purpose"]

    override suspend fun update(record: OtpRecord) {
        records[record.storeKey] = record
    }

    override suspend fun delete(mobile: String, purpose: OtpPurpose) {
        records.remove("$mobile:$purpose")
    }

    override suspend fun countSendsSince(mobile: String, purpose: OtpPurpose, since: Instant): Int {
        val key = "$mobile:$purpose"
        val list = sendLog[key] ?: return 0
        synchronized(list) {
            list.removeAll { it < since }
            return list.size
        }
    }

    override suspend fun recordSend(mobile: String, purpose: OtpPurpose, at: Instant) {
        val key = "$mobile:$purpose"
        val list = sendLog.getOrPut(key) { mutableListOf() }
        synchronized(list) { list.add(at) }
    }
}
