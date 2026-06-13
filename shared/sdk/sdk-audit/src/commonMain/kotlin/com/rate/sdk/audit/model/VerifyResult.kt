package com.rate.sdk.audit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outcome of walking the hash chain over a (slice of) the audit log.
 *
 * [ok] is true only when every checked event's [AuditEvent.hash] re-computes correctly
 * AND its [AuditEvent.prevHash] matches the previous event's hash AND [AuditEvent.seq]
 * advances by exactly one with no gaps. On failure [breakAtSeq] points at the first
 * offending event and [reason] explains why.
 */
@Serializable
data class VerifyResult(
    @SerialName("ok") val ok: Boolean,
    @SerialName("eventsChecked") val eventsChecked: Int,
    /** seq of the first event that failed verification, when [ok] is false. */
    @SerialName("breakAtSeq") val breakAtSeq: Long? = null,
    @SerialName("reason") val reason: String? = null,
) {
    companion object {
        fun ok(eventsChecked: Int): VerifyResult = VerifyResult(ok = true, eventsChecked = eventsChecked)

        fun broken(eventsChecked: Int, breakAtSeq: Long, reason: String): VerifyResult =
            VerifyResult(ok = false, eventsChecked = eventsChecked, breakAtSeq = breakAtSeq, reason = reason)
    }
}
