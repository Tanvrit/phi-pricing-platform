package com.rate.server.routes

import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import com.rate.server.startedAt
import io.ktor.http.HttpStatusCode
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
 * Wire-level mirror of the operator-side "Server config" diagnostic card.
 *
 * Read-only snapshot of the server's effective runtime config — version,
 * listening port, sanitised DB host (no creds), CORS allowlist, OTP / idempotency
 * TTLs, and boot timestamp. Mirrored byte-for-byte by `ApiClient.ServerConfigInfo`
 * in `:aegis/commonMain`.
 *
 * Defence-in-depth: NEVER widen this DTO to include the DB password, the OTP
 * token secret, or any other env-loaded credential. The endpoint is scope-gated
 * (`audit.verify`), but a leaky DTO would still embarrass us in logs / audit
 * payloads. If you need to surface another setting, double-check it isn't a
 * secret first.
 */
@Serializable
data class ServerConfigInfo(
    val version: String,
    val port: Int,
    val dbHost: String,
    val corsOrigins: List<String>,
    val otpTtlSec: Int,
    val idempotencyTtlHours: Int,
    val startedAtIso: String,
)

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
 * Wire-level snapshot of HikariCP connection-pool stats for the Aegis "DB
 * connection pool" diagnostic card. Read-only — operators get to see how
 * many connections are checked out vs. idle vs. waiting; the surface
 * colour-codes [threadsAwaiting] red when non-zero (pool exhaustion).
 *
 * Sourced from `HikariDataSource.hikariPoolMXBean` (live JMX bean) — no
 * tunables here; if `maxPoolSize` ever needs adjusting that goes through
 * `DatabaseFactory.init` env vars, not this endpoint.
 */
@Serializable
internal data class DbPoolStats(
    val active: Int,
    val idle: Int,
    val total: Int,
    val threadsAwaiting: Int,
    val maxPoolSize: Int,
)

@Serializable
private data class DbPoolStatus(val status: String)

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
    // Sibling diagnostic: effective server config snapshot. Gated under the
    // same `audit.verify` scope used by the outbox listing / idempotency cache
    // walker — operators with that scope already see the surrounding server
    // state and a dedicated `config.read` scope would be pure noise.
    get("/api/admin/config") {
        if (!requireScope("audit.verify")) return@get
        // DB host extraction: turn `jdbc:postgresql://host:port/db?params=...`
        // into `host:port/db`. We strip both the `jdbc:` prefix portion AND
        // any `?query=string` so we don't accidentally surface a password that
        // a poorly-configured deployment has dropped into the URL itself.
        // Falls back to "(unset)" if DB_URL isn't in the environment (config
        // file default is the local dev URL, which is fine to expose).
        val dbHost = System.getenv("DB_URL")?.let { url ->
            val afterScheme = url.substringAfter("//", url)
            afterScheme.substringBefore("?")
        } ?: "(unset)"
        val corsOrigins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        call.respond(
            ServerConfigInfo(
                version = "0.1.0-dev",
                port = (System.getenv("PORT") ?: "9090").toIntOrNull() ?: 9090,
                dbHost = dbHost,
                corsOrigins = corsOrigins,
                // OTP code TTL (mirrors OtpService.OTP_TTL_SECONDS) and the
                // IdempotencyService 24h purge window. Hardcoded here because
                // those constants are private to their services today; if they
                // ever become tunable we'll plumb them through DI rather than
                // re-reading env vars in the route.
                otpTtlSec = 300,
                idempotencyTtlHours = 24,
                startedAtIso = startedAt.toString(),
            )
        )
    }

    // Sibling diagnostic: live HikariCP pool stats. Same `audit.verify` scope
    // gate — read-only snapshot, no tunables exposed (pool size is set in
    // DatabaseFactory.init via env vars and changing it requires a restart).
    // We read the live JMX bean off the concrete `HikariDataSource` rather
    // than going through `javax.sql.DataSource` because `hikariPoolMXBean` is
    // Hikari-specific and `DatabaseFactory.dataSource` is already typed as
    // `HikariDataSource?` so no cast is needed.
    get("/api/admin/db-pool") {
        if (!requireScope("audit.verify")) return@get
        val ds = com.rate.server.database.DatabaseFactory.dataSource
        if (ds == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, DbPoolStatus("not-initialised"))
            return@get
        }
        val mx = ds.hikariPoolMXBean
        if (mx == null) {
            // MXBean is unregistered when the pool is mid-shutdown or JMX is
            // disabled in the Hikari config. Don't treat as fatal — emit a
            // distinct status so the surface can show a soft warning.
            call.respond(DbPoolStatus("no-mxbean"))
            return@get
        }
        call.respond(
            DbPoolStats(
                active = mx.activeConnections,
                idle = mx.idleConnections,
                total = mx.totalConnections,
                threadsAwaiting = mx.threadsAwaitingConnection,
                maxPoolSize = ds.maximumPoolSize,
            )
        )
    }

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
