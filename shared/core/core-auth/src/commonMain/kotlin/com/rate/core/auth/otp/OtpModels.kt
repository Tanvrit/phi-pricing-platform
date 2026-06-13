package com.rate.core.auth.otp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire request to send an OTP. `@SerialName` strings are the JSON field names.
 * The server resolves [mobile] + [purpose] to a store key, mints a code of the
 * purpose-appropriate length, hashes it, and dispatches it via SMS.
 */
@Serializable
data class OtpRequest(
    @SerialName("mobile") val mobile: String,
    @SerialName("purpose") val purpose: OtpPurpose = OtpPurpose.LOGIN,
)

/**
 * Wire request to verify a previously-sent OTP. The same [mobile]/[purpose] pair
 * keys the stored [OtpRecord]; [code] is the plaintext the user typed (the server
 * hashes it and compares in constant time against the stored hash).
 */
@Serializable
data class OtpVerifyRequest(
    @SerialName("mobile") val mobile: String,
    @SerialName("code") val code: String,
    @SerialName("purpose") val purpose: OtpPurpose = OtpPurpose.LOGIN,
)

/** Outcome categories shared by send + verify so the client renders one taxonomy. */
@Serializable
enum class OtpStatus {
    /** OTP sent, or verification succeeded. */
    OK,

    /** Too many sends for this mobile in the rolling window (send side). */
    RATE_LIMITED,

    /** No live code for this mobile/purpose (verify side). */
    NO_ACTIVE_CODE,

    /** The stored code's TTL elapsed (verify side). */
    EXPIRED,

    /** Max verification attempts exhausted; the record was discarded (verify side). */
    TOO_MANY_ATTEMPTS,

    /** Code did not match; see [OtpResult.attemptsRemaining] (verify side). */
    MISMATCH,
}

/**
 * Wire result for both send and verify. On a successful verify the server attaches
 * a short-lived [SessionToken]-style bearer in [token]; the customer presents it on
 * the subsequent proposal/KYC call. [attemptsRemaining] is meaningful only for
 * [OtpStatus.MISMATCH]. The plaintext [devCode] is populated ONLY in dev/test
 * profiles (never in production) so automated flows can read the issued code.
 */
@Serializable
data class OtpResult(
    @SerialName("status") val status: OtpStatus,
    @SerialName("token") val token: String? = null,
    @SerialName("attemptsRemaining") val attemptsRemaining: Int? = null,
    @SerialName("devCode") val devCode: String? = null,
)
