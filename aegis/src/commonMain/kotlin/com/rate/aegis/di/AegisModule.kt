package com.rate.aegis.di

import com.rate.aegis.AegisRole
import com.rate.aegis.toOperatorRole
import com.rate.aegis.settings.AegisSettings
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.core.network.auth.AuthProvider
import com.rate.core.network.auth.NoAuthProvider
import com.rate.core.network.auth.TokenAuthProvider
import com.rate.core.network.client.TanvritClient
import com.rate.sdk.ui.buyonline.di.buyOnlineUiModule
import com.rate.sdk.ui.kit.di.uiKitModule
import com.rate.sdk.ui.operator.di.operatorUiModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Top-level Koin wiring for the Aegis app shell.
 *
 * Aegis is a thin shell, so its own module is small: it binds the device-local
 * [AegisSettings], the credential [AuthProvider] (a session [TokenAuthProvider] so
 * a logged-in operator's JWT rides every request) and the ONE shared
 * [TanvritClient] every sdk-ui module consumes. The actual feature graphs come from
 * the sdk modules' own DSL modules ([uiKitModule], [buyOnlineUiModule],
 * [operatorUiModule]) — Aegis just composes them via [aegisModules] so the platform
 * `main` can `startKoin { modules(aegisModules(role)) }`.
 *
 * The provider is bound under BOTH [TokenAuthProvider] (so the login flow can
 * resolve it and call `set`/`clear`) and [AuthProvider] (so the client consumes it
 * through the port). Pass [NoAuthProvider] explicitly for an unauthenticated graph.
 *
 * Note: the composables ([com.rate.aegis.AegisRoot]) build the client directly
 * from [AppClientFactory] for the simple zero-DI path; this graph exists for
 * hosts/tests that prefer resolving the transport from Koin.
 *
 * @param settings     resolved settings snapshot (defaults to the persisted one).
 * @param authProvider credential source (defaults to a fresh [TokenAuthProvider]).
 */
fun aegisModule(
    settings: AegisSettings = AegisSettingsStore.load(),
    authProvider: AuthProvider = TokenAuthProvider(),
): Module = module {
    single { settings }
    single<AuthProvider> { authProvider }
    single { AppClientFactory.create(get<AegisSettings>(), get<AuthProvider>()) }
}

/**
 * The full module list the platform `main` should start Koin with, for the given
 * [role]. CUSTOMER pulls only the kit + buy-online graph; BUSINESS/ADMIN pull the
 * kit + operator graph wired for the mapped [OperatorRole].
 */
fun aegisModules(
    role: AegisRole,
    settings: AegisSettings = AegisSettingsStore.load(),
    authProvider: AuthProvider = TokenAuthProvider(),
): List<Module> {
    val base = listOf(aegisModule(settings, authProvider), uiKitModule())
    return when (role) {
        AegisRole.CUSTOMER -> base + buyOnlineUiModule()
        AegisRole.BUSINESS, AegisRole.ADMIN, AegisRole.OWNER ->
            base + operatorUiModule(
                role = role.toOperatorRole(),
                actor = settings.operatorIdentity.trim().ifEmpty { null },
            )
    }
}

/** Convenience accessor — build the shared client from a settings snapshot. */
fun buildClient(
    settings: AegisSettings = AegisSettingsStore.load(),
    authProvider: AuthProvider = NoAuthProvider,
): TanvritClient = AppClientFactory.create(settings, authProvider)
