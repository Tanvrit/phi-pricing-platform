package com.rate.server.routes

import com.rate.core.auth.rbac.Scope
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.auditActor
import com.rate.server.auth.requireScope
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Read-only operator diagnostics + the Phase-1 email-outbox housekeeping. RELOCATED from the
 * monolith's `AdminRoutes`, minus the HikariCP-specific db-pool card (the re-arch is Mongo-only).
 * All endpoints are gated by `settings.manage` — the same audience that sees server internals.
 *
 *   GET    /api/admin/config        — effective config snapshot (NEVER secrets)
 *   GET    /api/admin/log           — tail of logs/aegis.log (PII-masked at write time)
 *   GET    /api/admin/outbox        — list .eml outbox entries
 *   DELETE /api/admin/outbox        — purge .eml older than ?olderThanDays= (audited)
 */
@Serializable
data class ServerConfigInfo(
    val version: String,
    val port: Int,
    val mongoDb: String,
    val corsOrigins: List<String>,
    val otpTtlSec: Int,
    val idempotencyTtlHours: Int,
    val startedAtIso: String,
)

@Serializable
data class OutboxEntry(
    val filename: String,
    val sizeBytes: Long,
    val createdAtIso: String,
    val to: String,
    val subject: String,
    val previewText: String,
)

@Serializable
data class OutboxPurgeResponse(val requested: Int, val deleted: Int)

@Serializable
data class LogTail(val lines: List<String>, val reason: String? = null)

fun Route.adminRoutes(
    outboxDir: File,
    audit: ServerAuditService,
    startedAtIso: String,
) {
    get("/api/admin/config") {
        if (!requireScope(Scope.SETTINGS_MANAGE)) return@get
        val mongoDb = System.getenv("MONGO_DB") ?: "rate"
        val corsOrigins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }
        call.respond(
            ServerConfigInfo(
                version = "0.2.0-rearch",
                port = (System.getenv("PORT") ?: "9090").toIntOrNull() ?: 9090,
                mongoDb = mongoDb,
                corsOrigins = corsOrigins,
                otpTtlSec = 300,
                idempotencyTtlHours = 24,
                startedAtIso = startedAtIso,
            ),
        )
    }

    get("/api/admin/log") {
        if (!requireScope(Scope.SETTINGS_MANAGE)) return@get
        val lines = (call.request.queryParameters["lines"]?.toIntOrNull() ?: 200).coerceIn(1, 2000)
        val logFile = File("logs/aegis.log")
        if (!logFile.exists()) {
            call.respond(
                LogTail(
                    lines = emptyList(),
                    reason = "Log file at logs/aegis.log not found. Either nothing has been logged " +
                        "since boot, or no RollingFileAppender is wired in logback.xml.",
                ),
            )
            return@get
        }
        val tail = runCatching { logFile.useLines { it.toList().takeLast(lines) } }
            .getOrElse { t ->
                call.respond(LogTail(emptyList(), "Failed to read logs/aegis.log: ${t.message ?: t::class.simpleName}"))
                return@get
            }
        call.respond(LogTail(lines = tail))
    }

    route("/api/admin/outbox") {
        get {
            if (!requireScope(Scope.SETTINGS_MANAGE)) return@get
            if (!outboxDir.isDirectory) {
                call.respond(emptyList<OutboxEntry>())
                return@get
            }
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            val files = outboxDir.listFiles { f -> f.isFile && f.name.endsWith(".eml") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(limit)
                ?: emptyList()
            call.respond(
                files.map { f ->
                    val text = runCatching { f.readText() }.getOrDefault("")
                    OutboxEntry(
                        filename = f.name,
                        sizeBytes = f.length(),
                        createdAtIso = Instant.fromEpochMilliseconds(f.lastModified()).toString(),
                        to = extractHeader(text, "To"),
                        subject = extractHeader(text, "Subject"),
                        previewText = previewBody(text, maxChars = 240),
                    )
                },
            )
        }

        delete {
            if (!requireScope(Scope.SETTINGS_MANAGE)) return@delete
            val olderThanDays = (call.request.queryParameters["olderThanDays"]?.toIntOrNull() ?: 7).coerceIn(0, 365)
            val cutoff = System.currentTimeMillis() - olderThanDays * 24L * 3600_000L
            val files = outboxDir.listFiles { f ->
                f.isFile && f.name.endsWith(".eml") && f.lastModified() < cutoff
            } ?: emptyArray()
            val deleted = files.count { it.delete() }
            audit.record(
                action = "outbox.purged",
                entity = "outbox",
                entityId = null,
                payloadJson = buildJsonObject {
                    put("olderThanDays", olderThanDays)
                    put("requested", files.size)
                    put("deleted", deleted)
                }.toString(),
                actor = call.auditActor(),
            )
            call.respond(OutboxPurgeResponse(requested = files.size, deleted = deleted))
        }
    }
}

private fun extractHeader(eml: String, name: String): String {
    val needle = "$name: "
    return eml.lineSequence().firstOrNull { it.startsWith(needle, ignoreCase = true) }
        ?.removePrefix(needle)?.trim() ?: ""
}

private fun previewBody(eml: String, maxChars: Int): String {
    val crlf = eml.indexOf("\r\n\r\n")
    val lf = eml.indexOf("\n\n")
    val bodyStart = when {
        crlf >= 0 && (lf < 0 || crlf < lf) -> crlf + 4
        lf >= 0 -> lf + 2
        else -> return ""
    }
    return eml.substring(bodyStart).take(maxChars).replace("\r", "").replace("\n", " ")
}
