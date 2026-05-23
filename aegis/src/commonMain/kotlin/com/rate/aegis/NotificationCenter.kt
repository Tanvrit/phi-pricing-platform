package com.rate.aegis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.rate.aegis.data.AuditEventDto
import com.rate.aegis.data.openAuditStream
import com.rate.aegis.data.rememberApiClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Compact projection of a server audit event suitable for the bell-icon
 * NotificationDropdown. We don't reuse [AuditEventDto] directly because the
 * polling path on `/api/audit/events` returns raw `Map<String, JsonElement>` rows
 * (see [com.rate.aegis.business.calculator.api.ApiClient.getAuditEvents]); this
 * type collapses both shapes into the same set of fields the dropdown needs.
 */
data class Notification(
    val id: Long,
    val timestamp: String,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val actor: String?
)

/**
 * Single global subscription to the audit feed for the notification bell.
 *
 * Behaviour mirrors the existing surfaces (ActivityFeed / AuditEventsSurface):
 *  1. One-shot initial fetch via `getAuditEvents(limit = maxRetained)`.
 *  2. Best-effort SSE upgrade via [openAuditStream] — when the platform supports
 *     it (WASM today), new rows are prepended in near-real-time; on JVM the
 *     factory returns null and the list stays at whatever the initial fetch
 *     returned for this session. That matches the constraint of "DO NOT poll
 *     independently — one consumer is enough" — the existing surfaces already
 *     keep their own polling LaunchedEffects which re-prime the server-side
 *     view; the bell is purely a "since session start" affordance and trades
 *     freshness on JVM for not double-polling.
 *
 * The retained list is capped at [maxRetained] so a long-lived session that
 * sees a flurry of events doesn't grow unboundedly.
 */
@Composable
fun rememberNotifications(maxRetained: Int = 20): State<List<Notification>> {
    val client = rememberApiClient()
    val state = remember { mutableStateOf<List<Notification>>(emptyList()) }
    LaunchedEffect(client) {
        val initial = runCatching { client.getAuditEvents(limit = maxRetained) }
            .getOrElse { emptyList() }
        state.value = initial.mapNotNull { it.toNotification() }
        val stream = openAuditStream(client.baseUrl) ?: return@LaunchedEffect
        stream.collect { dto ->
            val incoming = dto.toNotification()
            // De-dupe against any row already retained (e.g. SSE races initial fetch).
            val merged = (listOf(incoming) + state.value.filterNot { it.id == incoming.id })
                .take(maxRetained)
            state.value = merged
        }
    }
    return state
}

private fun AuditEventDto.toNotification(): Notification = Notification(
    id = id,
    timestamp = eventAt,
    action = action,
    resourceType = resourceType,
    resourceId = resourceId,
    actor = actorSubject
)

/**
 * Extracts the same fields ActivityFeed pulls from the raw polling shape.
 * Returns null if `id` is missing or unparseable — every row we keep MUST
 * have a stable identity so dedup/jump-to-source works.
 */
private fun Map<String, JsonElement>.toNotification(): Notification? {
    val id = get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
    return Notification(
        id = id,
        timestamp = str("eventAt") ?: "",
        action = str("action") ?: "—",
        resourceType = str("resourceType") ?: "—",
        resourceId = str("resourceId"),
        actor = str("actorSubject"),
    )
}

private fun Map<String, JsonElement>.str(k: String): String? =
    get(k)?.jsonPrimitive?.contentOrNull
