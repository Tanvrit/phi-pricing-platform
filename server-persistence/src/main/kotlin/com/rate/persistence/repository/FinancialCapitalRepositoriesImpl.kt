package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.financialcapital.CostOfCapitalMatrix
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceTreaty
import com.rate.sdk.catalog.model.financialcapital.SumInsuredTier
import com.rate.sdk.catalog.repository.CostOfCapitalMatrixRepository
import com.rate.sdk.catalog.repository.ReinsuranceTreatyRepository
import com.rate.sdk.catalog.repository.SumInsuredTierRepository

/** Mongo collection names for the financial-capital domain (lowerCamel plural per entity). */
object FinancialCapitalCollections {
    const val SUM_INSURED_TIERS = "sumInsuredTiers"
    const val REINSURANCE_TREATIES = "reinsuranceTreaties"
    const val COST_OF_CAPITAL_MATRIX = "costOfCapitalMatrix"
}

/**
 * Mongo actuals for the financial-capital admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — generic CRUD + draft/publish + optimistic concurrency do all the work.
 */

class SumInsuredTierRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<SumInsuredTier>(
        db, FinancialCapitalCollections.SUM_INSURED_TIERS, SumInsuredTier::class.java, "SumInsuredTier",
    ),
    SumInsuredTierRepository

class ReinsuranceTreatyRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<ReinsuranceTreaty>(
        db, FinancialCapitalCollections.REINSURANCE_TREATIES, ReinsuranceTreaty::class.java, "ReinsuranceTreaty",
    ),
    ReinsuranceTreatyRepository

class CostOfCapitalMatrixRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<CostOfCapitalMatrix>(
        db, FinancialCapitalCollections.COST_OF_CAPITAL_MATRIX, CostOfCapitalMatrix::class.java, "CostOfCapitalMatrix",
    ),
    CostOfCapitalMatrixRepository
