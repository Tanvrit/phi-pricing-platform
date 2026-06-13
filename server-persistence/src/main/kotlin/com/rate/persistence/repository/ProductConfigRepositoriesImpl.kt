package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.productconfig.AdaptiveCategory
import com.rate.sdk.catalog.model.productconfig.BenefitType
import com.rate.sdk.catalog.model.productconfig.Category
import com.rate.sdk.catalog.model.productconfig.CategoryModel
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.catalog.repository.AdaptiveCategoryRepository
import com.rate.sdk.catalog.repository.BenefitTypeRepository
import com.rate.sdk.catalog.repository.CategoryModelRepository
import com.rate.sdk.catalog.repository.CategoryRepository
import com.rate.sdk.catalog.repository.ProductAddonConfigRepository

/** Mongo collection names for the PRODUCT-CONFIG master entities (lowerCamel plural per entity). */
object ProductConfigCollections {
    const val PRODUCT_ADDON_CONFIGS = "productAddonConfigs"
    const val CATEGORIES = "categories"
    const val BENEFIT_TYPES = "benefitTypes"
    const val CATEGORY_MODELS = "categoryModels"
    const val ADAPTIVE_CATEGORIES = "adaptiveCategories"
}

/**
 * Mongo actuals for the PRODUCT-CONFIG admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does all the
 * work; the only per-entity inputs are the collection name and entity class.
 */

class ProductAddonConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ProductAddonConfig>(
        db, ProductConfigCollections.PRODUCT_ADDON_CONFIGS, ProductAddonConfig::class.java, "ProductAddonConfig",
    ),
    ProductAddonConfigRepository

class CategoryRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Category>(db, ProductConfigCollections.CATEGORIES, Category::class.java, "Category"),
    CategoryRepository

class BenefitTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<BenefitType>(db, ProductConfigCollections.BENEFIT_TYPES, BenefitType::class.java, "BenefitType"),
    BenefitTypeRepository

class CategoryModelRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<CategoryModel>(
        db, ProductConfigCollections.CATEGORY_MODELS, CategoryModel::class.java, "CategoryModel",
    ),
    CategoryModelRepository

class AdaptiveCategoryRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<AdaptiveCategory>(
        db, ProductConfigCollections.ADAPTIVE_CATEGORIES, AdaptiveCategory::class.java, "AdaptiveCategory",
    ),
    AdaptiveCategoryRepository
