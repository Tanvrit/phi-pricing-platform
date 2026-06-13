package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.underwriting.DiseaseMapping
import com.rate.sdk.catalog.model.underwriting.RatingParameter
import com.rate.sdk.catalog.model.underwriting.TreatmentCategory

/**
 * UNDERWRITING admin-CRUD repository PORTS — one per entity, each a typed-marker over the generic
 * [ConfigRepository] so the console lists/searches/creates/updates/soft-deletes/restores/publishes
 * every underwriting master with identical wiring. Mongo-backed actuals live in server-persistence.
 */
interface TreatmentCategoryRepository : ConfigRepository<TreatmentCategory>
interface DiseaseMappingRepository : ConfigRepository<DiseaseMapping>
interface RatingParameterRepository : ConfigRepository<RatingParameter>
