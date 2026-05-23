package com.rate.server.audit

import com.rate.server.database.tables.AuditEventTable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Wire-format row for `GET /api/audit/events`. Mirrors the columns in
 * [AuditEventTable]; the `prevHash` is intentionally elided here because the
 * UI primarily wants `this_hash` for cross-reference (the verify endpoint
 * handles full-chain inspection).
 */
@Serializable
data class AuditEventRow(
    val id: Long,
    val eventAt: String,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val actorSubject: String?,
    val actorRole: String?,
    val requestId: String?,
    val payloadJson: String?,
    val prevHash: String?,
    val thisHash: String
)

/**
 * Identifies who is performing an action. Phase 2 of the audit roadmap will wire real
 * JWT claims into this; for now every route writes `AuditActor.unknown()` so the
 * column is plumbed end-to-end and future auth just has to fill in real values.
 */
data class AuditActor(
    val subject: String? = null,
    val role: String? = null
) {
    companion object {
        fun unknown(): AuditActor = AuditActor(subject = "unknown", role = null)
    }
}

/** Result of a chain-integrity walk. `breakAtId` is the first row whose hash is wrong. */
data class VerifyResult(
    val ok: Boolean,
    val rowsChecked: Int,
    val breakAtId: Long? = null,
    val reason: String? = null
)

/**
 * Append-only, hash-chained audit log. Each row's `this_hash` is computed from the
 * previous row's hash plus a canonical JSON serialization of the new event, so any
 * tampering with a historical row will surface during `verifyChain()`.
 *
 * Hash inputs are deterministic (sorted keys, no whitespace), so re-hashing the same
 * logical event always yields the same digest. The genesis row's prev_hash = "GENESIS".
 */
class AuditEventService {

    private val log = LoggerFactory.getLogger(AuditEventService::class.java)

    /**
     * Live broadcast of freshly-persisted audit rows. Used by `GET /api/audit/stream`
     * to push events to SSE subscribers without polling the DB.
     *
     * Buffer sizing:
     *  - replay = 0 (default): late subscribers don't get historical rows — they
     *    should bootstrap via `GET /api/audit/events?limit=N` and then attach.
     *  - extraBufferCapacity = 64: small headroom for transient consumer slowness
     *    (e.g. one client behind a slow proxy). On overflow we DROP_OLDEST so a
     *    stuck subscriber can never backpressure the audit write path. This is
     *    an explicit "best-effort live feed" — chain integrity stays in the DB
     *    and is verifiable via `/api/audit/verify` regardless of stream drops.
     */
    private val events = MutableSharedFlow<AuditEventRow>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    /**
     * Cold view of [events] for SSE subscribers. Each `collect` is an independent
     * subscription; cancelling the collecting coroutine (e.g. when the client
     * disconnects) automatically unsubscribes — no manual cleanup needed.
     */
    fun stream(): SharedFlow<AuditEventRow> = events.asSharedFlow()

