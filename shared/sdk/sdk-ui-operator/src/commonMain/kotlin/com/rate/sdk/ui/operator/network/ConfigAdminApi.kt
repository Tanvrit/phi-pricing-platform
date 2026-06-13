package com.rate.sdk.ui.operator.network

import com.rate.core.base.json.AppJson
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.network.client.TanvritClient
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The ONE generic admin-CRUD REST client the operator console uses for EVERY admin-managed
 * config entity. It mirrors the [com.rate.core.base.repository.ConfigRepository] port over HTTP
 * so the metadata-driven [com.rate.sdk.ui.operator.screen.ConfigEntityScreen] wires identically
 * for plans, products, sections, covers, CI lists, annexures, tenures, pincode-zones, add-ons,
 * group product config, grades, schedules, waiting periods, eligibility, ppd/ptd, day-care,
 * consumables, health-checkup and chronic-OPD.
 *
 * It sits ON TOP of the shared [TanvritClient] (transport: auth + retry + status mapping +
 * the frozen [AppJson] content-negotiation) and owns the admin route convention:
 *
 *   POST   /api/admin/{resource}/query    body PageRequest        → Page<T>
 *   GET    /api/admin/{resource}/{id}                              → T
 *   POST   /api/admin/{resource}          body T                   → T   (create)
 *   PUT    /api/admin/{resource}/{id}     body T, ?expectedV=      → T   (optimistic update)
 *   DELETE /api/admin/{resource}/{id}                              → 2xx (soft-delete)
 *   POST   /api/admin/{resource}/{id}/restore                      → 2xx
 *   POST   /api/admin/{resource}/{id}/publish                      → T   (publish draft)
 *   POST   /api/admin/{resource}/bulk     body List<T>             → Int (count written)
 *
 * The Mongo-backed actuals for these routes are implemented server-side in `server` over the
 * generic `ConfigRepository<T>` — this client is pure-KMP and never touches a driver.
 *
 * Because the operator console handles MANY entity types through one screen, the serializer for
 * `T` is passed explicitly (not `reified`) so a single registry can drive the whole CRUD surface.
 * The actor identity is threaded via the `X-Aegis-Actor` header (the same header the relocated
 * aegis ApiClient used for attribution).
 */
class ConfigAdminApi(
    private val client: TanvritClient,
    private val json: Json = AppJson.json,
) {
    private fun base(resource: String) = "/api/admin/${resource.trim('/')}"

    private fun actorHeaders(actor: String?): Map<String, String> =
        if (actor.isNullOrBlank()) emptyMap() else mapOf(ACTOR_HEADER to actor)

    /** Paged list/search. [req] carries page/size/sort/filter/includeDeleted. */
    suspend fun <T : ConfigEntity> list(
        resource: String,
        serializer: KSerializer<T>,
        req: PageRequest = PageRequest(),
    ): Page<T> {
        val response = client.execute(HttpMethod.Post, "${base(resource)}/query") {
            setBody(TextContent(json.encodeToString(PageRequest.serializer(), req), ContentType.Application.Json))
        }
        return json.decodeFromString(Page.serializer(serializer), response.bodyAsText())
    }

    /** Fetch one by id (null if the server returns 404). */
    suspend fun <T : ConfigEntity> get(
        resource: String,
        serializer: KSerializer<T>,
        id: String,
    ): T {
        val response = client.execute(HttpMethod.Get, "${base(resource)}/$id")
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    /** Create. The server stamps id/v/timestamps and the actor; the returned [T] is authoritative. */
    suspend fun <T : ConfigEntity> create(
        resource: String,
        serializer: KSerializer<T>,
        entity: T,
        actor: String?,
    ): T {
        val response = client.execute(HttpMethod.Post, base(resource)) {
            setBody(TextContent(json.encodeToString(serializer, entity), ContentType.Application.Json))
            actorHeaders(actor).forEach { (k, v) -> header(k, v) }
        }
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    /** Optimistic update — the server fails with Conflict if [expectedV] != stored v. */
    suspend fun <T : ConfigEntity> update(
        resource: String,
        serializer: KSerializer<T>,
        entity: T,
        expectedV: Long,
        actor: String?,
    ): T {
        val response = client.execute(HttpMethod.Put, "${base(resource)}/${entity.id}") {
            setBody(TextContent(json.encodeToString(serializer, entity), ContentType.Application.Json))
            parameter("expectedV", expectedV.toString())
            actorHeaders(actor).forEach { (k, v) -> header(k, v) }
        }
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    /** Soft-delete (sets isDeleted=true server-side). Returns true on 2xx. */
    suspend fun softDelete(resource: String, id: String, actor: String?): Boolean {
        val status = client.delete("${base(resource)}/$id", headers = actorHeaders(actor))
        return status in 200..299
    }

    /** Undo a soft-delete. Returns true on 2xx. */
    suspend fun restore(resource: String, id: String, actor: String?): Boolean {
        val response = client.execute(HttpMethod.Post, "${base(resource)}/$id/restore") {
            actorHeaders(actor).forEach { (k, v) -> header(k, v) }
        }
        return response.status.value in 200..299
    }

    /** Promote a DRAFT to PUBLISHED (atomically retires the version it drafts). */
    suspend fun <T : ConfigEntity> publishDraft(
        resource: String,
        serializer: KSerializer<T>,
        draftId: String,
        actor: String?,
    ): T {
        val response = client.execute(HttpMethod.Post, "${base(resource)}/$draftId/publish") {
            actorHeaders(actor).forEach { (k, v) -> header(k, v) }
        }
        return json.decodeFromString(serializer, response.bodyAsText())
    }

    /** Idempotent bulk upsert (seed/import). Returns the count written. */
    suspend fun <T : ConfigEntity> bulkUpsert(
        resource: String,
        serializer: KSerializer<T>,
        entities: List<T>,
        actor: String?,
    ): Int {
        val response = client.execute(HttpMethod.Post, "${base(resource)}/bulk") {
            setBody(TextContent(json.encodeToString(ListSerializer(serializer), entities), ContentType.Application.Json))
            actorHeaders(actor).forEach { (k, v) -> header(k, v) }
        }
        return response.bodyAsText().trim().toIntOrNull() ?: 0
    }

    companion object {
        /** Attribution header echoed into the audit chain for every admin mutation. */
        const val ACTOR_HEADER = "X-Aegis-Actor"
    }
}
