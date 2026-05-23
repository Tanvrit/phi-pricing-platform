package com.rate.aegis.surfaces.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisColumn
import com.rate.aegis.components.AegisEmptyState
import com.rate.aegis.components.AegisTable
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * "My audit trail" — a slim, identity-scoped view of the hash-chained ledger.
 *
 * Reads `GET /api/audit/events?limit=50&actor=<operatorIdentity>` — the server
 * does the narrowing so the wire payload is exactly what this view renders
 * (no client-side filter pass over the global 500-row feed like
 * [AuditEventsSurface] does). When the operator hasn't set an identity in
 * Settings yet we degrade gracefully to an info callout — calling the
 * endpoint with `actor=""` would be a no-op anyway (the server coerces blank
 * to null and returns the global feed, which is the opposite of what this
 * view is for).
 *
 * Visual density matches [com.rate.aegis.surfaces.home.ActivityFeed]: 4
 * compact columns (Action / Resource / Time / Hash) capped at ~5–6 visible
 * rows. Auto-refresh follows the surface-router refresh tick so a global
 * "refresh all" click keeps it in sync with surrounding cards. No SSE upgrade
 * — the existing surfaces own the live-stream wiring, and an identity-scoped
 * view is a much narrower hot path (typically one operator's own writes,
 * which they just initiated and don't need <1 s feedback on).
 *
 * Not wired into AegisRoot routing in this iteration. Drop it onto HomeSurface,
 * make it a role-specific tab, or render it in a Settings preview pane — all
 * follow-ups. The composable is purely self-contained: it owns its own
 * client, identity load, and polling effect.
 */
@Composable
fun MyAuditEvents() {
    val client = rememberApiClient()

    // Re-read identity on every composition so editing Settings takes effect
    // without rebuilding the surface tree. Cheap (a file/localStorage read);
    // mirrors the per-request lookup the ApiClient already does for the
    // `X-Aegis-Actor` header.
    val identity = remember { AegisSettingsStore.load().operatorIdentity }

    var rows by remember { mutableStateOf<List<MyAuditRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    val refreshTick by LocalRefreshTicker.current
    LaunchedEffect(client, identity, refreshTick) {
        if (identity.isBlank()) return@LaunchedEffect
        runCatching { client.getAuditEvents(limit = 50, actor = identity) }
            .onSuccess { raw ->
                rows = raw.mapNotNull { it.toMyAuditRow() }
                loadError = null
                loaded = true
            }
            .onFailure { t ->
                loadError = t.message ?: t::class.simpleName ?: "unknown error"
                loaded = true
            }
    }

    AegisCard(
        title = "My audit trail",
        subtitle = if (identity.isBlank())
            "Identity-scoped view — set your identity in Settings to populate."
        else
            "Recent ledger rows attributed to \"$identity\". Server-narrowed; up to 50 newest first.",
    ) {
        when {
            identity.isBlank() -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "No identity configured",
                body = "Set your identity in Settings to view your own audit trail.",
            )
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Could not load",
                body = "Server unreachable: $loadError. Auto-retries on the next refresh tick.",
            )
            !loaded -> Text(
                "Loading your audit trail…",
                fontSize = 13.sp,
                color = AegisColors.textSecondary,
            )
            rows.isEmpty() -> AegisEmptyState(
                title = "No events for \"$identity\" yet",
                helper = "Once you perform an audited write (plan upsert, quote save, import, etc.) " +
                        "it will appear here within the next refresh.",
            )
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                AegisTable(
                    items = rows,
                    columns = listOf(
                        AegisColumn<MyAuditRow>(
                            header = "Action", weight = 1.2f,
                            cell = {
                                Text(
                                    it.action,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AegisColors.textBody,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Resource", weight = 1.6f,
                            cell = { row ->
                                val summary = if (row.resourceId.isNullOrBlank())
                                    row.resourceType
                                else
                                    "${row.resourceType} ${row.resourceId}"
                                Text(summary, fontSize = 13.sp, color = AegisColors.textBody)
                            }
                        ),
                        AegisColumn(
                            header = "Time", weight = 1.0f,
                            cell = {
                                Text(
                                    formatShortInstantSlim(it.eventAt),
                                    fontSize = 13.sp,
                                    color = AegisColors.textSecondary,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Hash", weight = 0.7f, mono = true,
                            cell = {
                                Text(
                                    it.thisHash.take(8),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AegisColors.textBody,
                                )
                            }
                        ),
                    ),
                )
            }
        }
    }
}

/**
 * Slimmer internal row than [AuditEventsSurface]'s — only the 4 columns this
 * view renders. Kept private so this composable stays self-contained and the
 * sister surface can evolve its row shape without coupling.
 */
private data class MyAuditRow(
    val id: Long,
    val eventAt: String,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val thisHash: String,
)

private fun Map<String, JsonElement>.toMyAuditRow(): MyAuditRow? {
    val id = get("id")?.jsonPrimitive?.longOrNull ?: return null
    val eventAt = get("eventAt")?.jsonPrimitive?.contentOrNull ?: return null
    val action = get("action")?.jsonPrimitive?.contentOrNull ?: return null
    val resourceType = get("resourceType")?.jsonPrimitive?.contentOrNull ?: return null
    val thisHash = get("thisHash")?.jsonPrimitive?.contentOrNull ?: return null
    return MyAuditRow(
        id = id,
        eventAt = eventAt,
        action = action,
        resourceType = resourceType,
        resourceId = get("resourceId")?.jsonPrimitive?.contentOrNull,
        thisHash = thisHash,
    )
}

/**
 * Same approach as the sibling surface's `formatShortInstant` — first 16
 * chars of an ISO-8601 instant with `T` → space, e.g.
 * `2026-05-22T10:42:11Z` -> `2026-05-22 10:42`. Falls back to the raw value
 * on anything shorter so we never blank-cell a legit timestamp.
 */
private fun formatShortInstantSlim(iso: String): String {
    if (iso.length < 16) return iso
    return iso.substring(0, 16).replace('T', ' ')
}
