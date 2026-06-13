package com.rate.server.routes

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.server.metrics.Metrics
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get

@Serializable
internal data class HealthResponse(val status: String, val service: String, val check: String? = null)

/**
 * Liveness + readiness + Prometheus scrape. RELOCATED from the monolith; readiness now confirms
 * MongoDB connectivity (a `{ping:1}` admin command) instead of a JDBC `SELECT 1`. K8s gates traffic
 * on `/health/ready`.
 */
fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok", service = "rate-calculator"))
    }
    get("/health/live") {
        call.respond(HealthResponse(status = "ok", service = "rate-calculator", check = "live"))
    }
    get("/health/ready") {
        val db: MongoDatabase = call.application.get()
        try {
            // Force a server round-trip — listing collection names confirms connectivity + auth
            // without depending on any particular collection existing.
            db.listCollectionNames().firstOrNull()
            call.respond(HealthResponse(status = "ok", service = "rate-calculator", check = "db"))
        } catch (t: Throwable) {
            call.application.environment.log.warn("readiness probe failed: ${t.message}")
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = "down", service = "rate-calculator", check = "db: ${t.message ?: "unreachable"}"),
            )
        }
    }
}

/** Prometheus scrape target. */
fun Route.metricsRoutes() {
    get("/metrics") {
        call.respondText(Metrics.render(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
    }
}
