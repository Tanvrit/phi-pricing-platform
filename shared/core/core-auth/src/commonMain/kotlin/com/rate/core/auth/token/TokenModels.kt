package com.rate.core.auth.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Short-lived access bearer returned to the client after authentication. Carries the
 * compact signed [value] plus a denormalised [expiresAtEpochSeconds] so the client
 * can pre-emptively refresh without decoding the token. The matching [RefreshToken]
 * lets the client mint a fresh access token without re-doing OTP.
 */
@Serializable
data class SessionToken(
    @SerialName("value") val value: String,
    @SerialName("expiresAt") val expiresAtEpochSeconds: Long,
    @SerialName("tokenType") val tokenType: String = "Bearer",
) {
    /** Standard `Authorization` header value. */
    val authorizationHeader: String get() = "$tokenType $value"
}

/**
 * Long-lived opaque refresh credential. Unlike the access token this is NOT a JWT —
 * it is an opaque random handle the server stores against a [com.rate.core.auth.session.SessionRecord]
 * and can revoke. Presented to a refresh endpoint to obtain a new [SessionToken].
 */
@Serializable
data class RefreshToken(
    @SerialName("value") val value: String,
    @SerialName("expiresAt") val expiresAtEpochSeconds: Long,
)

/** Convenience pair returned by login/refresh: a fresh access token + its refresh handle. */
@Serializable
data class TokenPair(
    @SerialName("access") val access: SessionToken,
    @SerialName("refresh") val refresh: RefreshToken,
)
