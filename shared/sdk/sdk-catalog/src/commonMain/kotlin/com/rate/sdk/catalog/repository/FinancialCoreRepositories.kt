package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.financialcore.DiscountConfig
import com.rate.sdk.catalog.model.financialcore.InvestmentIncomeConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostConfig
import com.rate.sdk.catalog.model.financialcore.VariableExpenseConfig

/**
 * FINANCIAL-CORE admin-CRUD repository PORTS (Dorian slide 09) — discount levers, variable expense
 * ratios, per-policy costs and investment-income assumptions. Each extends the generic
 * [ConfigRepository] so the operator console lists/searches/creates/updates/soft-deletes/restores/
 * publishes every financial master with identical wiring. Mongo-backed actuals live in
 * server-persistence; these are typed-marker interfaces (no extra methods beyond the generic CRUD)
 * so DI can bind a distinct collection per entity while the handler stays uniform.
 */
interface DiscountConfigRepository : ConfigRepository<DiscountConfig>
interface VariableExpenseConfigRepository : ConfigRepository<VariableExpenseConfig>
interface PolicyCostConfigRepository : ConfigRepository<PolicyCostConfig>
interface InvestmentIncomeConfigRepository : ConfigRepository<InvestmentIncomeConfig>
