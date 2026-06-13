package com.rate.core.network.di

import com.rate.core.network.auth.AuthProvider
import com.rate.core.network.auth.NoAuthProvider
import com.rate.core.network.client.TanvritClient
import com.rate.core.network.client.TanvritClientConfig
import com.rate.core.network.retry.RetryPolicy
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for the network layer. Minimal by design: the app layer supplies the
 * concrete [TanvritClientConfig] (base URL + secrets read from env at startup) and,
 * if it has auth, an [AuthProvider]. core-network only knows how to assemble a
 * [TanvritClient] from those.
 *
 * Usage from an app `startKoin { modules(networkModule(config) , …) }`:
 *   single<AuthProvider> { MyTokenStore() }   // optional; defaults to NoAuthProvider
 */
fun networkModule(config: TanvritClientConfig): Module = module {
    single { config }
    single<AuthProvider> { NoAuthProvider }
    single { RetryPolicy(get<TanvritClientConfig>().retry) }
    single { TanvritClient(get<TanvritClientConfig>(), get<AuthProvider>()) }
}
