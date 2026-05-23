package com.rate.server.routes

import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Wire-level mirror of a single `.eml` row in the Phase-1 filesystem outbox.
 * Header values are pulled from the .eml directly (so what the operator sees
 * here is exactly what was written to disk); [previewText] is capped server-
 * side at 240 chars so we never broadcast an entire email body over the wire.
 */
@Serializable
data class OutboxEntry(
    val filename: String,
    val sizeBytes: Long,
    val createdAtIso: String,
    val to: String,
    val subject: String,
    val previewText: String,
)

/**
 * Result of an outbox purge — `requested` is the count of files matching the
 * cutoff filter, `deleted` is how many `File.delete()` actually succeeded.
 * The two diverge when a file vanished mid-call or a permission flip blocked
 * removal; surfacing both lets operators spot stuck files quickly.
 */
@Serializable
data class OutboxPurgeResponse(val requested: Int, val deleted: Int)

/**
 * Admin-only diagnostics. Today: list / bulk-prune the email outbox
 * (~/.aegis/outbox/), useful for verifying that the buyonline "Email me"
 * flow actually drops files where it claims to. In Phase 2 these endpoints
 * go away — real SMTP sends don't have a filesystem outbox.
 *
 * Gated by the existing `audit.verify` scope rather than a new one: this is a
 * read-only operator-side diagnostic of the same flavour as the audit-chain
 * walker and the idempotency cache listing, and operators with that scope
 * already see the surrounding system state. A dedicated `email.outbox.read`
 * scope would be pure noise. The DELETE variant also writes an audit row so
 * the operator-initiated purge is traceable through the chain.
 */
fun Route.adminRoutes(outboxDir: File, auditService: AuditEventService) {
    route("/api/admin/outbox") {
        get {
            if (!requireScope("audit.verify")) return@get
            if (!outboxDir.isDirectory) {
                call.respond(emptyList<OutboxEntry>())
                return@get
            }
            // Bound the response: the on-disk outbox grows unbounded, but the
            // dashboard only ever shows the most recent slice. Cap is 200 to
            // mirror /api/audit/events; default 50 keeps a fresh dashboard
            // open cheap.
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50)
                .coerceIn(1, 200)
            val files = outboxDir.listFiles { f -> f.isFile && f.name.endsWith(".eml") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(limit)
                ?: emptyList()
            val entries = files.map { f ->
                // Read failures (file vanished, permission flip mid-listing) degrade
                // to empty-string fields rather than failing the whole listing — the
                // surface still wants to render the row's mtime + size.
                val text = runCatching { f.readText() }.getOrDefault("")
                OutboxEntry(
                    filename = f.name,
                    sizeBytes = f.length(),
                    createdAtIso = Instant.fromEpochMilliseconds(f.lastModified()).toString(),
                    to = extractHeader(text, "To"),
                    subject = extractHeader(text, "Subject"),
                    previewText = previewBody(text, maxChars = 240),
                )
            }
            call.respond(entries)
        }

        // Bulk-prune .eml files older than `olderThanDays`. The query param is
        // clamped to 0..365 — 0 is the "delete everything" floor (server still
        // requires the operator to opt in by passing it explicitly; the Aegis
        // UI button passes 7) and 365 prevents pathological no-op calls. We
        // ONLY touch `.eml` files: a misconfigured outboxDir pointed at a
        // populated directory must never wipe unrelated content.
        delete {
            if (!requireScope("audit.verify")) return@delete
            val olderThanDays = (call.request.queryParameters["olderThanDays"]?.toIntOrNull() ?: 7)
                .coerceIn(0, 365)
            val cutoff = System.currentTimeMillis() - olderThanDays * 24L * 3600_000L
            val files = outboxDir.listFiles { f ->
                f.isFile && f.name.endsWith(".eml") && f.lastModified() < cutoff
            } ?: emptyArray()
            val deleted = files.count { it.delete() }

            // Audit the operator action so the purge shows up in the hash-
            // chained ledger alongside plan edits / quote saves. Best-effort:
            // a failed audit write does NOT roll back the delete (consistent
            // with the rest of the codebase's audit semantics).
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
            auditService.record(
                action = "outbox.purged",
                resourceType = "outbox",
                resourceId = null,
                payload = JsonObject(mapOf(
                    "olderThanDays" to JsonPrimitive(olderThanDays),
                    "requested" to JsonPrimitive(files.size),
                    "deleted" to JsonPrimitive(deleted),
                )),
                actor = actor,
                requestId = rid,
            )

            call.respond(OutboxPurgeResponse(requested = files.size, deleted = deleted))
        }
    }
}

private fun extractHeader(eml: String, name: String): String {
    val needle = "$name: "
    return eml.lineSequence().firstOrNull { it.startsWith(needle, ignoreCase = true) }
        ?.removePrefix(needle)
        ?.trim() ?: ""
}

private fun previewBody(eml: String, maxChars: Int): String {
    // The first blank line separates headers from body; everything after is preview.
    // Look for both LF-LF and CRLF-CRLF so we don't choke on Windows-line-ending
    // .eml files that a future SMTP-style writer might produce.
    val crlf = eml.indexOf("\r\n\r\n")
    val lf = eml.indexOf("\n\n")
    val bodyStart = when {
        crlf >= 0 && (lf < 0 || crlf < lf) -> crlf + 4
        lf >= 0 -> lf + 2
        else -> return ""
    }
    return eml.substring(bodyStart)
        .take(maxChars)
        .replace("\r", "")
        .replace("\n", " ")
}
