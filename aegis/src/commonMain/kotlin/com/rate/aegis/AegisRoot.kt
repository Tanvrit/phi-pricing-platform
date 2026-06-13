package com.rate.aegis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rate.aegis.di.buildClient
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.core.network.auth.TokenAuthProvider
import com.rate.sdk.ui.buyonline.BuyOnlineApp
import com.rate.sdk.ui.kit.i18n.AegisLocale
import com.rate.sdk.ui.kit.theme.AegisTheme
import com.rate.sdk.ui.operator.OperatorConsole
import com.rate.sdk.ui.operator.OperatorRole
import com.rate.sdk.ui.operator.network.AuthApi
import com.rate.sdk.ui.operator.surface.LoginSurface

/**
 * The single entry composable for the whole Aegis platform. After the
 * re-architecture this is a *thin* role switch — it owns no surfaces of its own;
 * every screen comes from the sdk-ui-* feature modules:
 *
 *   CUSTOMER → [BuyOnlineApp]   (sdk-ui-buyonline — the public 22-screen journey)
 *   BUSINESS → [OperatorConsole](sdk-ui-operator — operational surfaces)
 *   ADMIN    → [OperatorConsole](sdk-ui-operator — + config-CRUD + ingestion)
 *
 * Wiring done here, and ONLY here:
 *  - build the ONE shared [com.rate.core.network.client.TanvritClient] from the
 *    device-local [com.rate.aegis.settings.AegisSettings] (base URL + actor),
 *    backed by a [TokenAuthProvider] so a logged-in operator's JWT rides every
 *    request as `Authorization: Bearer …`;
 *  - the OPERATOR AUTH GATE (see below);
 *  - resolve the host-owned locale and persist a switch from the buy-online
 *    inline language toggle;
 *  - wrap everything in the kit [AegisTheme] so [com.rate.sdk.ui.kit.i18n.LocalAegisLocale]
 *    and the design-system color tokens resolve below.
 *
 * Auth gate (operator roles only — CUSTOMER short-circuits to the OTP-gated journey):
 *  - [aegisDevProfile] == true  → skip the gate, render the console directly at the
 *    launch [role] (the current dev fast-path; OWNER on desktop). No login needed.
 *  - [aegisDevProfile] == false → show [LoginSurface] until the operator signs in;
 *    on success store the JWT in the [TokenAuthProvider] and render the console with
 *    the [OperatorRole] DERIVED FROM THE TOKEN (not the launch role), so the server's
 *    grant — not the binary's default — decides what the operator can see.
 *
 * The client is built once (remembered) — flipping language or signing in does NOT
 * rebuild the transport; the [TokenAuthProvider] just starts returning the token.
 */
@Composable
fun AegisRoot(role: AegisRole) {
    // Snapshot persisted settings once; the shared client + initial locale derive
    // from it. The buy-online language switcher mutates only the in-composition
    // locale state and persists it; it never needs to rebuild the client.
    val settings = remember { AegisSettingsStore.load() }

    // One mutable credential holder for the whole session. The client reads it per
    // request; login stores into it; a server 401 (via onUnauthorized) clears it and
    // bounces back to the gate.
    var session by remember { mutableStateOf<OperatorSession?>(null) }
    val tokenProvider = remember {
        TokenAuthProvider(onUnauthorizedCallback = { session = null })
    }
    val client = remember { buildClient(settings, tokenProvider) }

    var locale by remember { mutableStateOf(settings.aegisLocale) }
    val onLocaleChange: (AegisLocale) -> Unit = { next ->
        locale = next
        // Apply-on-next-open contract for everything else, but persist immediately
        // so a refresh / relaunch keeps the customer's chosen language.
        runCatching {
            val current = AegisSettingsStore.load()
            AegisSettingsStore.save(current.copy(locale = next.code))
        }
    }

    AegisTheme(dark = settings.dark, locale = locale) {
        when (role) {
            AegisRole.CUSTOMER -> BuyOnlineApp(
                client = client,
                locale = locale,
                onLocaleChange = onLocaleChange,
            )
            AegisRole.BUSINESS,
            AegisRole.ADMIN,
            AegisRole.OWNER -> {
                // Effective operator role: in dev-profile we trust the launch role;
                // otherwise it comes from the JWT once the operator has signed in.
                val gated = !aegisDevProfile && session == null
                if (gated) {
                    val auth = remember { AuthApi(client) }
                    LoginSurface(
                        auth = auth,
                        onAuthenticated = { success ->
                            tokenProvider.set(success.token)
                            session = OperatorSession(success.role, success.displayName)
                        },
                    )
                } else {
                    val effectiveRole = session?.role ?: role.toOperatorRole()
                    OperatorConsole(
                        client = client,
                        role = effectiveRole,
                        actor = (session?.displayName ?: settings.operatorIdentity).trim().ifEmpty { null },
                        dark = settings.dark,
                        locale = locale,
                    )
                }
            }
        }
    }
}

/**
 * The authenticated operator's session, derived from the login JWT: the
 * server-granted [OperatorRole] (drives which console surfaces show) and a display
 * name used both for the chrome and as the audit-attribution actor.
 */
private data class OperatorSession(
    val role: OperatorRole,
    val displayName: String,
)
