package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.underwriting.DiseaseMapping
import com.rate.sdk.catalog.model.underwriting.RatingParameter
import com.rate.sdk.catalog.model.underwriting.TreatmentCategory
import com.rate.sdk.catalog.repository.DiseaseMappingRepository
import com.rate.sdk.catalog.repository.RatingParameterRepository
import com.rate.sdk.catalog.repository.TreatmentCategoryRepository

/**
 * Mongo actuals for the UNDERWRITING admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does all
 * the work; the only per-entity inputs are the collection name and entity class.
 */
object UnderwritingCollections {
    const val TREATMENT_CATEGORIES = "treatmentCategories"
    const val DISEASE_MAPPINGS = "diseaseMappings"
    const val RATING_PARAMETERS = "ratingParameters"
}

class TreatmentCategoryRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<TreatmentCategory>(
        db, UnderwritingCollections.TREATMENT_CATEGORIES, TreatmentCategory::class.java, "TreatmentCategory",
    ),
    TreatmentCategoryRepository

class DiseaseMappingRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<DiseaseMapping>(
        db, UnderwritingCollections.DISEASE_MAPPINGS, DiseaseMapping::class.java, "DiseaseMapping",
    ),
    DiseaseMappingRepository

class RatingParameterRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<RatingParameter>(
        db, UnderwritingCollections.RATING_PARAMETERS, RatingParameter::class.java, "RatingParameter",
    ),
    RatingParameterRepository
