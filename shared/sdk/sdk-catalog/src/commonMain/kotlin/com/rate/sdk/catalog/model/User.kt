package com.rate.sdk.catalog.model

import com.rate.core.auth.rbac.AuthRole
import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-managed internal/console USER — the principal that logs into the operator console with an
 * email + password (as opposed to the customer buy-online journey, which authenticates by mobile
 * OTP and never gets a User row). One [User] per back-office operator/admin/owner.
 *
 * This is the SINGLE source of truth for the user aggregate: both the server auth flow
 * (AuthRoutes) and the operator console's `users` admin-CRUD descriptor edit THIS type — there is
 * no separate console projection.
 *
 * It is a [ConfigEntity] so admins manage it through the SAME generic admin-CRUD wiring as every
 * other catalog entity (`/api/admin/users`): list / create / update / soft-delete / restore /
 * publish. The publish lifecycle is unused for users in practice (they are created PUBLISHED), but
 * the envelope is carried for uniformity.
 *
 * SECURITY: only the salted password HASH ([passwordHash]) is ever persisted — never the plaintext.
 * The hash is produced by the app-layer `PasswordHasher` (PBKDF2, server security); this pure-KMP
 * module carries no crypto and treats [passwordHash] as an opaque string. The [role] is stored as
 * the [AuthRole] NAME string so the wire/BSON value is stable and a User row drives token issuance
 * (scopes come from [AuthRole.bundleFor]).
 *
 * The login endpoint resolves a User by [email] (unique, case-insensitive at the app layer),
 * verifies [passwordHash], and — on success — issues a JWT whose `sub` is the User [id] and `role`
 * is [role]. [status] (publish envelope) and [accountStatus] (business account state) together gate
 * login via [canLogin]: a non-PUBLISHED, soft-deleted, or non-ACTIVE user cannot authenticate.
 */
@Serializable
data class User(
    @SerialName("_id") override val id: String = newId(),
    /** Login identity — unique, matched case-insensitively by the auth layer. */
    @SerialName("email") val email: String,
    /**
     * Opaque salted password hash (PBKDF2 `algo:iterations:saltB64:hashB64`). Never the plaintext.
     *
     * Defaults to BLANK so a console-CREATED user (whose admin-CRUD payload carries no password)
     * serializes/deserializes cleanly. A blank [passwordHash] means "no local password set yet" —
     * the invite / temp-password / reset flow assigns one later; until then [verify] never matches
     * and the user cannot log in with a password.
     */
    @SerialName("passwordHash") val passwordHash: String = "",
    /** Human display name shown in the operator chrome; blank falls back to name/email via [effectiveDisplayName]. */
    @SerialName("displayName") val displayName: String = "",
    /** Coarse principal role, stored as the [AuthRole] name; drives token scopes via bundleFor. */
    @SerialName("role") val role: AuthRole = AuthRole.BUSINESS,
    /** Business account state (distinct from the publish [status] envelope). Default ACTIVE. */
    @SerialName("accountStatus") val accountStatus: UserAccountStatus = UserAccountStatus.ACTIVE,
    @SerialName("firstName") val firstName: String? = null,
    @SerialName("lastName") val lastName: String? = null,
    @SerialName("phone") val phone: String? = null,
    /** Last successful-login instant; stamped by the auth layer on each login. */
    @SerialName("lastLoginAt") val lastLoginAt: Instant? = null,
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
     * Best-effort human label for the auth response. Prefers the explicit [displayName], then a
     * first/last name join, then the [email] as a last resort — never blank.
     */
    val effectiveDisplayName: String
        get() = displayName.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: email

    /** A User may authenticate only when published, not soft-deleted, and account-ACTIVE. */
    val canLogin: Boolean
        get() = !isDeleted && status == EntityStatus.PUBLISHED && accountStatus == UserAccountStatus.ACTIVE
}

/** Business-account lifecycle state of a [User] (distinct from the publish [EntityStatus] envelope). */
@Serializable
enum class UserAccountStatus { ACTIVE, INVITED, SUSPENDED, DISABLED }
