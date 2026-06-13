package com.rate.server.routes

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainException
import com.rate.server.plugins.ErrorResponse
import com.rate.server.plugins.REQUEST_ID_KEY
import com.rate.server.plugins.mapDomainError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Shared route helpers so every route group maps the transport-agnostic [AppResult] / [DomainException]
 * to HTTP identically. RELOCATED behaviour from the monolith's scattered `when (result)` blocks.
 */

/** Respond with an [AppResult]: on Ok respond [okStatus] + value, on Err map the domain error. */
suspend inline fun <reified T : Any> RoutingContext.respondResult(
    result: AppResult<T>,
    okStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    when (result) {
        is AppResult.Ok -> call.respond(okStatus, result.value)
        is AppResult.Err -> {
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val (status, code, details) = mapDomainError(result.error)
            call.respond(status, ErrorResponse(code, result.error.msg, rid, details))
        }
    }
}

/** Unwrap an [AppResult] or throw its [DomainException] (handled by StatusPages). */
fun <T> AppResult<T>.orThrow(): T = when (this) {
    is AppResult.Ok -> value
    is AppResult.Err -> throw DomainException(error)
}

/** Required path parameter or a 400-mapped [IllegalArgumentException]. */
fun ApplicationCall.requireParam(name: String): String =
    parameters[name] ?: throw IllegalArgumentException("Missing path parameter '$name'")

/** Parse a paged-list query (page/size/sort) from the request query string. */
fun ApplicationCall.pageRequest(defaultSize: Int = 50, maxSize: Int = 500): com.rate.core.base.model.PageRequest {
    val page = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val size = (request.queryParameters["size"]?.toIntOrNull() ?: defaultSize).coerceIn(1, maxSize)
    val includeDeleted = request.queryParameters["includeDeleted"]?.toBoolean() ?: false
    val sort = request.queryParameters["sort"]?.split(',')?.mapNotNull { spec ->
        val parts = spec.split(':')
        val field = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val dir = if (parts.getOrNull(1)?.equals("desc", ignoreCase = true) == true) {
            com.rate.core.base.model.SortDir.DESC
        } else {
            com.rate.core.base.model.SortDir.ASC
        }
        com.rate.core.base.model.SortSpec(field, dir)
    } ?: emptyList()
    // Arbitrary `field=value` filters via the `f.<field>=<value>` query convention.
    val filter = request.queryParameters.entries()
        .filter { it.key.startsWith("f.") }
        .associate { it.key.removePrefix("f.") to (it.value.firstOrNull() ?: "") }
    return com.rate.core.base.model.PageRequest(
        page = page, size = size, sort = sort, filter = filter, includeDeleted = includeDeleted,
    )
}
