package com.rate.server.routes

import com.rate.domain.model.Plan
import com.rate.domain.repository.PlanRepository
import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Route.planRoutes(planRepo: PlanRepository, auditService: AuditEventService) {
    route("/api/plans") {
        get  { call.respond(planRepo.getAllPlans()) }

        get("/{id}") {
            val id   = call.parameters["id"] ?: throw IllegalArgumentException("Missing plan id")
            val plan = planRepo.getPlan(id) ?: throw NoSuchElementException("Plan not found: $id")
            call.respond(plan)
        }

        post {
            val plan = call.receive<Plan>()
            planRepo.upsertPlan(plan)
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "plan.upserted",
                resourceType = "plan",
                resourceId = plan.id,
                payload = JsonObject(mapOf(
                    "name" to JsonPrimitive(plan.name),
                    "planType" to JsonPrimitive(plan.planType.name),
                    "isActive" to JsonPrimitive(plan.isActive)
                )),
                actor = actor,
                requestId = rid
            )
            call.respond(HttpStatusCode.OK, plan)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing plan id")
            planRepo.deletePlan(id)
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "plan.deleted",
                resourceType = "plan",
                resourceId = id,
                actor = actor,
                requestId = rid
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
