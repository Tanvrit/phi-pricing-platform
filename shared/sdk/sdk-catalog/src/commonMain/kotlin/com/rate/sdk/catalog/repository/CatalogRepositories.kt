package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Tenure

/**
 * RETAIL catalog repository PORTS — one per admin-CRUD entity, each extending the generic
 * [ConfigRepository] so the frontend lists/searches/creates/updates/soft-deletes/restores/
 * publishes every catalog entity with identical wiring. Mongo-backed actuals live in
 * server-persistence; read paths are served via [com.rate.sdk.catalog.network.CatalogApi].
 *
 * The repos are typed-marker interfaces (no extra methods beyond the generic CRUD) so DI can
 * bind distinct collections per entity while the handler stays uniform.
 */
interface ProductRepository : ConfigRepository<Product>
interface SectionRepository : ConfigRepository<Section>
interface CoverRepository : ConfigRepository<Cover>
interface CriticalIllnessListRepository : ConfigRepository<CriticalIllnessList>
interface AnnexureRepository : ConfigRepository<Annexure>
interface AddOnRepository : ConfigRepository<AddOn>
interface TenureRepository : ConfigRepository<Tenure>
interface PincodeZoneRepository : ConfigRepository<PincodeZone>
