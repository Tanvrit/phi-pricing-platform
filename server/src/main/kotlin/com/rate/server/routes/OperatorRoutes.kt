package com.rate.server.routes

import com.rate.server.auth.Operator
import com.rate.server.auth.OperatorsStore
import com.rate.server.auth.requireScope
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Operators allowlist CRUD. GET is open so the Settings surface can render
 * the list during the bootstrap window; mutations require the
 * `operators.write` scope (which the first admin grants themselves while
 * the store is still empty — see [OperatorsStore] for the permissive-mode
 * note).
 */
fun Route.operatorRoutes() {
    route("/api/operators") {
        get { call.respond(OperatorsStore.load().operators) }

        post {
            if (!requireScope("operators.write")) return@post
            val op = call.receive<Operator>()
            OperatorsStore.upsert(op.copy(addedAtIso = Clock.System.now().toString()))
            call.respond(HttpStatusCode.OK, op)
        }

        delete("/{identity}") {
            if (!requireScope("operators.write")) return@delete
            val id = call.parameters["identity"]
                ?: throw IllegalArgumentException("Missing identity")
            OperatorsStore.remove(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
