package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Append-only record of a single console LOGIN ATTEMPT (success or failure). Distinct from the
 * tamper-evident business audit chain ([com.rate.sdk.audit.model.AuditEvent]): this is a flat,
 * security-operations log used to surface "who logged in / who failed and why / from where" for
 * lockout review and intrusion detection.
 *
 * This is the SINGLE source of truth for the login-audit row: the server auth flow (AuthRoutes)
 * writes it and the operator console's `login-audits` admin hub lists it — there is no separate
 * console projection.
 *
 * It implements [ConfigEntity] only so it can ride the SAME generic admin-CRUD descriptor/registry
 * as every other entity (the publish/draft lifecycle is never exercised — rows are inserted by the
 * auth layer and only ever listed/read, never authored or edited from the UI). Admins read it at
 * `/api/admin/login-audits`.
 *
 * PRIVACY: [email] is the typed login identity (recorded even on failure so a brute-force against a
 * non-existent account is still visible); no password material is ever stored. [ip]/[userAgent] are
 * best-effort request metadata.
 */
@Serializable
data class LoginAudit(
    @SerialName("_id") override val id: String = newId(),
    /** The email the caller attempted to log in as (recorded on success AND failure). */
    @SerialName("email") val email: String = "",
    /** Resolved user id when the attempt matched an account (else blank). */
    @SerialName("userRef") val userRef: String = "",
    @SerialName("success") val success: Boolean = false,
    /** Stable reason code for a failed attempt (e.g. NO_SUCH_USER, BAD_PASSWORD, DISABLED); null on success. */
    @SerialName("failureReason") val failureReason: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("userAgent") val userAgent: String? = null,
    /** Instant of the attempt. */
    @SerialName("at") val at: Instant = Now.instant(),
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity {

    /**
     * Coarse [LoginOutcome] derived from [success] + [failureReason] for the console list column.
     * SUCCESS on success; LOCKED when the failure was a lockout/disabled account; otherwise FAILED.
     */
    val outcome: LoginOutcome
        get() = when {
            success -> LoginOutcome.SUCCESS
            failureReason == REASON_DISABLED -> LoginOutcome.LOCKED
            else -> LoginOutcome.FAILED
        }

    companion object {
        // ── Stable failureReason codes ───────────────────────────────────────
        const val REASON_NO_SUCH_USER = "NO_SUCH_USER"
        const val REASON_BAD_PASSWORD = "BAD_PASSWORD"
        const val REASON_DISABLED = "DISABLED"
    }
}

/** Outcome of a login attempt recorded in a [LoginAudit]. */
@Serializable
enum class LoginOutcome { SUCCESS, FAILED, LOCKED, OTP_SENT, OTP_FAILED, LOGOUT }
