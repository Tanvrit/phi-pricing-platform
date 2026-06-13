package com.rate.sdk.catalog.repository

import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.GroupSizeMatrix
import com.rate.sdk.catalog.model.rfq.Rfq

/**
 * RFQ (SALES) master-data repository PORTS — one per admin-CRUD entity, each extending the generic
 * [ConfigRepository] so the operator console lists/searches/creates/updates/soft-deletes/restores/
 * publishes every RFQ master with identical wiring. Mongo-backed actuals live in server-persistence
 * (RfqRepositoriesImpl). These are typed-marker interfaces (no extra methods beyond the generic
 * CRUD) so DI can bind a distinct collection per entity while the handler stays uniform.
 */
interface RfqRepository : ConfigRepository<Rfq>
interface ExperienceRatingRepository : ConfigRepository<ExperienceRating>
interface GroupSizeMatrixRepository : ConfigRepository<GroupSizeMatrix>
