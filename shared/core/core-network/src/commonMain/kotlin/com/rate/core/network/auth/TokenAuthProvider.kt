package com.rate.core.network.auth

/**
 * An [AuthProvider] backed by a mutable, in-memory JWT bearer token. This is the
 * frontend's credential source after the operator logs in: the login flow [set]s
 * the freshly minted token, the [com.rate.core.network.client.TanvritClient] then
 * attaches it as `Authorization: Bearer <token>` on every outbound request.
 *
 * Unlike [StaticTokenAuthProvider] (a fixed service-account token) the value here
 * changes over the app lifetime — login [set]s it, logout / a server 401 [clear]s
 * it. The token is a single mutable reference: [token] (read per-request on the
 * network path) and [set]/[clear] (called from the UI) only ever assign/read one
 * object reference, which is atomic on the JVM and trivially correct under WASM's
 * single-threaded model — no lock or extra dependency needed for a credential holder.
 *
 * The network layer stays oblivious to the login screen and the token store: it
 * only ever calls [token] and [onUnauthorized] through the [AuthProvider] port.
 * The app layer wires the [onUnauthorized] reaction (drop the token + bounce back
 * to the login surface) via [onUnauthorizedCallback], so this class owns no UI and
 * no navigation.
 *
 * Lifecycle, end to end:
 *   1. boot — token is null → requests go out unauthenticated (public endpoints OK);
 *   2. login success — [set] the JWT → subsequent requests carry the Bearer header;
 *   3. server answers 401 — [com.rate.core.network.client.TanvritClient] calls
 *      [onUnauthorized] → token [clear]ed and [onUnauthorizedCallback] fires so the
 *      shell can show the login gate again;
 *   4. logout — [clear].
 */
class TokenAuthProvider(
    initialToken: String? = null,
    /**
     * App-layer reaction to a 401 (e.g. flip the auth gate back to the login
     * surface). Invoked AFTER the token is cleared. Must not throw — a failure
     * here must not mask the underlying network error.
     */
    private val onUnauthorizedCallback: () -> Unit = {},
) : AuthProvider {

    private var current: String? = initialToken?.takeIf { it.isNotBlank() }

    /** The currently held token, or null when logged out. Read per-request by the client. */
    override suspend fun token(): String? = current

    /** Whether a non-blank token is currently held (i.e. the operator is logged in). */
    val isAuthenticated: Boolean get() = current != null

    /** Store a freshly issued JWT after a successful login. Blank → treated as [clear]. */
    fun set(token: String?) {
        current = token?.takeIf { it.isNotBlank() }
    }

    /** Drop the token (logout, or after an unrecoverable 401). */
    fun clear() {
        current = null
    }

    /**
     * Invoked by the client on a 401: clear the cached token, then notify the app
     * so it can re-show the login gate. Swallows any callback failure.
     */
    override fun onUnauthorized() {
        clear()
        runCatching { onUnauthorizedCallback() }
    }
}
