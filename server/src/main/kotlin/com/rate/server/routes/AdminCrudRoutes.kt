package com.rate.server.routes

import com.rate.core.base.json.AppJson
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.Page
import com.rate.core.base.repository.ConfigRepository
import com.rate.core.auth.rbac.Scope
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.auditActor
import com.rate.server.auth.requireScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The ONE generic admin-CRUD route factory. Every admin-manageable config entity (plan, cover,
 * section, tenure, zone, pincode, add-on, rate, group config, …) is exposed through IDENTICAL
 * wiring over the core [ConfigRepository] PORT — so internal users can list/search/create/update/
 * soft-delete/restore/publish/import/export ANY entity from the frontend without per-entity glue.
 *
 * This is the re-arch's answer to the monolith's hand-written, per-entity route files (PlanRoutes,
 * CoverRoutes, DiscountRoutes, …): one factory, one set of semantics, every entity audited.
 *
 * Endpoints under [path] (each scoped by `config.<entity>.<verb>`):
 *   GET  /                 — paged/filtered list (read)
 *   GET  /{id}             — single (read)
 *   POST /                 — create (write)
 *   PUT  /{id}             — optimistic update; `?expectedV=` or body v (write)
 *   DELETE /{id}           — soft delete (delete)
 *   POST /{id}/restore     — undelete (write)
 *   POST /{id}/publish     — promote DRAFT → PUBLISHED (publish)
 *   POST /import           — bulk upsert (write)
 *   GET  /export           — full export (read)
 *
 * Every mutation writes an audit row via [audit] (best-effort; never blocks the response).
 *
 * [entityName] is the scope segment + audit `entity` + JSON `_class`-free element name.
 * [serializer] is the entity's KSerializer (used for body decode + bulk import/export).
 */
