package com.rate.server.routes

import com.rate.core.base.json.AppJson
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.auditActor
import com.rate.server.metrics.Metrics
import com.rate.server.plugins.withIdempotency
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.sdk.audit.repository.IdempotencyStore
import com.rate.sdk.quoting.handler.QuoteHandler
import com.rate.core.base.error.AppResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    val createdAt: String,
    val totalIncludingGst: Double,
    val isValid: Boolean = true,
)

/**
 * Quote endpoints — backward-compatible paths preserved from the monolith
 * (`/api/quotes/calculate`, `POST /api/quotes`, `GET /api/quotes/{id}`, list). RELOCATED off the
 * monolith's inline `PricingEngine` wiring onto sdk-quoting's [QuoteHandler], which prices via the
 * injected `RatingPort` (never a concrete engine) and persists via the core `QuoteRepository`.
 */
fun Route.quoteRoutes(
    quotes: QuoteHandler,
    audit: ServerAuditService,
    idempotency: IdempotencyStore,
    hasher: HmacIdempotencyHasher,
) {
    val json = AppJson.json

    route("/api/quotes") {
        // ── Calculate without saving ─────────────────────────────────────────
        post("/calculate") {
            val request = call.receive<QuoteRequest>()
            val result = quotes.calculate(request)
            Metrics.recordQuoteCalculation(request.planId, result.isValid)
            audit.record(
                action = "quote.calculated",
                entity = "quote",
                entityId = result.requestId.ifBlank { null },
                payloadJson = buildJsonObject {
                    put("planId", request.planId)
                    put("primaryAge", request.primaryAge)
                    put("sumInsured", request.sumInsured)
                    put("isValid", result.isValid)
                    put("totalIncludingGst", result.totalIncludingGst)
                }.toString(),
                actor = call.auditActor(),
            )
            call.respond(result)
        }

        // ── Calculate + save (idempotent on Idempotency-Key) ─────────────────
        post {
            withIdempotency(idempotency, hasher, scope = "POST /api/quotes") { rawBody ->
                val request = json.decodeFromString(QuoteRequest.serializer(), rawBody)
                Metrics.recordQuoteCalculation(request.planId, true)
                when (val saved = quotes.calculateAndSave(request, call.auditActor().subject)) {
                    is AppResult.Ok -> {
                        val v = saved.value
                        audit.record(
                            action = "quote.created",
                            entity = "quote",
                            entityId = v.id,
                            payloadJson = buildJsonObject {
                                put("planId", request.planId)
                                put("primaryAge", request.primaryAge)
                                put("sumInsured", request.sumInsured)
                                put("familyType", request.familyType)
                                put("zone", request.zone)
                                put("tenure", request.tenure.label)
                                put("finalPremium", v.result.totalAfterDiscount)
                            }.toString(),
                            actor = call.auditActor(),
                        )
                        HttpStatusCode.Created to
                            json.encodeToString(SavedQuoteResponse.serializer(), SavedQuoteResponse(v.id, v.result))
                    }
                    is AppResult.Err -> {
                        Metrics.recordQuoteCalculation(request.planId, false)
                        val (status, code, details) = com.rate.server.plugins.mapDomainError(saved.error)
                        status to json.encodeToString(
                            com.rate.server.plugins.ErrorResponse.serializer(),
                            com.rate.server.plugins.ErrorResponse(code, saved.error.msg, details = details),
                        )
                    }
                }
            }
        }

        // ── List (newest-first paged) ────────────────────────────────────────
        get {
            val page = quotes.list(call.pageRequest())
            call.respond(page.items.map { it.toListItem() })
        }

        // ── Query (paged summaries for dashboards; client POSTs a PageRequest body) ──
        post("/query") {
            val req = call.receive<com.rate.core.base.model.PageRequest>()
            call.respond(quotes.list(req))
        }

        // ── Per-plan history (operator drill-down) ───────────────────────────
        get("/by-plan/{planId}") {
            val planId = call.requireParam("planId")
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val all = quotes.list(com.rate.core.base.model.PageRequest(page = 0, size = 500))
            val filtered = all.items.filter { it.request.planId == planId }.take(limit)
            call.respond(filtered.map { it.toListItem() })
        }

        // ── Get one ──────────────────────────────────────────────────────────
        get("/{id}") {
            val id = call.requireParam("id")
            when (val r = quotes.get(id)) {
                is AppResult.Ok -> call.respond(QuoteDetailResponse(r.value.request, r.value.result))
                is AppResult.Err -> r.orThrow() // throws DomainException → 404
            }
        }
    }
}

private fun com.rate.core.rating.ports.QuoteSummary.toListItem(): QuoteListItem = QuoteListItem(
    id = id,
    planId = request.planId,
    age = request.primaryAge,
    sumInsured = request.sumInsured,
    familyType = request.familyType,
    zone = request.zone,
    tenure = request.tenure.label,
    createdAt = createdAt.toString(),
    totalIncludingGst = totalIncludingGst,
    isValid = isValid,
)
