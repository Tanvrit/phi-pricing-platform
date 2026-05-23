package com.rate.server.routes

import com.rate.domain.model.Plan
import com.rate.domain.repository.PlanRepository
import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Route.planRoutes(planRepo: PlanRepository, auditService: AuditEventService) {
    route("/api/plans") {
        get  { call.respond(planRepo.getAllPlans()) }

        get("/{id}") {
            val id   = call.parameters["id"] ?: throw IllegalArgumentException("Missing plan id")
            val plan = planRepo.getPlan(id) ?: throw NoSuchElementException("Plan not found: $id")
            call.respond(plan)
        }

        post {
            if (!requireScope("plans.write")) return@post
            val plan = call.receive<Plan>()
            // Snapshot the previous state BEFORE upserting so we can compute a field-level
            // diff for the audit log. `null` means this is a create.
            val previous = planRepo.getPlan(plan.id)
            planRepo.upsertPlan(plan)
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "plan.upserted",
                resourceType = "plan",
                resourceId = plan.id,
                payload = buildPlanDiff(previous, plan),
                actor = actor,
                requestId = rid
            )
            call.respond(HttpStatusCode.OK, plan)
        }

        delete("/{id}") {
            if (!requireScope("plans.delete")) return@delete
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

/** Json formatter used to encode collection-shaped fields for the diff payload. */
private val diffJson = Json { encodeDefaults = true }

/**
 * Build a structured audit payload describing how [next] differs from [prev].
 *
 * Shape:
 * ```
 * {
 *   "planId": "<id>",
 *   "isCreate": true|false,
 *   "changes": {
 *     "<field>": { "old": <json>, "new": <json> },   // updates
 *     "__create": true                                // creates only
 *   }
 * }
 * ```
 *
 * For sets (`allowedCoverIds`) we normalize order by sorting before encoding,
 * so iteration-order differences in equal collections don't manufacture a fake
 * diff. Lists are compared as-is — order is semantic for the list-typed fields.
 */
private fun buildPlanDiff(prev: Plan?, next: Plan): JsonObject = buildJsonObject {
    put("planId", JsonPrimitive(next.id))
    put("isCreate", JsonPrimitive(prev == null))
    putJsonObject("changes") {
        if (prev == null) {
            // Marker so the UI can render "Created" without scanning every field.
            put("__create", JsonPrimitive(true))
            return@putJsonObject
        }
        scalarChange("name", prev.name, next.name) { JsonPrimitive(it) }
        scalarChange("planType", prev.planType.name, next.planType.name) { JsonPrimitive(it) }
        scalarChange("description", prev.description, next.description) { JsonPrimitive(it) }
        listChange("availableSumInsureds", prev.availableSumInsureds, next.availableSumInsureds)
        listChange("availableZones", prev.availableZones, next.availableZones)
        listChange("availableFamilyTypes", prev.availableFamilyTypes, next.availableFamilyTypes)
        setChange("allowedCoverIds", prev.allowedCoverIds, next.allowedCoverIds)
        scalarChange("isActive", prev.isActive, next.isActive) { JsonPrimitive(it) }
        scalarChange("lifecycle", prev.lifecycle.name, next.lifecycle.name) { JsonPrimitive(it) }
        scalarChange("minAge", prev.minAge, next.minAge) { JsonPrimitive(it) }
        scalarChange("maxAge", prev.maxAge, next.maxAge) { JsonPrimitive(it) }
        scalarChange("maxDiscountCap", prev.maxDiscountCap, next.maxDiscountCap) { JsonPrimitive(it) }
        scalarChange("gstRate", prev.gstRate, next.gstRate) { JsonPrimitive(it) }
        scalarChange("rateTableId", prev.rateTableId, next.rateTableId) { JsonPrimitive(it) }
    }
}

/** Add an `{old,new}` entry under [field] iff the values differ. */
private inline fun <T> JsonObjectBuilder.scalarChange(
    field: String,
    old: T,
    new: T,
    crossinline encode: (T) -> JsonElement
) {
    if (old == new) return
    putJsonObject(field) {
        put("old", encode(old))
        put("new", encode(new))
    }
}

/**
 * Order-preserving list diff: lists are ordered semantically (e.g. zones are
 * declared in a deliberate sequence), so we compare them as-is and emit the
 * encoded list verbatim.
 */
private inline fun <reified T> JsonObjectBuilder.listChange(
    field: String,
    old: List<T>,
    new: List<T>
) {
    if (old == new) return
    putJsonObject(field) {
        put("old", diffJson.encodeToJsonElement(old))
        put("new", diffJson.encodeToJsonElement(new))
    }
}

/**
 * Order-insensitive set diff: `allowedCoverIds` is a `Set` so iteration order
 * is undefined across repo backends. We compare via `Set` equality and emit a
 * sorted list when changed, so the rendered diff is stable and human-readable.
 */
private fun JsonObjectBuilder.setChange(
    field: String,
    old: Set<String>,
    new: Set<String>
) {
    if (old == new) return
    putJsonObject(field) {
        put("old", diffJson.encodeToJsonElement(old.sorted()))
        put("new", diffJson.encodeToJsonElement(new.sorted()))
    }
}
