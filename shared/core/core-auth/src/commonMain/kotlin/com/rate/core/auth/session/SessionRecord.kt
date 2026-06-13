package com.rate.core.auth.session

import com.rate.core.auth.rbac.AuthRole
import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-side record of an authenticated session, keyed by [id] (the opaque session
 * handle the refresh token resolves to). Lets the server revoke access per-session /
 * per-device even though the access JWT itself is stateless. Transactional, so it
 * implements [BaseDataClass], not ConfigEntity.
 *
 * [refreshTokenHash] stores only the SHA-256 of the opaque refresh handle (hashing in
 * the server actual); the raw handle is never persisted. [expiresAt] is the session
 * TTL; [revoked] supports immediate logout/forced-logout.
 */
@Serializable
data class SessionRecord(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("subject") val subject: String,
    @SerialName("role") val role: AuthRole,
    @SerialName("deviceId") val deviceId: String? = null,
    /** SHA-256 hex of the opaque refresh handle; never the raw handle. */
    @SerialName("refreshTokenHash") val refreshTokenHash: String? = null,
    @SerialName("expiresAt") val expiresAt: Instant,
    @SerialName("revoked") val revoked: Boolean = false,
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    fun isExpired(at: Instant = Now.instant()): Boolean = at >= expiresAt
    fun isActive(at: Instant = Now.instant()): Boolean = !revoked && !isExpired(at)
}
