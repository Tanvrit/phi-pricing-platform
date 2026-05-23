package com.rate.server.auth

import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * 403s if the caller's identity doesn't have [scope] (or admin role).
 *
 * Empty operators store = unbootstrapped → permissive. The first POST to
 * /api/operators populates the file; from then on every mutation route is
 * enforced. This intentionally avoids a "create initial admin" flow — an SRE
 * either drops a JSON file in `~/.aegis/operators.json` out-of-band or uses
 * the unbootstrapped window to add their own identity via the Aegis UI.
 *
 * Routes call this at the top of their handler. On scope-miss we [respond]
 * with 403 and then return false; callers check the return and bail. We
 * can't `finish()` cleanly across Ktor 3's RoutingContext API, so the
 * callsite pattern is `if (!requireScope("plans.write")) return@post`.
 */
suspend fun RoutingContext.requireScope(scope: String): Boolean {
    val store = OperatorsStore.load()
    if (store.operators.isEmpty()) return true
    val identity = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
    if (OperatorsStore.hasScope(identity, scope)) return true
    call.respond(HttpStatusCode.Forbidden, mapOf(
        "errorCode" to "INSUFFICIENT_SCOPE",
        "scope" to scope,
        "identity" to (identity ?: "unknown")
    ))
    return false
}