fun <T : ConfigEntity> Route.adminCrudRoutes(
    path: String,
    entityName: String,
    serializer: KSerializer<T>,
    repo: ConfigRepository<T>,
    audit: ServerAuditService,
    /**
     * The `config.<scopeEntity>.<verb>` segment used for RBAC. Defaults to [entityName] but is
     * passed explicitly for entities whose audit name is more specific than the coarse scope token
     * in core-auth's role bundles (e.g. `criticalIllnessList` is audited by name but scoped under
     * `cover`), so a BUSINESS operator's default bundle still grants access. ADMIN (`*`) is
     * unaffected either way.
     */
    scopeEntity: String = entityName,
) {
    val json = AppJson.json
    val listSerializer = ListSerializer(serializer)
    val pageSerializer = Page.serializer(serializer)

    val readScope = Scope.config(scopeEntity, Scope.READ)
    val writeScope = Scope.config(scopeEntity, Scope.WRITE)
    val deleteScope = Scope.config(scopeEntity, Scope.DELETE)
    val publishScope = Scope.config(scopeEntity, Scope.PUBLISH)

    route(path) {
        // ── LIST (paged + filtered) ──────────────────────────────────────────
        get {
            if (!requireScope(readScope)) return@get
            val page = repo.list(call.pageRequest())
            call.respondText(json.encodeToString(pageSerializer, page), ContentType.Application.Json)
        }

        // ── QUERY (paged + filtered via a PageRequest BODY — the operator-ui client path) ──
        post("/query") {
            if (!requireScope(readScope)) return@post
            val req = json.decodeFromString(com.rate.core.base.model.PageRequest.serializer(), call.receiveText())
            val page = repo.list(req)
            call.respondText(json.encodeToString(pageSerializer, page), ContentType.Application.Json)
        }

        // ── EXPORT (full, unpaged) ───────────────────────────────────────────
        get("/export") {
            if (!requireScope(readScope)) return@get
            // Pull a large page; export is an operator/back-office action, not a hot path.
            val all = repo.list(com.rate.core.base.model.PageRequest(page = 0, size = 100_000, includeDeleted = true))
            call.respondText(json.encodeToString(listSerializer, all.items), ContentType.Application.Json)
        }

        // ── GET one ──────────────────────────────────────────────────────────
        get("/{id}") {
            if (!requireScope(readScope)) return@get
            val id = call.requireParam("id")
            val entity = repo.get(id) ?: throw NoSuchElementException("$entityName '$id' not found")
            call.respondText(json.encodeToString(serializer, entity), ContentType.Application.Json)
        }

        // ── CREATE ───────────────────────────────────────────────────────────
        post {
            if (!requireScope(writeScope)) return@post
            val actor = call.auditActor()
            val entity = json.decodeFromString(serializer, call.receiveText())
            val created = repo.create(entity, actor.subject)
            audit.record(
                action = "$entityName.created",
                entity = entityName,
                entityId = created.id,
                payloadJson = buildJsonObject { put("id", created.id); put("v", created.v) }.toString(),
                actor = actor,
            )
            call.respondText(json.encodeToString(serializer, created), ContentType.Application.Json, HttpStatusCode.Created)
        }

        // ── UPDATE (optimistic on v) ─────────────────────────────────────────
        put("/{id}") {
            if (!requireScope(writeScope)) return@put
            val id = call.requireParam("id")
            val actor = call.auditActor()
            val entity = json.decodeFromString(serializer, call.receiveText())
            // expectedV precedence: query param wins (lets a stale-body client be explicit),
            // else fall back to the body's own v.
            val expectedV = call.request.queryParameters["expectedV"]?.toLongOrNull() ?: entity.v
            val updated = repo.update(entity, expectedV, actor.subject)
            audit.record(
                action = "$entityName.updated",
                entity = entityName,
                entityId = id,
                payloadJson = buildJsonObject { put("id", id); put("fromV", expectedV); put("toV", updated.v) }.toString(),
                actor = actor,
            )
            call.respondText(json.encodeToString(serializer, updated), ContentType.Application.Json)
        }

        // ── SOFT DELETE ──────────────────────────────────────────────────────
        delete("/{id}") {
            if (!requireScope(deleteScope)) return@delete
            val id = call.requireParam("id")
            val actor = call.auditActor()
            val ok = repo.softDelete(id, actor.subject)
            if (!ok) throw NoSuchElementException("$entityName '$id' not found")
            audit.record(
                action = "$entityName.deleted",
                entity = entityName,
                entityId = id,
                actor = actor,
            )
            call.respond(HttpStatusCode.NoContent)
        }

        // ── RESTORE ──────────────────────────────────────────────────────────
        post("/{id}/restore") {
            if (!requireScope(writeScope)) return@post
            val id = call.requireParam("id")
            val actor = call.auditActor()
            val ok = repo.restore(id, actor.subject)
            if (!ok) throw NoSuchElementException("$entityName '$id' not found")
            audit.record(
                action = "$entityName.restored",
                entity = entityName,
                entityId = id,
                actor = actor,
            )
            call.respond(HttpStatusCode.OK, RestoreResponse(id))
        }

        // ── PUBLISH DRAFT ────────────────────────────────────────────────────
        post("/{id}/publish") {
            if (!requireScope(publishScope)) return@post
            val id = call.requireParam("id")
            val actor = call.auditActor()
            val published = repo.publishDraft(id, actor.subject)
            audit.record(
                action = "$entityName.published",
                entity = entityName,
                entityId = published.id,
                payloadJson = buildJsonObject { put("draftId", id); put("publishedId", published.id) }.toString(),
                actor = actor,
            )
            call.respondText(json.encodeToString(serializer, published), ContentType.Application.Json)
        }

        // ── BULK IMPORT (idempotent upsert) — served at BOTH /import and /bulk ────
        val bulkHandler: suspend io.ktor.server.routing.RoutingContext.() -> Unit = {
            if (requireScope(writeScope)) {
                val actor = call.auditActor()
                val entities = json.decodeFromString(listSerializer, call.receiveText())
                val written = repo.bulkUpsert(entities, actor.subject)
                audit.record(
                    action = "$entityName.imported",
                    entity = entityName,
                    entityId = null,
                    payloadJson = buildJsonObject { put("count", written) }.toString(),
                    actor = actor,
                )
                call.respond(HttpStatusCode.OK, ImportResponse(entityName, written))
            }
        }
        post("/import", bulkHandler)
        post("/bulk", bulkHandler)
    }
}

@kotlinx.serialization.Serializable
internal data class RestoreResponse(val id: String, val restored: Boolean = true)

@kotlinx.serialization.Serializable
internal data class ImportResponse(val entity: String, val written: Int)
