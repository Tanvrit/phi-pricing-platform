package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.financialcore.DiscountConfig
import com.rate.sdk.catalog.model.financialcore.InvestmentIncomeConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostConfig
import com.rate.sdk.catalog.model.financialcore.VariableExpenseConfig
import com.rate.sdk.catalog.repository.DiscountConfigRepository
import com.rate.sdk.catalog.repository.InvestmentIncomeConfigRepository
import com.rate.sdk.catalog.repository.PolicyCostConfigRepository
import com.rate.sdk.catalog.repository.VariableExpenseConfigRepository

/**
 * Mongo actuals for the FINANCIAL-CORE admin-CRUD PORTS. Each is a one-liner over
 * [GenericConfigRepository] — the generic CRUD + draft/publish + optimistic concurrency does all
 * the work; the only per-entity inputs are the collection name and the entity class.
 */

object FinancialCoreCollections {
    const val DISCOUNT_CONFIGS = "discountConfigs"
    const val VARIABLE_EXPENSE_CONFIGS = "variableExpenseConfigs"
    const val POLICY_COST_CONFIGS = "policyCostConfigs"
    const val INVESTMENT_INCOME_CONFIGS = "investmentIncomeConfigs"
}

class DiscountConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<DiscountConfig>(
        db, FinancialCoreCollections.DISCOUNT_CONFIGS, DiscountConfig::class.java, "DiscountConfig",
    ),
    DiscountConfigRepository

class VariableExpenseConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<VariableExpenseConfig>(
        db, FinancialCoreCollections.VARIABLE_EXPENSE_CONFIGS, VariableExpenseConfig::class.java, "VariableExpenseConfig",
    ),
    VariableExpenseConfigRepository

class PolicyCostConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<PolicyCostConfig>(
        db, FinancialCoreCollections.POLICY_COST_CONFIGS, PolicyCostConfig::class.java, "PolicyCostConfig",
    ),
    PolicyCostConfigRepository

class InvestmentIncomeConfigRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<InvestmentIncomeConfig>(
        db, FinancialCoreCollections.INVESTMENT_INCOME_CONFIGS, InvestmentIncomeConfig::class.java, "InvestmentIncomeConfig",
    ),
    InvestmentIncomeConfigRepository
