package com.rate.core.auth.rbac

import kotlinx.serialization.Serializable

/**
 * Coarse principal role. Mirrors the Aegis app's `AegisRole` so the UI surface and
 * the auth token agree on one vocabulary.
 *
 *  - [CUSTOMER]: end-customer buy-online journey; read catalog, run own quotes,
 *    own proposals only.
 *  - [BUSINESS]: operator surfaces; read + write catalog/quotes, run any quote.
 *  - [ADMIN]: full control incl. RBAC, settings, audit, publish.
 *  - [OWNER]: superuser — every scope (`*`), no restrictions. The role the desktop runs as.
 *
 * The concrete permissions for each role are the bundles in [Scope.bundleFor].
 */
@Serializable
enum class AuthRole {
    CUSTOMER,
    BUSINESS,
    ADMIN,
    OWNER;

    /** The default scope bundle granted to this role. */
    val defaultScopes: Set<Scope> get() = Scope.bundleFor(this)
}
