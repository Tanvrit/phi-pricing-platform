package com.rate.sdk.ui.operator.network

import com.rate.core.base.json.AppJson
import com.rate.core.network.client.TanvritClient
import com.rate.sdk.audit.model.AuditEvent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.client.request.parameter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Read-only client for the append-only audit chain. The operator console's audit surface tails the
 * most recent immutable events from the server (`GET /api/audit/events`). Writes happen exclusively
 * server-side through the [com.rate.sdk.audit.handler.AuditRecorder] — there is no client write path
 * (the chain integrity depends on the server's sequence counter + hash chaining).
 */
class AuditReadApi(
    private val client: TanvritClient,
    private val json: Json = AppJson.json,
) {
    /**
     * Most-recent-first page of audit events.
     *
     * [limit] caps the tail. The remaining params are OPTIONAL server-side hints — the
     * route honours whichever it recognises and ignores the rest, so older servers stay
     * back-compatible (they just return the unfiltered tail and the surface narrows it
     * client-side over the loaded page):
     *  - [entity] filters by resource type ("plan", "quote", …).
     *  - [action] filters by verb ("plan.published", "quote.created", …).
     *  - [actor] filters by the acting subject.
     */
    suspend fun recent(
        limit: Int = 100,
        entity: String? = null,
        action: String? = null,
        actor: String? = null,
    ): List<AuditEvent> {
        val response = client.execute(HttpMethod.Get, "/api/audit/events") {
            parameter("limit", limit.toString())
            entity?.let { parameter("entity", it) }
            action?.let { parameter("action", it) }
            actor?.let { parameter("actor", it) }
        }
        return json.decodeFromString(ListSerializer(AuditEvent.serializer()), response.bodyAsText())
    }
}
