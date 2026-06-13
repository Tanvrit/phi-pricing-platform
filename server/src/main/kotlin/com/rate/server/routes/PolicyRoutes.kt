package com.rate.server.routes

import com.rate.core.auth.rbac.Scope
import com.rate.core.regulatory.Irdai
import com.rate.server.auth.requireScope
import com.rate.sdk.policy.handler.RenewalEngine
import com.rate.sdk.policy.repository.ClaimRepository
import com.rate.sdk.policy.repository.PolicyRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class RenewalErrorResponse(val errorCode: String, val message: String)

/**
 * Policy + renewal + claim endpoints. RELOCATED/NEW for the re-arch (the monolith had no policy
 * lifecycle surface) — mounts sdk-policy's [RenewalEngine] over the core renewal-rate + plan ports
 * and the [PolicyRepository] / [ClaimRepository]. All reads are scope-gated.
 *
 *   GET  /api/policies               — paged list (proposal.read.all)
 *   GET  /api/policies/{id}          — single
 *   GET  /api/policies/by-mobile/{m} — holder's policies
 *   POST /api/policies/{id}/renewal-illustration  — renewal quote (?dueDate=ISO&years=)
 *   GET  /api/policies/{id}/claims   — claims for a policy
 */
fun Route.policyRoutes(
    policies: PolicyRepository,
    claims: ClaimRepository,
    renewal: RenewalEngine,
) {
    route("/api/policies") {
        get {
            if (!requireScope(Scope.PROPOSAL_READ_ALL)) return@get
            call.respond(policies.list(call.pageRequest()))
        }

        get("/{id}") {
            if (!requireScope(Scope.PROPOSAL_READ_ALL)) return@get
            val id = call.requireParam("id")
            val policy = policies.get(id) ?: throw NoSuchElementException("Policy '$id' not found")
            call.respond(policy)
        }

        get("/by-mobile/{mobile}") {
            if (!requireScope(Scope.PROPOSAL_READ_ALL)) return@get
            call.respond(policies.getByMobile(call.requireParam("mobile")))
        }

        get("/{id}/claims") {
            if (!requireScope(Scope.PROPOSAL_READ_ALL)) return@get
            call.respond(claims.listByPolicy(call.requireParam("id")))
        }

        // ── Renewal illustration ─────────────────────────────────────────────
        post("/{id}/renewal-illustration") {
            if (!requireScope(Scope.QUOTE_RUN)) return@post
            val id = call.requireParam("id")
            val policy = policies.get(id) ?: throw NoSuchElementException("Policy '$id' not found")
            val dueDate = call.request.queryParameters["dueDate"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: policy.expiresAt
            val years = (call.request.queryParameters["years"]?.toIntOrNull() ?: 5).coerceIn(1, 5)
            when (val r = renewal.quote(
                policy = policy,
                dueDate = dueDate,
                gracePeriodDays = Irdai.GRACE_PERIOD_DAYS_ANNUAL,
                illustrationYears = years,
            )) {
                is RenewalEngine.Result.Success -> call.respond(r.quote)
                is RenewalEngine.Result.Failure ->
                    call.respond(HttpStatusCode.UnprocessableEntity, RenewalErrorResponse("RENEWAL_UNAVAILABLE", r.reason))
            }
        }
    }
}
