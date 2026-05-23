package com.rate.server.routes

import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.import.ExcelImporter
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import com.rate.server.security.IdempotencyOutcome
import com.rate.server.security.IdempotencyService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.security.MessageDigest

@Serializable internal data class ImportErrorResponse(val errorCode: String, val message: String)
@Serializable internal data class ImportMessageResponse(val message: String, val sourceFilename: String? = null)

fun Route.importRoutes(auditService: AuditEventService, idempotencyService: IdempotencyService) {
    val importer = ExcelImporter()

    route("/api/import") {

        // Upload an Excel file to import rates. Honors Idempotency-Key (file bytes
        // are SHA-256'd so re-uploading the same file with the same key is a replay).
        post("/upload") {
            if (!requireScope("import.upload")) return@post
            val parts = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var filename: String? = null
            parts.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileBytes = part.provider().readRemaining().readByteArray()
                    filename = part.originalFileName
                }
                part.dispose()
            }
            val bytes = fileBytes
            if (bytes == null) {
                call.respond(HttpStatusCode.BadRequest,
                    ImportErrorResponse("NO_FILE", "No file provided"))
                return@post
            }

            // Idempotency: same key + same file bytes => replay; same key + different
            // bytes => 409; no key => process normally.
            val idemKey = call.request.headers["Idempotency-Key"]?.takeIf { it.isNotBlank() }
            val routeKey = "POST /api/import/upload"
            val requestHash = sha256Bytes(bytes)
            if (idemKey != null) {
                when (val outcome = idempotencyService.check(idemKey, routeKey, requestHash)) {
                    is IdempotencyOutcome.Replay -> {
                        call.response.header("Idempotency-Replayed", "true")
                        call.respondText(
                            outcome.hit.body ?: "",
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.fromValue(outcome.hit.status)
                        )
                        return@post
                    }
                    IdempotencyOutcome.Conflict -> {
                        call.respond(HttpStatusCode.Conflict,
                            ImportErrorResponse("IDEMPOTENCY_CONFLICT",
                                "Idempotency-Key was used previously with different file bytes."))
                        return@post
                    }
                    IdempotencyOutcome.Fresh -> { /* fall through */ }
                }
            }

            // Run the import.
            ByteArrayInputStream(bytes).use { stream -> importer.importFromExcel(stream) }

            // Audit + idempotency store.
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "rates.imported",
                resourceType = "rate_table",
                resourceId = filename,
                payload = JsonObject(mapOf(
                    "sourceFilename" to JsonPrimitive(filename ?: "unknown"),
                    "fileBytes" to JsonPrimitive(bytes.size.toLong()),
                    "sha256" to JsonPrimitive(requestHash)
                )),
                actor = actor,
                requestId = rid
            )
            // Secondary, upload-centric audit row. `rates.imported` above is keyed to the
            // rate_table resource; this companion event is keyed to the upload itself
            // (sha256 acts as the import-job id) so dashboards filtering on the
            // "import" resourceType see every successful POST /api/import/upload.
            auditService.record(
                action = "import.uploaded",
                resourceType = "import",
                resourceId = requestHash,
                payload = JsonObject(mapOf(
                    "filename" to JsonPrimitive(filename ?: "(unknown)"),
                    "sizeBytes" to JsonPrimitive(bytes.size.toLong()),
                    "sha256" to JsonPrimitive(requestHash),
                    "idempotencyKey" to JsonPrimitive(idemKey ?: "")
                )),
                actor = actor,
                requestId = rid
            )
            val responseBody = """{"message":"Excel data imported successfully","sourceFilename":${
                if (filename == null) "null" else "\"" + filename!!.replace("\"", "\\\"") + "\""
            }}"""
            if (idemKey != null) {
                idempotencyService.store(idemKey, routeKey, requestHash, HttpStatusCode.OK.value, responseBody)
            }
            call.respondText(responseBody, ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}

private fun sha256Bytes(b: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(b).joinToString("") { "%02x".format(it) }
}
