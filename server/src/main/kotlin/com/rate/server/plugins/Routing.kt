package com.rate.server.plugins

import com.rate.server.database.repositories.*
import com.rate.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val quoteRepo = QuoteRepositoryImpl()
    val planRepo  = PlanRepositoryImpl()

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "rate-calculator"))
        }
        quoteRoutes(RateDataProviderImpl(), quoteRepo)
        planRoutes(planRepo)
        coverRoutes()
        importRoutes()
        buyOnlineRoutes()
    }
}
