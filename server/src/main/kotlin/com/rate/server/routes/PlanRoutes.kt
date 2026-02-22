package com.rate.server.routes

import com.rate.domain.model.Plan
import com.rate.domain.repository.PlanRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.planRoutes(planRepo: PlanRepository) {
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
            call.respond(HttpStatusCode.OK, plan)
        }

        delete("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing plan id")
            planRepo.deletePlan(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
