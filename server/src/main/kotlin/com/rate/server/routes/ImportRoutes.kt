package com.rate.server.routes

import com.rate.core.auth.rbac.Scope
import com.rate.core.base.json.AppJson
import com.rate.core.rating.ports.model.ProductLine
import com.rate.persistence.importer.ExcelRateImporter
import com.rate.persistence.rating.RateTableCache
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.auditActor
import com.rate.server.auth.requireScope
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.sdk.ingestion.handler.RateImportHandler
import com.rate.sdk.ingestion.model.ImportSummary
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream

@Serializable internal data class ImportErrorResponse(val errorCode: String, val message: String)

/**
 * Rate-table import. RELOCATED from the monolith's `ImportRoutes` — the POI parsing now lives in
 * server-persistence's [ExcelRateImporter] (the ONLY place POI lives), and the version/dedup/
 * activate orchestration lives in sdk-ingestion's pure [RateImportHandler]. The route is thin:
 * read the multipart file, parse it to a `RateRowBatch`, hand it to the handler, reload the engine
 * snapshot, audit it.
 *
 * Idempotency: the file content's SHA-256 keys an Idempotency-Key replay so re-uploading the same
 * file with the same key returns the first summary; a different file with the same key → 409.
 * (The handler ALSO dedupes by file SHA independently, so a missing key is still safe.)
 *
 *   POST /api/import/upload  — multipart file (import.run scope)
 */
fun Route.importRoutes(
    importer: ExcelRateImporter,
    rateImport: RateImportHandler,
    rateCache: RateTableCache,
    audit: ServerAuditService,
    idempotencyHasher: HmacIdempotencyHasher,
) {
    val json = AppJson.json
    val summarySerializer = ImportSummary.serializer()

    route("/api/import") {
        post("/upload") {
            if (!requireScope(Scope.IMPORT_RUN)) return@post

            val parts = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var filename: String? = null
            var version: String? = call.request.queryParameters["version"]
            parts.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileBytes = part.provider().readRemaining().readByteArray()
                        filename = part.originalFileName
                    }
                    is PartData.FormItem -> if (part.name == "version") version = part.value
                    else -> Unit
                }
                part.dispose()
            }

            val bytes = fileBytes
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, ImportErrorResponse("NO_FILE", "No file provided"))
                    return@post
                }

            // Content SHA keys both the idempotency replay AND the handler's own dedupe.
            val sha = HmacIdempotencyHasher.sha256Hex(bytes)
            val effectiveVersion = version?.takeIf { it.isNotBlank() } ?: "import-${sha.take(12)}"
            val actor = call.auditActor()

            val batch = ByteArrayInputStream(bytes).use { stream ->
                importer.parse(stream, effectiveVersion)
            }
            val summary: ImportSummary = rateImport.importBatch(
                batch = batch,
                sha = sha,
                sourceFileName = filename ?: "(unknown)",
                productLine = ProductLine.RETAIL,
                activate = true,
                actor = actor.subject,
            )

            // Reload the in-RAM engine snapshot so the freshly-activated version serves traffic.
            if (summary.ok && !summary.deduped) {
                runCatching { rateCache.load(ProductLine.RETAIL) }
            }

            audit.record(
                action = if (summary.deduped) "import.deduped" else "rates.imported",
                entity = "rate_table",
                entityId = summary.version.ifBlank { sha },
                payloadJson = buildJsonObject {
                    put("filename", filename ?: "(unknown)")
                    put("sizeBytes", bytes.size)
                    put("sha256", sha)
                    put("version", summary.version)
                    put("rowsWritten", summary.totalWritten)
                    put("deduped", summary.deduped)
                }.toString(),
                actor = actor,
            )

            call.response.header("X-Import-Sha256", sha)
            call.respondText(json.encodeToString(summarySerializer, summary), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}
