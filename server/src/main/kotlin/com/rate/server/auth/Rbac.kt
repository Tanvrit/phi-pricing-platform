package com.rate.server.auth

import com.rate.core.auth.rbac.AuthRole
import com.rate.core.auth.rbac.Authorization
import com.rate.core.auth.rbac.Scope
import com.rate.core.auth.rbac.toScopeSet
import com.rate.core.auth.token.JwtClaims
import com.rate.core.auth.token.TokenSigner
import com.rate.sdk.audit.model.AuditActor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.util.AttributeKey
import org.koin.ktor.ext.get

/**
 * Server-side RBAC boundary. The pure DECISION lives in core-auth's [Authorization]
 * (KMP, testable); this file is the thin Ktor glue that:
 *  1. resolves the caller's [JwtClaims] from the `Authorization: Bearer <jwt>` header (verified by
 *     the injected [TokenSigner] actual), falling back to the legacy `X-Aegis-Actor` header during
 *     the auth-migration window;
 *  2. exposes [requireScope] which 403s when the decision says "Forbidden".
 *
 * RELOCATED from the monolith's `RoutingContext.requireScope`, which mixed an operators-JSON
 * lookup with a Ktor 403 respond. The operators-file allowlist is replaced by real signed-token
 * scopes (the `X-Aegis-Actor` fallback preserves the monolith's "trust the header" behaviour ONLY
 * when no bearer is present, so existing operator tooling keeps working until JWTs are everywhere).
 */

/** Cache the resolved claims on the call so repeated [requireScope] checks verify the token once. */
private val CLAIMS_KEY = AttributeKey<JwtClaims>("authClaims")

/** Resolve (and memoise) the verified [JwtClaims] for this call, or null when unauthenticated. */
fun ApplicationCall.authClaims(): JwtClaims? {
    attributes.getOrNull(CLAIMS_KEY)?.let { return it }
    val signer: TokenSigner = application.get()
    val bearer = request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")
        ?.trim()
    val claims = bearer?.let { signer.verify(it) }
    if (claims != null) attributes.put(CLAIMS_KEY, claims)
    return claims
}

/**
 * The granted scope set for this call. From the verified token when present; otherwise — during
 * the migration window — the legacy `X-Aegis-Actor` header is treated as an unverified BUSINESS
 * operator (its self-declared identity becomes the audit subject). When neither is present the
 * caller is anonymous (empty grant), so only un-scoped public routes succeed.
 */
fun ApplicationCall.grantedScopes(): Set<Scope> {
    authClaims()?.let { return it.scopes.toScopeSet() }
    // DEV PROFILE (AEGIS_DEV_PROFILE=true): local manual testing has no login yet, so grant full
    // ADMIN scopes to every call. Strictly dev-gated — production (devProfile=false) is unaffected.
    if (System.getenv("AEGIS_DEV_PROFILE")?.equals("true", ignoreCase = true) == true) {
        return AuthRole.ADMIN.defaultScopes
    }
    // Legacy fallback: an X-Aegis-Actor header (no bearer) is trusted as a BUSINESS operator.
    // This preserves the monolith's "trust the header" RBAC until every client sends a JWT.
    val legacyActor = request.headers["X-Aegis-Actor"]?.trim()?.takeIf { it.isNotEmpty() }
    return if (legacyActor != null) AuthRole.BUSINESS.defaultScopes else emptySet()
}

/** The audit actor for this call — verified subject/role from the token, else the legacy header. */
fun ApplicationCall.auditActor(): AuditActor {
    val rid = request.headers["X-Request-Id"]
    authClaims()?.let {
        return AuditActor(subject = it.sub, role = it.role.name, requestId = rid)
    }
    val legacy = request.headers["X-Aegis-Actor"]?.trim()?.takeIf { it.isNotEmpty() }
    return if (legacy != null) AuditActor(subject = legacy, role = AuthRole.BUSINESS.name, requestId = rid)
    else AuditActor.unknown().copy(requestId = rid)
}

/**
 * Enforce that the caller holds [required] (honouring wildcards via [Authorization]). On a miss it
 * responds 403 with a stable error envelope and returns false. Call-site pattern (Ktor 3 has no
 * clean cross-`RoutingContext` `finish()`):
 *
 * ```
 * post { if (!requireScope(Scope.config("plan", Scope.WRITE))) return@post; … }
 * ```
 */
suspend fun RoutingContext.requireScope(required: Scope): Boolean {
    val granted = call.grantedScopes()
    if (Authorization.hasScope(granted, required)) return true
    call.respond(
        HttpStatusCode.Forbidden,
        ForbiddenResponse(
            scope = required.value,
            identity = call.authClaims()?.sub
                ?: call.request.headers["X-Aegis-Actor"]?.trim()
                ?: "anonymous",
        ),
    )
    return false
}

@kotlinx.serialization.Serializable
internal data class ForbiddenResponse(
    val errorCode: String = "INSUFFICIENT_SCOPE",
    val scope: String,
    val identity: String,
)
