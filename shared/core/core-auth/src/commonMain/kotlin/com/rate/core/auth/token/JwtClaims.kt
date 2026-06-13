package com.rate.core.auth.token

import com.rate.core.auth.rbac.AuthRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Decoded JWT payload (claims). This is the wire shape of the token body; the
 * signing/verification (HS256 with the secret) is done by a [TokenSigner] actual in
 * the server app — this module never sees the key.
 *
 * Field names follow JWT registered-claim conventions where they exist (`sub`, `exp`)
 * so a standard JWT decoder produces this object. [scopes] is the flattened set of
 * permission strings (see [com.rate.core.auth.rbac.Scope]); [keyId] selects the
 * signing key for rotation.
 */
@Serializable
data class JwtClaims(
    /** Subject — the authenticated principal id (operator id or customer mobile/id). */
    @SerialName("sub") val sub: String,
    @SerialName("role") val role: AuthRole,
    @SerialName("scopes") val scopes: Set<String> = emptySet(),
    /** Device the token was issued to; lets sessions be revoked per-device. */
    @SerialName("deviceId") val deviceId: String? = null,
    /** Expiry, epoch SECONDS (JWT `exp` convention). */
    @SerialName("exp") val exp: Long,
    /** Signing-key id for key rotation; verifier picks the matching key. */
    @SerialName("kid") val keyId: String? = null,
) {
    /** True when [exp] is at or before [nowEpochSeconds]. */
    fun isExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= exp
}
