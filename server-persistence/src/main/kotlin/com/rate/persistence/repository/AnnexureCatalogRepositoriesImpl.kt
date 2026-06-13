package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog
import com.rate.sdk.catalog.repository.MedicalDeviceCatalogRepository
import com.rate.sdk.catalog.repository.SurgicalSublimitRepository
import com.rate.sdk.catalog.repository.VaccinationCatalogRepository

/**
 * Mongo actuals for the GROUP annexure-derived catalog admin-CRUD PORTS — the structured
 * entities split out of the EE annexure (surgical sublimit table, adult vaccination list,
 * medical-device list). Each is a one-liner over [GenericConfigRepository], bound to its own
 * distinct collection; the generic CRUD + draft/publish + optimistic concurrency does the work.
 */

class SurgicalSublimitRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<SurgicalSublimit>(
        db, CollectionNames.SURGICAL_SUBLIMITS, SurgicalSublimit::class.java, "SurgicalSublimit",
    ),
    SurgicalSublimitRepository

class VaccinationCatalogRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<VaccinationCatalog>(
        db, CollectionNames.VACCINATION_CATALOGS, VaccinationCatalog::class.java, "VaccinationCatalog",
    ),
    VaccinationCatalogRepository

class MedicalDeviceCatalogRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<MedicalDeviceCatalog>(
        db, CollectionNames.MEDICAL_DEVICE_CATALOGS, MedicalDeviceCatalog::class.java, "MedicalDeviceCatalog",
    ),
    MedicalDeviceCatalogRepository
