package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.demographics.AgeBand
import com.rate.sdk.catalog.model.demographics.FamilyRelation
import com.rate.sdk.catalog.model.demographics.Gender
import com.rate.sdk.catalog.model.demographics.MemberType
import com.rate.sdk.catalog.model.demographics.PaymentFrequency
import com.rate.sdk.catalog.repository.AgeBandRepository
import com.rate.sdk.catalog.repository.FamilyRelationRepository
import com.rate.sdk.catalog.repository.GenderRepository
import com.rate.sdk.catalog.repository.MemberTypeRepository
import com.rate.sdk.catalog.repository.PaymentFrequencyRepository

/** Mongo collection names for the DEMOGRAPHICS master entities (lowerCamel plural per entity). */
object DemographicsCollections {
    const val AGE_BANDS = "ageBands"
    const val GENDERS = "genders"
    const val PAYMENT_FREQUENCIES = "paymentFrequencies"
    const val MEMBER_TYPES = "memberTypes"
    const val FAMILY_RELATIONS = "familyRelations"
}

/**
 * Mongo actuals for the DEMOGRAPHICS admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does all
 * the work; the only per-entity inputs are the collection name and entity class.
 */

class AgeBandRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<AgeBand>(db, DemographicsCollections.AGE_BANDS, AgeBand::class.java, "AgeBand"),
    AgeBandRepository

class GenderRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Gender>(db, DemographicsCollections.GENDERS, Gender::class.java, "Gender"),
    GenderRepository

class PaymentFrequencyRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<PaymentFrequency>(
        db, DemographicsCollections.PAYMENT_FREQUENCIES, PaymentFrequency::class.java, "PaymentFrequency",
    ),
    PaymentFrequencyRepository

class MemberTypeRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<MemberType>(db, DemographicsCollections.MEMBER_TYPES, MemberType::class.java, "MemberType"),
    MemberTypeRepository

class FamilyRelationRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<FamilyRelation>(
        db, DemographicsCollections.FAMILY_RELATIONS, FamilyRelation::class.java, "FamilyRelation",
    ),
    FamilyRelationRepository
