package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.demographics.AgeBand
import com.rate.sdk.catalog.model.demographics.FamilyRelation
import com.rate.sdk.catalog.model.demographics.Gender
import com.rate.sdk.catalog.model.demographics.MemberType
import com.rate.sdk.catalog.model.demographics.PaymentFrequency

/**
 * DEMOGRAPHICS master-data repository PORTS — one per admin-CRUD entity, each extending the generic
 * [ConfigRepository] so the operator console lists/searches/creates/updates/soft-deletes/restores/
 * publishes every demographics master with identical wiring. Mongo-backed actuals live in
 * server-persistence (DemographicsRepositoriesImpl). These are typed-marker interfaces (no extra
 * methods beyond the generic CRUD) so DI can bind a distinct collection per entity while the
 * handler stays uniform.
 */
interface AgeBandRepository : ConfigRepository<AgeBand>
interface GenderRepository : ConfigRepository<Gender>
interface PaymentFrequencyRepository : ConfigRepository<PaymentFrequency>
interface MemberTypeRepository : ConfigRepository<MemberType>
interface FamilyRelationRepository : ConfigRepository<FamilyRelation>
