package com.rate.server.routes

import com.rate.domain.engine.PricingEngine
import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import com.rate.domain.repository.QuoteRepository
import com.rate.domain.repository.RateDataProvider
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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
    val tenure: String
)

fun Route.quoteRoutes(rateProvider: RateDataProvider, quoteRepo: QuoteRepository) {
    val engine = PricingEngine(rateProvider)

    route("/api/quotes") {
        // Calculate without saving
        post("/calculate") {
            val request = call.receive<QuoteRequest>()
            call.respond(engine.calculate(request))
        }

        // Calculate and save
        post {
            val request = call.receive<QuoteRequest>()
            val result  = engine.calculate(request)
            if (result.isValid) {
                val id = quoteRepo.saveQuote(request, result)
                call.respond(HttpStatusCode.Created, SavedQuoteResponse(id = id, result = result))
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity, result)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing quote id")
            val (req, res) = quoteRepo.getQuote(id) ?: throw NoSuchElementException("Quote not found: $id")
            call.respond(QuoteDetailResponse(request = req, result = res))
        }

        get {
            val limit  = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val quotes = quoteRepo.listQuotes(limit)
            call.respond(quotes.map { (id, req) ->
                QuoteListItem(
                    id = id,
                    planId = req.planId,
                    age = req.primaryAge,
                    sumInsured = req.sumInsured,
                    familyType = req.familyType,
                    zone = req.zone,
                    tenure = req.tenure.label
                )
            })
        }
    }
}
