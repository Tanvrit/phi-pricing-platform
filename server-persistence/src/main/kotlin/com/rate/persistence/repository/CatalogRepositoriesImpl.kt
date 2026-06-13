package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.catalog.repository.AddOnRepository
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.PincodeZoneRepository
import com.rate.sdk.catalog.repository.ProductRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.TenureRepository

/**
 * Mongo actuals for the RETAIL catalog admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does
 * all the work; the only per-entity inputs are the collection name and entity class.
 */

class ProductRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Product>(db, CollectionNames.PRODUCTS, Product::class.java, "Product"),
    ProductRepository

class SectionRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Section>(db, CollectionNames.SECTIONS, Section::class.java, "Section"),
    SectionRepository

class CoverRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Cover>(db, CollectionNames.COVERS, Cover::class.java, "Cover"),
    CoverRepository

class CriticalIllnessListRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<CriticalIllnessList>(
        db, CollectionNames.CRITICAL_ILLNESS_LISTS, CriticalIllnessList::class.java, "CriticalIllnessList",
    ),
    CriticalIllnessListRepository

class AnnexureRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Annexure>(db, CollectionNames.ANNEXURES, Annexure::class.java, "Annexure"),
    AnnexureRepository

class AddOnRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<AddOn>(db, CollectionNames.ADD_ONS, AddOn::class.java, "AddOn"),
    AddOnRepository

class TenureRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Tenure>(db, CollectionNames.TENURES, Tenure::class.java, "Tenure"),
    TenureRepository

class PincodeZoneRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<PincodeZone>(
        db, CollectionNames.PINCODE_ZONES, PincodeZone::class.java, "PincodeZone",
    ),
    PincodeZoneRepository
