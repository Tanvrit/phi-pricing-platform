package com.rate.server.audit

import com.rate.core.base.model.PageRequest
import com.rate.core.base.model.SortDir
import com.rate.core.base.model.SortSpec
import com.rate.sdk.audit.event.AuditBroadcast
import com.rate.sdk.audit.handler.AuditRecorder
import com.rate.sdk.audit.model.AuditActor
import com.rate.sdk.audit.model.AuditEvent
import com.rate.sdk.audit.model.VerifyResult
import com.rate.sdk.audit.repository.AuditStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Thin server-side facade over the pure sdk-audit feature. RELOCATED from the monolith's
 * `AuditEventService` (which tangled JDBC transactions, MessageDigest hashing, the chain math AND
 * the Ktor/SSE concerns in one JVM class). The re-arch keeps:
 *  - the APPEND + chain + verify ALGORITHM in sdk-audit's [AuditRecorder] (pure-KMP, testable);
 *  - the STORAGE behind the Mongo-backed [AuditStore] (server-persistence);
 *  - the live FAN-OUT in sdk-audit's [AuditBroadcast];
 * and adds ONLY the two JVM-app concerns that can't live in a pure module:
 *  - the background periodic chain-verify coroutine (writes its result back into the chain so
 *    integrity surfaces in the regular feed even when nobody clicks "Re-verify");
 *  - convenience read helpers the routes use to serve the `/api/audit/...` endpoints.
 *
 * `record` NEVER throws (delegates to the recorder's swallow-and-return-null contract) — an audit
 * write must not break the business operation it records.
 */
class ServerAuditService(
    private val recorder: AuditRecorder,
    private val store: AuditStore,
    private val broadcast: AuditBroadcast,
) {

    private val log = LoggerFactory.getLogger(ServerAuditService::class.java)

    /** Record one event; returns it or null on failure. */
    suspend fun record(
        action: String,
        entity: String,
        entityId: String? = null,
        payloadJson: String? = null,
        actor: AuditActor = AuditActor.unknown(),
    ): AuditEvent? = recorder.record(action, entity, entityId, payloadJson, actor)

    /** Live SSE stream of freshly-appended events (best-effort; bootstrap history via [listEvents]). */
    fun stream(): SharedFlow<AuditEvent> = broadcast.subscribe()

    /** Newest-first page of persisted events, optionally narrowed to one actor subject. */
    suspend fun listEvents(limit: Int = 100, actor: String? = null): List<AuditEvent> {
        // AuditStoreImpl interprets the "actor" filter key as an equality on actor.subject.
        val filter = if (actor != null) mapOf("actor" to actor) else emptyMap()
        return store.listBySeq(
            PageRequest(
                page = 0,
                size = limit.coerceIn(1, 500),
                sort = listOf(SortSpec("seq", SortDir.DESC)),
                filter = filter,
            ),
        ).items
    }

    /** Walk a contiguous slice `[fromSeq, toSeq]` of the chain and confirm its integrity. */
    suspend fun verifyChain(fromSeq: Long? = null, toSeq: Long? = null): VerifyResult =
        recorder.verifyChain(fromSeq, toSeq)

    /**
     * Background chain-integrity walker (relocated from the monolith). Walks the chain every
     * [intervalHours] hours and persists the result as an `audit.chain_verified` /
     * `audit.chain_broken` row so integrity issues surface in the regular feed. Errors are
     * swallowed so a single transient DB hiccup can't kill the loop. Floor of 5 minutes guards
     * against a misconfigured env var turning the full-table walk into a hot loop.
     */
    fun startPeriodicVerify(scope: CoroutineScope, intervalHours: Int = 6) {
        val intervalMs = maxOf(maxOf(intervalHours, 1) * 3_600_000L, 5 * 60 * 1000L)
        scope.launch {
            while (isActive) {
                try {
                    delay(intervalMs)
                    val result = verifyChain()
                    val payload = JsonObject(
                        mapOf(
                            "ok" to JsonPrimitive(result.ok),
                            "eventsChecked" to JsonPrimitive(result.eventsChecked),
                            "breakAtSeq" to (result.breakAtSeq?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                            "reason" to (result.reason?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                        ),
                    )
                    record(
                        action = if (result.ok) "audit.chain_verified" else "audit.chain_broken",
                        entity = "audit_chain",
                        entityId = null,
                        payloadJson = payload.toString(),
                        actor = AuditActor.system("scheduler"),
                    )
                    if (!result.ok) {
                        log.error("audit.chain_broken at seq={} reason={}", result.breakAtSeq, result.reason)
                    }
                } catch (t: Throwable) {
                    log.warn("audit.periodic_verify_failed: {}", t.message)
                }
            }
        }
    }
}
