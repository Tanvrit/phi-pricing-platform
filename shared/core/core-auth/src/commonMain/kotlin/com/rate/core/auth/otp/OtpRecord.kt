package com.rate.core.auth.otp

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * At-rest record for an issued OTP. This is a transactional persisted entity (it has
 * a short TTL and is deleted on success), so it implements [BaseDataClass] rather
 * than ConfigEntity — it is not admin-managed config.
 *
 * SECURITY: only the SHA-256 [codeHash] is ever stored; the plaintext code never
 * lands here. The hash is computed in the server actual (`OtpStore` impl) — this
 * pure module carries no crypto. [attempts] is bumped on each failed verify and the
 * record is discarded once it hits the policy max or [expiresAt] passes.
 *
 * `<mobile>:<purpose>` is the logical key; the unique key on persistence is that
 * pair (a fresh send overwrites the prior record for the same pair).
 */
@Serializable
data class OtpRecord(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("mobile") val mobile: String,
    @SerialName("purpose") val purpose: OtpPurpose,
    /** SHA-256 hex of the plaintext code. Computed in the server actual; never the code itself. */
    @SerialName("codeHash") val codeHash: String,
    @SerialName("expiresAt") val expiresAt: Instant,
    /** Failed-verify counter; record is dropped when it reaches the policy max. */
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    /** Natural store key combining mobile + purpose. */
    val storeKey: String get() = "$mobile:$purpose"

    fun isExpired(at: Instant = Now.instant()): Boolean = at >= expiresAt
}
