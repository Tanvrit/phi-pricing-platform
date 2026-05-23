package com.rate.server.routes

import com.rate.domain.engine.PricingEngine
import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import com.rate.domain.repository.QuoteRepository
import com.rate.domain.repository.RateDataProvider
import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.metrics.Metrics
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import com.rate.server.plugins.withIdempotency
import com.rate.server.security.IdempotencyService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SavedQuoteResponse(val id: String, val result: QuoteResult)

@Serializable
data class QuoteDetailResponse(val request: QuoteRequest, val result: QuoteResult)

@Serializable
data class QuoteListItem(
    val id: String,
    val planId: String,
    val age: Int,
    val sumInsured: Long,
    val familyType: String,
    val zone: String,
    val tenure: String,
    val createdAt: String,            // ISO-8601 instant; UI parses for grouping/sort
    val totalIncludingGst: Double,    // headline figure (post-GST). 0.0 for INVALID quotes.
    val isValid: Boolean = true
)

private val routeJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

fun Route.quoteRoutes(
    rateProvider: RateDataProvider,
    quoteRepo: QuoteRepository,
    auditService: AuditEventService,
    idempotencyService: IdempotencyService
) {
    val engine = PricingEngine(rateProvider)

    route("/api/quotes") {
        // Calculate without saving
        post("/calculate") {
            val request = call.receive<QuoteRequest>()
            val result  = engine.calculate(request)
            Metrics.recordQuoteCalculation(request.planId, result.isValid)
            // Audit attribution for anonymous calculations — every calc shows up in
            // the ledger even when not saved. auditService.record swallows its own
            // errors so this never delays the response.
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "quote.calculated",
                resourceType = "quote",
                resourceId = result.requestId,
                payload = JsonObject(mapOf(
                    "planId" to JsonPrimitive(request.planId),
                    "primaryAge" to JsonPrimitive(request.primaryAge),
                    "sumInsured" to JsonPrimitive(request.sumInsured),
                    "isValid" to JsonPrimitive(result.isValid),
                    "totalIncludingGst" to JsonPrimitive(result.totalIncludingGst)
                )),
                actor = actor,
                requestId = rid
            )
            call.respond(result)
        }

        // Calculate and save (idempotent on Idempotency-Key header)
        post {
            withIdempotency(idempotencyService, routeKey = "POST /api/quotes") { rawBody ->
                val request = routeJson.decodeFromString<QuoteRequest>(rawBody)
                val result  = engine.calculate(request)
                Metrics.recordQuoteCalculation(request.planId, result.isValid)
                if (result.isValid) {
                    val id = quoteRepo.saveQuote(request, result)
                    val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
                    val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                        ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
                    auditService.record(
                        action = "quote.created",
                        resourceType = "quote",
                        resourceId = id,
                        payload = JsonObject(mapOf(
                            "planId" to JsonPrimitive(request.planId),
                            "primaryAge" to JsonPrimitive(request.primaryAge),
                            "sumInsured" to JsonPrimitive(request.sumInsured),
                            "familyType" to JsonPrimitive(request.familyType),
                            "zone" to JsonPrimitive(request.zone),
                            "tenure" to JsonPrimitive(request.tenure.label),
                            "finalPremium" to JsonPrimitive(result.totalAfterDiscount)
                        )),
                        actor = actor,
                        requestId = rid
                    )
                    HttpStatusCode.Created to routeJson.encodeToString(SavedQuoteResponse(id, result))
                } else {
                    HttpStatusCode.UnprocessableEntity to routeJson.encodeToString(result)
                }
            }
        }

        // Per-plan history (operator-side drill-down on the Configurator dialog).
        // TODO(perf): push the planId filter into a parameterised DB query on
        // QuotesTable; the in-memory filter over the recent 500 is adequate for
        // current traffic but won't scale past 5–10× current quote volume.
        get("/by-plan/{planId}") {
            val planId = call.parameters["planId"] ?: throw IllegalArgumentException("Missing planId")
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val all = quoteRepo.listQuoteSummaries(limit = 500)
            val filtered = all.filter { it.request.planId == planId }.take(limit)
            call.respond(filtered.map { summary ->
                QuoteListItem(
                    id                = summary.id,
                    planId            = summary.request.planId,
                    age               = summary.request.primaryAge,
                    sumInsured        = summary.request.sumInsured,
                    familyType        = summary.request.familyType,
                    zone              = summary.request.zone,
                    tenure            = summary.request.tenure.label,
                    createdAt         = summary.createdAt.toString(),
                    totalIncludingGst = summary.totalIncludingGst,
                    isValid           = summary.isValid
                )
            })
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing quote id")
            val (req, res) = quoteRepo.getQuote(id) ?: throw NoSuchElementException("Quote not found: $id")
            call.respond(QuoteDetailResponse(request = req, result = res))
        }

        get {
            val limit  = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val quotes = quoteRepo.listQuoteSummaries(limit)
            call.respond(quotes.map { summary ->
                QuoteListItem(
                    id                = summary.id,
                    planId            = summary.request.planId,
                    age               = summary.request.primaryAge,
                    sumInsured        = summary.request.sumInsured,
                    familyType        = summary.request.familyType,
                    zone              = summary.request.zone,
                    tenure            = summary.request.tenure.label,
                    createdAt         = summary.createdAt.toString(),
                    totalIncludingGst = summary.totalIncludingGst,
                    isValid           = summary.isValid
                )
            })
        }
    }
}
