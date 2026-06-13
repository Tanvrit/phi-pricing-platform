package com.rate.core.network.auth

/**
 * Pluggable bearer-credential source for [com.rate.core.network.client.TanvritClient].
 * Kept as a tiny port so the network layer never owns a token store, a login
 * screen, or RBAC: the app layer supplies an implementation (reading from secure
 * storage on the frontend, or from a service account on the backend).
 *
 * The client calls [token] before every outbound request and, if non-null/blank,
 * attaches it as `Authorization: Bearer <token>`. On a 401 it calls
 * [onUnauthorized] so the app can clear the token / trigger re-auth, then surfaces
 * a [com.rate.core.network.error.NetworkError.Unauthorized] to the caller.
 */
interface AuthProvider {
    /** Current credential, or null/blank to send the request unauthenticated. */
    suspend fun token(): String?

    /**
     * Invoked when the server answers 401. Implementations typically clear the
     * cached token and kick off a refresh/login. Must not throw — failures here
     * shouldn't mask the original network error.
     */
    fun onUnauthorized()
}

/**
 * A no-op provider: never attaches a token, ignores 401s. The default so a
 * [com.rate.core.network.client.TanvritClient] is usable for unauthenticated
 * endpoints (health, public catalog) without ceremony.
 */
object NoAuthProvider : AuthProvider {
    override suspend fun token(): String? = null
    override fun onUnauthorized() {}
}

/**
 * Provider backed by a static token (env var on the backend, build-injected key
 * for service-to-service calls). [onUnauthorized] is a hook the caller may override.
 */
open class StaticTokenAuthProvider(private val staticToken: String?) : AuthProvider {
    override suspend fun token(): String? = staticToken
    override fun onUnauthorized() {}
}

/**
 * The header name used for the actor-attribution contract carried by the old
 * aegis ApiClient (`X-Aegis-Actor`). Lifted here so both backend handlers and the
 * frontend agree on the spelling without depending on aegis.
 */
const val ACTOR_HEADER: String = "X-Aegis-Actor"
