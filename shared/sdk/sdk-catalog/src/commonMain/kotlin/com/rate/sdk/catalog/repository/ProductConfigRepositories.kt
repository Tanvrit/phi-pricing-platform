package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.productconfig.AdaptiveCategory
import com.rate.sdk.catalog.model.productconfig.BenefitType
import com.rate.sdk.catalog.model.productconfig.Category
import com.rate.sdk.catalog.model.productconfig.CategoryModel
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig

/**
 * PRODUCT-CONFIG master-data repository PORTS — one per admin-CRUD entity, each extending the
 * generic [ConfigRepository] so the operator console lists/searches/creates/updates/soft-deletes/
 * restores/publishes every product-config master with identical wiring. Mongo-backed actuals live
 * in server-persistence (ProductConfigRepositoriesImpl). These are typed-marker interfaces (no extra
 * methods beyond the generic CRUD) so DI can bind a distinct collection per entity while the handler
 * stays uniform.
 */
interface ProductAddonConfigRepository : ConfigRepository<ProductAddonConfig>
interface CategoryRepository : ConfigRepository<Category>
interface BenefitTypeRepository : ConfigRepository<BenefitType>
interface CategoryModelRepository : ConfigRepository<CategoryModel>
interface AdaptiveCategoryRepository : ConfigRepository<AdaptiveCategory>
