package com.rate.sdk.catalog.di

import com.rate.sdk.catalog.handler.CatalogHandler
import com.rate.sdk.catalog.repository.AddOnRepository
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.DefaultPincodeZoneResolver
import com.rate.sdk.catalog.repository.PincodeZoneRepository
import com.rate.sdk.catalog.repository.PincodeZoneResolver
import com.rate.sdk.catalog.repository.ProductRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.TenureRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-catalog.
 *
 * This module binds only what is constructible in pure-KMP commonMain:
 *  - [PincodeZoneResolver] → the seed-backed [DefaultPincodeZoneResolver] (overridable).
 *  - [CatalogHandler]      → wired from the repository PORTS pulled out of the Koin graph.
 *
 * The repository PORTS themselves (Mongo-backed actuals) are bound by `server-persistence`'s
 * Koin module on the app layer; on the client the read paths use [com.rate.sdk.catalog.network.CatalogApi]
 * instead. Keeping the resolver default here means the handler is usable in tests/offline.
 */
fun catalogModule(): Module = module {
    single<PincodeZoneResolver> { DefaultPincodeZoneResolver() }

    single {
        CatalogHandler(
            products = get<ProductRepository>(),
            sections = get<SectionRepository>(),
            covers = get<CoverRepository>(),
            criticalIllnessLists = get<CriticalIllnessListRepository>(),
            annexures = get<AnnexureRepository>(),
            addOns = get<AddOnRepository>(),
            tenures = get<TenureRepository>(),
            pincodeZones = get<PincodeZoneRepository>(),
            zoneResolver = get<PincodeZoneResolver>(),
        )
    }
}
