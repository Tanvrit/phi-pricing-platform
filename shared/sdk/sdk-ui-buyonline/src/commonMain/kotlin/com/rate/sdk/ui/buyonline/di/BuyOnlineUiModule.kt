package com.rate.sdk.ui.buyonline.di

import com.rate.core.network.client.TanvritClient
import com.rate.sdk.proposal.network.BuyOnlineApi
import com.rate.sdk.quoting.network.QuoteApi
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-ui-buyonline.
 *
 * The customer journey is a Compose leaf driven by the feature SDKs' Ktor-client
 * surfaces. It owns no stateful singletons of its own — the [BuyOnlineApp]
 * composable builds its [com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel]
 * per-composition from the supplied [TanvritClient]. This module simply binds the
 * two client surfaces ([BuyOnlineApi], [QuoteApi]) on top of the app-provided
 * [TanvritClient] (from core-network's `networkModule`) so a host that prefers DI
 * over passing the client directly can `get()` them.
 *
 * The concrete engine + Mongo repositories are NEVER bound here — they are
 * server-only. This module only knows the CLIENT transport.
 *
 * Usage in the app shell (WASM customer / JVM operator-embed):
 *   startKoin { modules(networkModule(config), uiKitModule(), buyOnlineUiModule()) }
 */
fun buyOnlineUiModule(): Module = module {
    single { BuyOnlineApi(get<TanvritClient>().http, get<TanvritClient>().config.normalizedBaseUrl) }
    single { QuoteApi(get<TanvritClient>().http, get<TanvritClient>().config.normalizedBaseUrl) }
}
