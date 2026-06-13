package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.financialcapital.CostOfCapitalMatrix
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceTreaty
import com.rate.sdk.catalog.model.financialcapital.SumInsuredTier

/**
 * FINANCIAL-CAPITAL admin-CRUD PORTS — sum-insured tiers, reinsurance treaties and the
 * cost-of-capital matrix (Dorian slides 09/10). Each is a typed-marker [ConfigRepository] so DI
 * binds a distinct Mongo collection per entity while the generic CRUD / draft-publish / optimistic
 * concurrency stays uniform.
 */
interface SumInsuredTierRepository : ConfigRepository<SumInsuredTier>
interface ReinsuranceTreatyRepository : ConfigRepository<ReinsuranceTreaty>
interface CostOfCapitalMatrixRepository : ConfigRepository<CostOfCapitalMatrix>
