package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.actuarial.AuthorityLevel
import com.rate.sdk.catalog.model.actuarial.ClaimThreshold
import com.rate.sdk.catalog.model.actuarial.IBNRReserve
import com.rate.sdk.catalog.model.actuarial.IndividualLoadingFactor
import com.rate.sdk.catalog.model.actuarial.InflationMTF
import com.rate.sdk.catalog.model.actuarial.MedicalTrendFactor

/**
 * ACTUARIAL admin-CRUD repository PORTS (Dorian slide 10 masters) — one per config entity, each
 * extending the generic [ConfigRepository]. These drive RBAC approval gating, claims-cost
 * projection (medical trend + inflation), claims monitoring (thresholds), individual underwriting
 * loadings and reserving (IBNR). Same uniform CRUD as every other catalog repo.
 */
interface AuthorityLevelRepository : ConfigRepository<AuthorityLevel>
interface MedicalTrendFactorRepository : ConfigRepository<MedicalTrendFactor>
interface InflationMTFRepository : ConfigRepository<InflationMTF>
interface ClaimThresholdRepository : ConfigRepository<ClaimThreshold>
interface IndividualLoadingFactorRepository : ConfigRepository<IndividualLoadingFactor>
interface IBNRReserveRepository : ConfigRepository<IBNRReserve>
