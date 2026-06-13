package com.rate.sdk.ui.operator.registry

import com.rate.core.auth.rbac.AuthRole
import com.rate.sdk.catalog.model.LoginAudit
import com.rate.sdk.catalog.model.LoginOutcome
import com.rate.sdk.catalog.model.User
import com.rate.sdk.catalog.model.UserAccountStatus
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the IDENTITY / access entities surfaced in the operator console:
 *  - `users` — operator/admin accounts (email / display name / role / account status), fully editable.
 *  - `login-audits` — read-mostly login trail (listed/searched, never authored from the UI).
 *
 * These ride the same generic [com.rate.sdk.ui.operator.screen.ConfigEntityScreen] as catalog
 * entities. Both descriptors point at the UNIFIED domain types in `sdk-catalog/model`
 * ([com.rate.sdk.catalog.model.User] / [com.rate.sdk.catalog.model.LoginAudit]) — the SAME types the
 * server auth flow reads/writes — so a console-created/edited user round-trips end-to-end.
 */
internal object IdentityDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val user = TypedEntityDescriptor(
        id = "users",
        singular = "User",
        plural = "Users",
        description = "Operator/admin accounts — email, role and account status. Invite, suspend or re-role users.",
        isGroup = false,
        serializer = User.serializer(),
        fields = listOf(
            FormField("email", "Email", FieldKind.TEXT, required = true, helper = "Login identity — must be unique"),
            FormField("displayName", "Display name", FieldKind.TEXT),
            FormField("role", "Role", FieldKind.ENUM, enumNames(AuthRole.entries.toTypedArray()), helper = "Coarse RBAC role"),
            FormField("accountStatus", "Account status", FieldKind.ENUM, enumNames(UserAccountStatus.entries.toTypedArray())),
        ),
        columns = listOf(
            "Email" to { u: User -> u.email },
            "Name" to { u -> u.effectiveDisplayName.ifBlank { "—" } },
            "Role" to { u -> u.role.name },
            "Account" to { u -> u.accountStatus.name },
        ),
        factory = { User(email = "") },
        reader = { u ->
            mapOf(
                "email" to u.email,
                "displayName" to u.displayName,
                "role" to u.role.name,
                "accountStatus" to u.accountStatus.name,
            )
        },
        editor = { base, b ->
            base.copy(
                email = FormCodec.str(b, "email", base.email),
                displayName = FormCodec.str(b, "displayName", base.displayName),
                role = FormCodec.enum(b, "role", base.role),
                accountStatus = FormCodec.enum(b, "accountStatus", base.accountStatus),
            )
        },
        validator = { b ->
            buildList {
                val email = FormCodec.str(b, "email")
                if (email.isBlank()) add("Email is required")
                else if (!email.contains('@')) add("Email looks invalid")
            }
        },
    )

    val loginAudit = TypedEntityDescriptor(
        id = "login-audits",
        singular = "Login Audit",
        plural = "Login Audits",
        description = "Read-mostly login trail — email, outcome, IP and time. Listed and searched, not authored here.",
        isGroup = false,
        serializer = LoginAudit.serializer(),
        fields = listOf(
            FormField("email", "Email", FieldKind.TEXT),
            FormField("userRef", "User", FieldKind.ENTITY_PICKER, refEntityId = "users"),
            FormField("outcome", "Outcome", FieldKind.ENUM, enumNames(LoginOutcome.entries.toTypedArray())),
            FormField("ip", "IP address", FieldKind.TEXT),
            FormField("userAgent", "User agent", FieldKind.MULTILINE),
            FormField("at", "At", FieldKind.READONLY),
        ),
        columns = listOf(
            "Email" to { a: LoginAudit -> a.email.ifBlank { "—" } },
            "Outcome" to { a -> a.outcome.name },
            "IP" to { a -> a.ip?.ifBlank { null } ?: "—" },
            "At" to { a -> a.at.toString().take(19).replace('T', ' ') },
        ),
        factory = { LoginAudit() },
        reader = { a ->
            mapOf(
                "email" to a.email,
                "userRef" to a.userRef,
                "outcome" to a.outcome.name,
                "ip" to a.ip.orEmpty(),
                "userAgent" to a.userAgent.orEmpty(),
                "at" to a.at.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                email = FormCodec.str(b, "email", base.email),
                userRef = FormCodec.str(b, "userRef", base.userRef),
                ip = FormCodec.strOrNull(b, "ip") ?: base.ip,
                userAgent = FormCodec.strOrNull(b, "userAgent") ?: base.userAgent,
            )
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(user, loginAudit)
}
