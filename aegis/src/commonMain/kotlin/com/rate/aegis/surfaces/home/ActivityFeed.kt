package com.rate.aegis.surfaces.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisHDivider
import com.rate.aegis.data.AuditEventDto
import com.rate.aegis.data.openAuditStream
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * Live-ish activity feed for the Aegis Home dashboard.
 *
 * Polls `GET /api/audit/events?limit=N` every 5 seconds and renders the most
 * recent rows inline. We chose polling over server-sent events because the
 * Ktor SSE + Compose-WASM streaming dependency story is non-trivial, and an
 * audit feed is not sub-second sensitive — 5s is functionally indistinguishable.
 *
 * The card shows a small status dot (top-right):
 *   • LOADING — grey (slate6) until the first response lands
 *   • LIVE    — success500 green when the last poll succeeded
 *   • OFFLINE — warn500 amber when the last poll threw (we keep showing the
 *               previous snapshot rather than blanking the list).
 *
 * TODO(next iteration): clicking a row should deep-link into AuditEventsSurface
 * with that row pre-selected. Wired as a no-op for now.
 */
@Composable
fun ActivityFeed(maxRows: Int = 6) {
    val client = rememberApiClient()
    var events by remember { mutableStateOf<List<Map<String, JsonElement>>>(emptyList()) }
    var status by remember { mutableStateOf(FeedStatus.LOADING) }

    val refreshTick by LocalRefreshTicker.current
    LaunchedEffect(client, refreshTick) {
        while (coroutineContext.isActive) {
            runCatching { client.getAuditEvents(limit = maxRows) }
                .onSuccess {
                    events = it
                    status = FeedStatus.LIVE
                }
                .onFailure {
                    status = FeedStatus.OFFLINE
                }
            delay(5_000L)
        }
    }

    // Best-effort SSE upgrade. On platforms that can stream
    // (WASM via the browser `EventSource`) this prepends each new audit row
    // immediately, dropping the perceived latency from ≤5 s to near-zero.
    // On JVM the factory returns null and we fall through — the polling
    // LaunchedEffect above is the sole live source.
    //
    // We keep polling running even with SSE active so a transient disconnect
    // is automatically backfilled on the next 5 s tick; no explicit reconnect
    // wiring needed here.
    LaunchedEffect(client) {
        val stream = openAuditStream(client.baseUrl) ?: return@LaunchedEffect
        stream.collect { dto ->
            val incoming = dto.toEventMap()
            events = (listOf(incoming) + events.filterNot { it.eventId() == dto.id })
                .take(maxRows)
            status = FeedStatus.LIVE
        }
    }

    AegisCard(
        title = "Activity · live",
        subtitle = "Operator writes (plan upserts, quote saves, imports) appear here within 5 s.",
        action = { StatusDot(status) }
    ) {
        if (events.isEmpty()) {
            Text(
                "No activity yet. Operator changes appear here in near-real-time.",
                fontSize = 13.sp,
                color = AegisColors.textSecondary
            )
        } else {
            Column(Modifier.fillMaxWidth()) {
                events.take(maxRows).forEachIndexed { index, ev ->
                    if (index > 0) AegisHDivider()
                    ActivityRow(ev)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(ev: Map<String, JsonElement>) {
    val action = ev.str("action") ?: "—"
    val resourceType = ev.str("resourceType") ?: "—"
    val resourceId = ev.str("resourceId")
    val actor = ev.str("actorSubject") ?: "unknown"
    val eventAt = ev.str("eventAt") ?: ""
    val accent = accentFor(action)
    val resourceSummary = if (resourceId.isNullOrBlank()) resourceType else "$resourceType $resourceId"

    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .padding(vertical = AegisSpacing.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
        Spacer(Modifier.width(AegisSpacing.s3))
        Text(
            action,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
            modifier = Modifier.width(160.dp)
        )
        Spacer(Modifier.width(AegisSpacing.s3))
        Text(
            resourceSummary,
            fontSize = 13.sp,
            color = AegisColors.textBody,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(AegisSpacing.s3))
        Text(
            "by $actor",
            fontSize = 12.sp,
            color = AegisColors.textSecondary
        )
        Spacer(Modifier.width(AegisSpacing.s3))
        Text(
            sliceClock(eventAt),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AegisColors.textTertiary
        )
    }
}

@Composable
private fun StatusDot(status: FeedStatus) {
    val color = when (status) {
        FeedStatus.LIVE -> AegisColors.success500
        FeedStatus.OFFLINE -> AegisColors.warn500
        FeedStatus.LOADING -> AegisColors.slate6
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

private enum class FeedStatus { LOADING, LIVE, OFFLINE }

/**
 * Map an action verb onto a semantic accent dot. Anything not recognised falls
 * back to the brand colour so new server-side actions still look intentional.
 */
@Composable
private fun accentFor(action: String): Color {
    val a = action.lowercase()
    return when {
        a.contains("delete") || a.contains("retired") || a.contains("reject") -> AegisColors.danger500
        a.contains("import") || a.contains("upsert") || a.contains("created") || a.contains("create") -> AegisColors.success500
        a.contains("update") || a.contains("change") || a.contains("edit") -> AegisColors.info500
        a.contains("quote") -> AegisColors.premium500
        else -> AegisColors.brand
    }
}

/**
 * Server stamps `eventAt` as ISO-8601 (`2026-05-22T10:42:11Z`). For an inline
 * feed only the clock time matters — slice chars 11..16 ("10:42"). Falls back
 * to an em-dash if the value is too short or not ISO-shaped.
 */
private fun sliceClock(iso: String): String {
    if (iso.length < 16) return "—"
    val t = iso.indexOf('T')
    return if (t in 0..(iso.length - 6)) iso.substring(t + 1, t + 6) else "—"
}

private fun Map<String, JsonElement>.str(k: String) = get(k)?.jsonPrimitive?.contentOrNull

/** Long id extracted from a wire row, used to de-duplicate when SSE racepoll. */
private fun Map<String, JsonElement>.eventId(): Long? =
    get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull()

/**
 * Convert an SSE-delivered [AuditEventDto] into the same `Map<String, JsonElement>`
 * shape the polling path produces, so the rendering code stays single-source.
 * `Json.encodeToJsonElement` performs a deterministic round-trip via the DTO's
 * `@Serializable` schema — no manual field-by-field mapping to drift.
 */
private val auditWireJson = Json { encodeDefaults = true }

private fun AuditEventDto.toEventMap(): Map<String, JsonElement> =
    (auditWireJson.encodeToJsonElement(AuditEventDto.serializer(), this) as JsonObject).toMap()
