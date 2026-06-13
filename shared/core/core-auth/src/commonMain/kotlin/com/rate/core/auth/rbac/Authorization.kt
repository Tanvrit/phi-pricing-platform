package com.rate.core.auth.rbac

import com.rate.core.auth.token.JwtClaims
import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.error.raise

/**
 * Pure RBAC decision functions. NO transport here — callers (Ktor routes in the
 * server app, guards in the UI) feed in the decoded [JwtClaims] or a granted scope
 * set and act on the boolean / [AppResult]. Relocated from the server's
 * `RoutingContext.requireScope` (which mixed the decision with a Ktor 403 respond);
 * the decision is hoisted here so it is testable and KMP-pure.
 */
object Authorization {

    /** Does this granted scope set satisfy [required] (honouring wildcards)? */
    fun hasScope(granted: Set<Scope>, required: Scope): Boolean =
        granted.any { it.matches(required) }

    /** Does this granted scope set satisfy ALL of [required]? */
    fun hasAllScopes(granted: Set<Scope>, required: Collection<Scope>): Boolean =
        required.all { hasScope(granted, it) }

    /** Does this granted scope set satisfy ANY of [required]? */
    fun hasAnyScope(granted: Set<Scope>, required: Collection<Scope>): Boolean =
        required.any { hasScope(granted, it) }

    /** Convenience over claims: decode the flat scope strings and check [required]. */
    fun hasScope(claims: JwtClaims, required: Scope): Boolean =
        hasScope(claims.scopes.toScopeSet(), required)

    /**
     * Pure check returning [AppResult]: `Ok(Unit)` when granted, else
     * `Err(Forbidden)`. The boundary (route/UI) maps the error to its transport.
     */
    fun requireScope(granted: Set<Scope>, required: Scope): AppResult<Unit> =
        if (hasScope(granted, required)) AppResult.Ok(Unit)
        else AppResult.Err(DomainError.Forbidden("Missing scope '${required.value}'"))

    /** Same over decoded claims. */
    fun requireScope(claims: JwtClaims, required: Scope): AppResult<Unit> =
        requireScope(claims.scopes.toScopeSet(), required)

    /** Throwing variant for call sites that prefer exceptions over results. */
    fun requireScopeOrThrow(granted: Set<Scope>, required: Scope) {
        if (!hasScope(granted, required)) {
            DomainError.Forbidden("Missing scope '${required.value}'").raise()
        }
    }
}
