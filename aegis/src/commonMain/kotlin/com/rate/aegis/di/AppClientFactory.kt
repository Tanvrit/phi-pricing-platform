package com.rate.aegis.di

import com.rate.aegis.settings.AegisSettings
import com.rate.core.network.auth.ACTOR_HEADER
import com.rate.core.network.auth.AuthProvider
import com.rate.core.network.auth.NoAuthProvider
import com.rate.core.network.client.NetworkLogLevel
import com.rate.core.network.client.TanvritClient
import com.rate.core.network.client.TanvritClientConfig

/**
 * Builds the ONE shared [TanvritClient] the whole Aegis shell hands to the sdk-ui
 * modules (BuyOnlineApp / OperatorConsole). This is the single place a base URL,
 * an [AuthProvider] and the operator actor-attribution header come together.
 *
 * - baseUrl is read from the device-local [AegisSettings.serverBaseUrl];
 * - the [authProvider] is the app-layer credential source. It defaults to
 *   [NoAuthProvider] (the customer journey is OTP-gated server-side and needs no
 *   bearer), but the operator shell injects a
 *   [com.rate.core.network.auth.TokenAuthProvider] so a logged-in operator's JWT
 *   rides every request as `Authorization: Bearer …` once [AegisRoot]'s auth gate
 *   stores it on login;
 * - the operator/admin identity is forwarded as the `X-Aegis-Actor` header on
 *   every request via the client's dynamic-headers hook, so the server can
 *   attribute audit rows. Blank actor = no header (server falls back to unknown).
 *
 * Networking (ContentNegotiation with the frozen AppJson, timeouts, retry,
 * status-mapping, the platform engine) is all wired ONCE inside [TanvritClient];
 * this factory only assembles the [TanvritClientConfig].
 */
object AppClientFactory {

    /**
     * @param settings     device-local settings (base URL + operator identity).
     * @param authProvider credential source; defaults to [NoAuthProvider].
     */
    fun create(
        settings: AegisSettings,
        authProvider: AuthProvider = NoAuthProvider,
    ): TanvritClient {
        val config = TanvritClientConfig(
            baseUrl = settings.serverBaseUrl,
            enableLogging = platformLogLevel != NetworkLogLevel.NONE,
            logLevel = platformLogLevel,
            userAgent = "aegis/$platformName",
        )
        val actor = settings.operatorIdentity.trim()
        return TanvritClient(
            config = config,
            auth = authProvider,
            dynamicHeaders = {
                if (actor.isNotEmpty()) mapOf(ACTOR_HEADER to actor) else emptyMap()
            },
        )
    }
}
