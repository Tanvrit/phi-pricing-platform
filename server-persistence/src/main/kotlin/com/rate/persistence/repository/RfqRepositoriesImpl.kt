package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.GroupSizeMatrix
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.repository.ExperienceRatingRepository
import com.rate.sdk.catalog.repository.GroupSizeMatrixRepository
import com.rate.sdk.catalog.repository.RfqRepository

/** Mongo collection names for the RFQ (SALES) master entities (lowerCamel plural per entity). */
object RfqCollections {
    const val RFQS = "rfqs"
    const val EXPERIENCE_RATINGS = "experienceRatings"
    const val GROUP_SIZE_MATRICES = "groupSizeMatrices"
}

/**
 * Mongo actuals for the RFQ admin-CRUD PORTS. Each is a one-liner over [GenericConfigRepository] —
 * the generic CRUD + draft/publish + optimistic concurrency does all the work; the only per-entity
 * inputs are the collection name and entity class.
 */

class RfqRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Rfq>(db, RfqCollections.RFQS, Rfq::class.java, "Rfq"),
    RfqRepository

class ExperienceRatingRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ExperienceRating>(
        db, RfqCollections.EXPERIENCE_RATINGS, ExperienceRating::class.java, "ExperienceRating",
    ),
    ExperienceRatingRepository

class GroupSizeMatrixRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<GroupSizeMatrix>(
        db, RfqCollections.GROUP_SIZE_MATRICES, GroupSizeMatrix::class.java, "GroupSizeMatrix",
    ),
    GroupSizeMatrixRepository
