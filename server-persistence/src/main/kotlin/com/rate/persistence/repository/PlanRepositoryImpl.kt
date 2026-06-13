package com.rate.persistence.repository

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.model.Plan
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [Plan] persistence. Plan is BOTH the rating anchor and an admin-CRUD
 * config entity, so this impl serves the two PORTS at once:
 *  - the core [PlanRepository] (the engine/quoting read+write surface: getAllPlans / getPlan /
 *    upsertPlan / deletePlan);
 *  - the generic admin-CRUD [com.rate.core.base.repository.ConfigRepository]<Plan> via
 *    [GenericConfigRepository] (list / create / update(expectedV) / softDelete / publishDraft …).
 *
 * Binding one object to both interfaces keeps the plan collection single-sourced.
 */
class PlanRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Plan>(db, CollectionNames.PLANS, Plan::class.java, "Plan"),
    PlanRepository {

    override suspend fun getAllPlans(): List<Plan> =
        collection.find(notDeleted).toList()

    override suspend fun getPlan(id: String): Plan? = findById(id)

    override suspend fun upsertPlan(plan: Plan) {
        upsertEntity(plan)
    }

    override suspend fun deletePlan(id: String) {
        softDelete(id)
    }
}
