package com.rate.aegis.surfaces.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.*
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Aegis AUDIT surface — newest-first listing of the hash-chained audit ledger.
 *
 * Reads `GET /api/audit/events?limit=200`, auto-refreshes every 30s (matches
 * the cadence of [rememberDashboardData]), and lets the operator drill into a
 * single row for the canonical timestamp, full hashes, and pretty-printed
 * payload. Cross-reference the `Hash` column with `/api/audit/verify` output
 * to confirm chain integrity.
 */
@Composable
fun AuditEventsSurface() {
    val client = rememberApiClient()
    var rows by remember { mutableStateOf<List<AuditRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var resourceFilter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<AuditRow?>(null) }

    LaunchedEffect(client) {
        while (coroutineContext.isActive) {
            runCatching { client.getAuditEvents(limit = 200) }
                .onSuccess { raw ->
                    rows = raw.mapNotNull { it.toAuditRow() }
                    loadError = null
                    loaded = true
                }
                .onFailure { t ->
                    loadError = t.message ?: t::class.simpleName ?: "unknown error"
                    loaded = true
                }
            delay(30_000L)
        }
    }

    val resourceTypes = remember(rows) {
        listOf("All") + rows.map { it.resourceType }.distinct().sorted()
    }
    val filtered = remember(rows, resourceFilter) {
        if (resourceFilter == "All") rows else rows.filter { it.resourceType == resourceFilter }
    }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text("Audit log", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        Text(
            "Append-only ledger — every state-changing write lands here, " +
                    "and each row's hash links to the previous one so tampering is detectable. " +
                    "Use the verify endpoint to walk the chain.",
            fontSize = 13.sp, color = AegisColors.textSecondary
        )

        when {
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Server unreachable",
                body = "Could not load audit events: $loadError. Showing the last successful snapshot " +
                        "(${rows.size} rows). Auto-retry every 30s."
            )
            !loaded -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Fetching the latest audit events from the server."
            )
            else -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "${rows.size} events loaded. Auto-refreshes every 30s."
            )
        }

        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text("Resource:", fontSize = 13.sp, color = AegisColors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    resourceTypes.forEach { rt ->
                        AegisChip(label = rt, selected = resourceFilter == rt, onClick = { resourceFilter = rt })
                    }
                }
            }
        }

        AegisCard {
            AegisTable(
                items = filtered,
                onRowClick = { selected = it },
                columns = listOf(
                    AegisColumn<AuditRow>(
                        header = "Time", weight = 1.1f,
                        cell = { Text(formatShortInstant(it.eventAt), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Action", weight = 1.2f,
                        cell = { Text(it.action, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    ),
                    AegisColumn(
                        header = "Resource", weight = 1.6f,
                        cell = {
                            val txt = it.resourceType + (it.resourceId?.let { id -> " $id" } ?: "")
                            Text(txt, fontSize = 13.sp, color = AegisColors.textBody)
                        }
                    ),
                    AegisColumn(
                        header = "Actor", weight = 0.9f,
                        cell = {
                            Text(it.actorSubject ?: "—", fontSize = 13.sp,
                                color = AegisColors.textSecondary)
                        }
                    ),
                    AegisColumn(
                        header = "Request ID", weight = 0.9f, mono = true,
                        cell = {
                            Text(
                                it.requestId?.take(8)?.plus("…") ?: "—",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AegisColors.textSecondary
                            )
                        }
                    ),
                    AegisColumn(
                        header = "Hash", weight = 0.8f, mono = true,
                        cell = {
                            Text(
                                it.thisHash.take(8),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AegisColors.textBody
                            )
                        }
                    ),
                ),
                emptyState = {
                    AegisEmptyState(
                        title = "No audit events to display",
                        helper = "Either nothing has been recorded yet, or the filter excludes every row."
                    )
                }
            )
        }
    }

    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.action ?: "Audit event",
        subtitle = selected?.let { "#${it.id} · ${it.resourceType}" },
    ) {
        selected?.let { row ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
            ) {
                Text(row.action, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                AegisHDivider()
                LedgerRow("ID", row.id.toString())
                LedgerRow("Timestamp", row.eventAt)
                LedgerRow("Action", row.action)
                LedgerRow("Resource type", row.resourceType)
                LedgerRow("Resource ID", row.resourceId ?: "—")
                LedgerRow("Actor subject", row.actorSubject ?: "—")
                LedgerRow("Actor role", row.actorRole ?: "—")
                LedgerRow("Request ID", row.requestId ?: "—", mono = true)
                AegisHDivider()
                Text("Hash chain", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary)
                LedgerRow("prev_hash", row.prevHash ?: "—", mono = true)
                LedgerRow("this_hash", row.thisHash, mono = true)
                AegisHDivider()
                Text("Payload", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary)
                val pretty = remember(row.payloadJson) { prettyJson(row.payloadJson) }
                if (pretty == null) {
                    Text("(no payload)", fontSize = 13.sp, color = AegisColors.textSecondary)
                } else {
                    Text(
                        pretty,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AegisColors.textBody
                    )
                }
            }
        }
    }
}

/** Internal client-side row — decouples the surface from server-side serialization tweaks. */
private data class AuditRow(
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
    val thisHash: String,
)

private fun Map<String, JsonElement>.toAuditRow(): AuditRow? {
    val id = get("id")?.jsonPrimitive?.longOrNull ?: return null
    val eventAt = get("eventAt")?.jsonPrimitive?.contentOrNull ?: return null
    val action = get("action")?.jsonPrimitive?.contentOrNull ?: return null
    val resourceType = get("resourceType")?.jsonPrimitive?.contentOrNull ?: return null
    val thisHash = get("thisHash")?.jsonPrimitive?.contentOrNull ?: return null
    return AuditRow(
        id = id,
        eventAt = eventAt,
        action = action,
        resourceType = resourceType,
        resourceId = get("resourceId")?.jsonPrimitive?.contentOrNull,
        actorSubject = get("actorSubject")?.jsonPrimitive?.contentOrNull,
        actorRole = get("actorRole")?.jsonPrimitive?.contentOrNull,
        requestId = get("requestId")?.jsonPrimitive?.contentOrNull,
        payloadJson = get("payloadJson")?.jsonPrimitive?.contentOrNull,
        prevHash = get("prevHash")?.jsonPrimitive?.contentOrNull,
        thisHash = thisHash
    )
}

/**
 * Best-effort short timestamp: keeps the first 16 chars of an ISO-8601 string
 * and swaps the `T` for a space, e.g. `2026-05-22T10:42:11Z` -> `2026-05-22 10:42`.
 * Falls back to the raw value if it doesn't look ISO-shaped.
 */
private fun formatShortInstant(iso: String): String {
    if (iso.length < 16) return iso
    return iso.substring(0, 16).replace('T', ' ')
}

/** Pretty-printer used in the drawer. Returns null if input is null/blank/invalid JSON. */
private val prettyJsonFormatter = Json { prettyPrint = true; isLenient = true }
private fun prettyJson(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val el = prettyJsonFormatter.parseToJsonElement(raw)
        prettyJsonFormatter.encodeToString(JsonElement.serializer(), el)
    }.getOrElse { raw }  // Fall back to raw text on parse failure rather than hiding it.
}

@Composable
private fun LedgerRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody
        )
    }
}
