package com.rate.server.database.repositories

import com.rate.domain.model.Plan
import com.rate.domain.model.PlanLifecycle
import com.rate.domain.model.PlanType
import com.rate.domain.repository.PlanRepository
import com.rate.server.database.tables.PlansTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class PlanRepositoryImpl : PlanRepository {

    override suspend fun getAllPlans(): List<Plan> = newSuspendedTransaction {
        PlansTable.selectAll().where { PlansTable.isActive eq true }.map { it.toPlan() }
    }

    override suspend fun getPlan(id: String): Plan? = newSuspendedTransaction {
        PlansTable.selectAll().where { PlansTable.id eq id }.firstOrNull()?.toPlan()
    }

    override suspend fun upsertPlan(plan: Plan): Unit = newSuspendedTransaction {
        val exists = PlansTable.selectAll().where { PlansTable.id eq plan.id }.count() > 0
        if (exists) {
            PlansTable.update({ PlansTable.id eq plan.id }) { plan.applyTo(it) }
        } else {
            PlansTable.insert {
                it[id] = plan.id
                plan.applyTo(it)
            }
        }
    }

    override suspend fun deletePlan(id: String): Unit = newSuspendedTransaction {
        PlansTable.update({ PlansTable.id eq id }) { it[isActive] = false }
    }

    private fun Plan.applyTo(stmt: UpdateBuilder<*>) {
        stmt[PlansTable.name]                 = name
        stmt[PlansTable.planType]             = planType.name
        stmt[PlansTable.description]          = description
        stmt[PlansTable.availableSumInsureds] = "[${availableSumInsureds.joinToString(",")}]"
        stmt[PlansTable.availableZones]       = "[${availableZones.joinToString(",") { "\"$it\"" }}]"
        stmt[PlansTable.availableFamilyTypes] = "[${availableFamilyTypes.joinToString(",") { "\"$it\"" }}]"
        stmt[PlansTable.minAge]               = minAge
        stmt[PlansTable.maxAge]               = maxAge
        stmt[PlansTable.isActive]             = isActive
        stmt[PlansTable.lifecycle]            = lifecycle.name
    }

    private fun ResultRow.toPlan() = Plan(
        id                   = this[PlansTable.id],
        name                 = this[PlansTable.name],
        planType             = PlanType.valueOf(this[PlansTable.planType]),
        description          = this[PlansTable.description],
        availableSumInsureds = this[PlansTable.availableSumInsureds].toJsonLongList(),
        availableZones       = this[PlansTable.availableZones].toJsonStringList(),
        availableFamilyTypes = this[PlansTable.availableFamilyTypes].toJsonStringList(),
        minAge               = this[PlansTable.minAge],
        maxAge               = this[PlansTable.maxAge],
        isActive             = this[PlansTable.isActive],
        lifecycle            = runCatching { PlanLifecycle.valueOf(this[PlansTable.lifecycle]) }
            .getOrDefault(PlanLifecycle.LIVE)
    )

    private fun String.toJsonLongList()   = trim('[',']').split(",").mapNotNull { it.trim().toLongOrNull() }
    private fun String.toJsonStringList() = trim('[',']').split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
}
