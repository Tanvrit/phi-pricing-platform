package com.rate.sdk.rating.di

import com.rate.core.rating.ports.GroupRateDataProvider
import com.rate.core.rating.ports.RateDataProvider
import com.rate.core.rating.ports.RatingPort
import com.rate.core.rating.ports.RenewalRateProvider
import com.rate.sdk.rating.data.InProcessGroupRateDataProvider
import com.rate.sdk.rating.data.InProcessRateDataProvider
import com.rate.sdk.rating.handler.GroupPricingEngine
import com.rate.sdk.rating.handler.PricingEngine
import com.rate.sdk.rating.repository.RateDataRenewalProvider
import org.koin.dsl.module

/**
 * Koin wiring for sdk-rating.
 *
 * Binds the engines behind their core PORTs so downstream features depend on the contract,
 * never the concretes:
 *  - [RatingPort]            ← [PricingEngine]
 *  - [GroupPricingEngine]    (concrete; the group result type is module-local)
 *  - [RenewalRateProvider]   ← [RateDataRenewalProvider] over the same [RateDataProvider]
 *
 * The [RateDataProvider] / [GroupRateDataProvider] ACTUALS (Mongo snapshot) are bound by the
 * app layer (server-persistence). For offline contexts (WASM customer journey / desktop
 * fallback) this module falls back to the in-process providers via `getOrNull()`, so it both
 * compiles standalone and works without a backend — matching the sdk-party DI convention.
 *
 * Usage in the app: `startKoin { modules(serverPersistenceModule, ratingModule) }` — when
 * server-persistence is on the graph its Mongo providers win; otherwise the in-process ones do.
 */
val ratingModule = module {
    single<RateDataProvider> { InProcessRateDataProvider() }
    single<GroupRateDataProvider> { InProcessGroupRateDataProvider() }

    single<RatingPort> { PricingEngine(data = get()) }
    single { PricingEngine(data = get()) }
    single {
        GroupPricingEngine(
            data = get<GroupRateDataProvider>(),
            retail = getOrNull<PricingEngine>(),
        )
    }
    single<RenewalRateProvider> { RateDataRenewalProvider(data = get()) }
}