    /**
     * Records one event and returns the inserted row id. Failures are caught and
     * logged — audit writes must never break the underlying business operation
     * (we'd rather miss one audit row than fail a quote save).
     */
    suspend fun record(
        action: String,
        resourceType: String,
        resourceId: String?,
        payload: JsonElement? = null,
        actor: AuditActor = AuditActor.unknown(),
        requestId: String? = null
    ): Long? = try {
        val inserted = newSuspendedTransaction {
            val prevHash = AuditEventTable
                .selectAll()
                .orderBy(AuditEventTable.id, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(AuditEventTable.thisHash)
                ?: GENESIS

            val now = Clock.System.now()
            val canonical = canonicalJson(
                eventAt = now,
                actorSubject = actor.subject,
                actorRole = actor.role,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                payload = payload,
                requestId = requestId
            )
            val thisHash = sha256(prevHash + canonical)

            val newId = AuditEventTable.insert {
                it[eventAt]      = now
                it[actorSubject] = actor.subject
                it[actorRole]    = actor.role
                it[AuditEventTable.action] = action
                it[AuditEventTable.resourceType] = resourceType
                it[AuditEventTable.resourceId]   = resourceId
                it[payloadJson]  = payload?.toString()
                it[AuditEventTable.requestId]    = requestId
                it[AuditEventTable.prevHash]     = prevHash
                it[AuditEventTable.thisHash]     = thisHash
            } get AuditEventTable.id

            // Build the wire row from the same fields that just hit the DB. We
            // emit OUTSIDE the transaction (after it returns) so a slow collector
            // can't hold the JDBC connection open.
            AuditEventRow(
                id            = newId,
                eventAt       = now.toString(),
                action        = action,
                resourceType  = resourceType,
                resourceId    = resourceId,
                actorSubject  = actor.subject,
                actorRole     = actor.role,
                requestId     = requestId,
                payloadJson   = payload?.toString(),
                prevHash      = prevHash,
                thisHash      = thisHash
            )
        }

        // tryEmit is non-suspending; drops on overflow (DROP_OLDEST) — acceptable
        // for a best-effort live feed. The persisted row in `audit_events` is the
        // source of truth; SSE is a convenience push, not a guarantee.
        events.tryEmit(inserted)
        inserted.id
    } catch (t: Throwable) {
        log.warn("audit.record failed for action={} resource={}: {}", action, resourceType, t.message)
        null
    }

    /**
     * Lists the most recent audit events for display in operator surfaces.
     * Newest-first, capped at [limit] (caller is responsible for upper-bound clamping).
     * No tampering checks are performed here — that's what `verifyChain` is for.
     */
    suspend fun listEvents(limit: Int = 100): List<AuditEventRow> =
        newSuspendedTransaction {
            AuditEventTable
                .selectAll()
                .orderBy(AuditEventTable.id, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    AuditEventRow(
                        id            = row[AuditEventTable.id],
                        eventAt       = row[AuditEventTable.eventAt].toString(),
                        action        = row[AuditEventTable.action],
                        resourceType  = row[AuditEventTable.resourceType],
                        resourceId    = row[AuditEventTable.resourceId],
                        actorSubject  = row[AuditEventTable.actorSubject],
                        actorRole     = row[AuditEventTable.actorRole],
                        requestId     = row[AuditEventTable.requestId],
                        payloadJson   = row[AuditEventTable.payloadJson],
                        prevHash      = row[AuditEventTable.prevHash],
                        thisHash      = row[AuditEventTable.thisHash]
                    )
                }
        }

    /**
     * Walks the chain in id-order from `fromId` (inclusive) to `toId` (inclusive),
     * confirming each row's `this_hash` matches the recomputed digest of its content
     * and that `prev_hash` matches the previous row's `this_hash`.
     */
    suspend fun verifyChain(fromId: Long? = null, toId: Long? = null): VerifyResult =
        newSuspendedTransaction {
            val rows = AuditEventTable
                .selectAll()
                .orderBy(AuditEventTable.id, SortOrder.ASC)
                .toList()
                .filter { row ->
                    val id = row[AuditEventTable.id]
                    (fromId == null || id >= fromId) && (toId == null || id <= toId)
                }
            if (rows.isEmpty()) return@newSuspendedTransaction VerifyResult(ok = true, rowsChecked = 0)

            var expectedPrev: String? = null  // null = "any" for the first row in the slice
            var checked = 0
            for (row in rows) {
                val id = row[AuditEventTable.id]
                val storedPrev = row[AuditEventTable.prevHash]
                val storedHash = row[AuditEventTable.thisHash]

                if (expectedPrev != null && storedPrev != expectedPrev) {
                    return@newSuspendedTransaction VerifyResult(
                        ok = false, rowsChecked = checked, breakAtId = id,
                        reason = "prev_hash mismatch: expected=$expectedPrev got=$storedPrev"
                    )
                }
                val canonical = canonicalJson(
                    eventAt = row[AuditEventTable.eventAt],
                    actorSubject = row[AuditEventTable.actorSubject],
                    actorRole = row[AuditEventTable.actorRole],
                    action = row[AuditEventTable.action],
                    resourceType = row[AuditEventTable.resourceType],
                    resourceId = row[AuditEventTable.resourceId],
                    payload = row[AuditEventTable.payloadJson]?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull() },
                    requestId = row[AuditEventTable.requestId]
                )
                val recomputed = sha256((storedPrev ?: "") + canonical)
                if (recomputed != storedHash) {
                    return@newSuspendedTransaction VerifyResult(
                        ok = false, rowsChecked = checked, breakAtId = id,
                        reason = "this_hash mismatch at id=$id"
                    )
                }
                expectedPrev = storedHash
                checked++
            }
            VerifyResult(ok = true, rowsChecked = checked)
        }

    companion object {
        const val GENESIS: String = "GENESIS"

        /**
         * Canonical, deterministic serialization of an audit event for hashing.
         * Fields appear in fixed order, JSON keys sorted, no whitespace. Same event
         * always hashes to the same digest regardless of how kotlinx-serialization
         * happened to order map keys.
         */
        fun canonicalJson(
            eventAt: Instant,
            actorSubject: String?,
            actorRole: String?,
            action: String,
            resourceType: String,
            resourceId: String?,
            payload: JsonElement?,
            requestId: String?
        ): String {
            val sb = StringBuilder()
            sb.append('{')
            appendKv(sb, "action", action); sb.append(',')
            appendKv(sb, "actorRole", actorRole); sb.append(',')
            appendKv(sb, "actorSubject", actorSubject); sb.append(',')
            appendKv(sb, "eventAt", eventAt.toString()); sb.append(',')
            sb.append("\"payload\":").append(canonicalize(payload ?: JsonNull)); sb.append(',')
            appendKv(sb, "requestId", requestId); sb.append(',')
            appendKv(sb, "resourceId", resourceId); sb.append(',')
            appendKv(sb, "resourceType", resourceType)
            sb.append('}')
            return sb.toString()
        }

        private fun appendKv(sb: StringBuilder, k: String, v: String?) {
            sb.append('"').append(k).append("\":")
            if (v == null) sb.append("null")
            else sb.append(JsonPrimitive(v).toString())
        }

        /**
         * Canonicalises any JsonElement: object keys are emitted in sorted order;
         * arrays preserve order; primitives are emitted via JsonPrimitive.toString().
         */
        private fun canonicalize(el: JsonElement): String = when (el) {
            is JsonNull -> "null"
            is JsonPrimitive -> el.toString()
            is JsonObject -> {
                val sb = StringBuilder()
                sb.append('{')
                el.entries.sortedBy { it.key }.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    sb.append(JsonPrimitive(e.key).toString()).append(':').append(canonicalize(e.value))
                }
                sb.append('}')
                sb.toString()
            }
            is kotlinx.serialization.json.JsonArray -> {
                val sb = StringBuilder()
                sb.append('[')
                el.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    sb.append(canonicalize(e))
                }
                sb.append(']')
                sb.toString()
            }
        }

        fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
