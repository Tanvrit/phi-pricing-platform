package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.actuarial.AuthorityLevel
import com.rate.sdk.catalog.model.actuarial.ClaimThreshold
import com.rate.sdk.catalog.model.actuarial.IBNRReserve
import com.rate.sdk.catalog.model.actuarial.IndividualLoadingFactor
import com.rate.sdk.catalog.model.actuarial.InflationMTF
import com.rate.sdk.catalog.model.actuarial.MedicalTrendFactor
import com.rate.sdk.catalog.repository.AuthorityLevelRepository
import com.rate.sdk.catalog.repository.ClaimThresholdRepository
import com.rate.sdk.catalog.repository.IBNRReserveRepository
import com.rate.sdk.catalog.repository.IndividualLoadingFactorRepository
import com.rate.sdk.catalog.repository.InflationMTFRepository
import com.rate.sdk.catalog.repository.MedicalTrendFactorRepository

/**
 * Mongo collection-name constants for the ACTUARIAL admin-CRUD masters. lowerCamel plural per
 * entity; held here (not in shared CollectionNames) so this domain owns its own storage names.
 */
object ActuarialCollections {
    const val AUTHORITY_LEVELS = "authorityLevels"
    const val MEDICAL_TREND_FACTORS = "medicalTrendFactors"
    const val INFLATION_FACTORS = "inflationFactors"
    const val CLAIM_THRESHOLDS = "claimThresholds"
    const val LOADING_FACTORS = "loadingFactors"
    const val IBNR_RESERVES = "ibnrReserves"
}

/**
 * Mongo actuals for the ACTUARIAL admin-CRUD PORTS — each a one-liner over
 * [GenericConfigRepository], bound to its own distinct collection; the generic CRUD +
 * draft/publish + optimistic concurrency does the work.
 */

class AuthorityLevelRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<AuthorityLevel>(
        db, ActuarialCollections.AUTHORITY_LEVELS, AuthorityLevel::class.java, "AuthorityLevel",
    ),
    AuthorityLevelRepository

class MedicalTrendFactorRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<MedicalTrendFactor>(
        db, ActuarialCollections.MEDICAL_TREND_FACTORS, MedicalTrendFactor::class.java, "MedicalTrendFactor",
    ),
    MedicalTrendFactorRepository

class InflationMTFRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<InflationMTF>(
        db, ActuarialCollections.INFLATION_FACTORS, InflationMTF::class.java, "InflationMTF",
    ),
    InflationMTFRepository

class ClaimThresholdRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ClaimThreshold>(
        db, ActuarialCollections.CLAIM_THRESHOLDS, ClaimThreshold::class.java, "ClaimThreshold",
    ),
    ClaimThresholdRepository

class IndividualLoadingFactorRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<IndividualLoadingFactor>(
        db, ActuarialCollections.LOADING_FACTORS, IndividualLoadingFactor::class.java, "IndividualLoadingFactor",
    ),
    IndividualLoadingFactorRepository

class IBNRReserveRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<IBNRReserve>(
        db, ActuarialCollections.IBNR_RESERVES, IBNRReserve::class.java, "IBNRReserve",
    ),
    IBNRReserveRepository
