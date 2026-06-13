package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.Vendor
import com.rate.sdk.catalog.repository.VendorRepository

/**
 * Mongo actual for the GLOBAL [VendorRepository] PORT — vendors are NOT productLine-scoped
 * (shared across RETAIL/GROUP), so they live in their own ["vendors"][CollectionNames.VENDORS]
 * collection. Like every catalog repo it is a one-liner over [GenericConfigRepository]; the
 * generic CRUD + draft/publish + optimistic concurrency does all the work.
 */
class VendorRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Vendor>(db, CollectionNames.VENDORS, Vendor::class.java, "Vendor"),
    VendorRepository
