package com.rate.aegis.surfaces.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.rate.aegis.DeepLink
import com.rate.aegis.LocalAegisDeepLink
import com.rate.aegis.LocalSurfaceRouter
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
    var search by remember { mutableStateOf("") }
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
    val filtered = remember(rows, resourceFilter, search) {
        rows.asSequence()
            .filter { rt -> resourceFilter == "All" || rt.resourceType == resourceFilter }
            .filter { rt -> search.isBlank() || rt.matchesSearch(search) }
            .toList()
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
                body = "${filtered.size} of ${rows.size} events. Auto-refreshes every 30s."
            )
        }

        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "Search audit log",
                    helper = "Matches action, resource, actor, request id, hash, or payload contents.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text("Resource:", fontSize = 13.sp, color = AegisColors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    resourceTypes.forEach { rt ->
                        AegisChip(label = rt, selected = resourceFilter == rt, onClick = { resourceFilter = rt })
                    }
                }
            }
        }

        if (search.isNotBlank() && filtered.isEmpty()) {
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "No matches",
                body = "Adjust the search or clear it to see all events."
            )
        } else {
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
                            cell = { row ->
                                ResourceCell(
                                    resourceType = row.resourceType,
                                    resourceId = row.resourceId,
                                )
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
                ResourceIdLedgerRow(
                    resourceType = row.resourceType,
                    resourceId = row.resourceId,
                    onBeforeNavigate = { selected = null },
                )
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
 * Case-insensitive substring match across the human-relevant fields of an
 * audit row. Operates on the already-loaded snapshot — purely client-side.
 */
private fun AuditRow.matchesSearch(q: String): Boolean {
    val needle = q.trim().lowercase()
    if (needle.isEmpty()) return true
    val haystack = listOfNotNull(
        id.toString(),
        action,
        resourceType,
        resourceId,
        actorSubject,
        actorRole,
        requestId,
        prevHash,
        thisHash,
        payloadJson,
    ).joinToString(" ").lowercase()
    return needle in haystack
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

/**
 * Resource types whose ids are deep-linkable to an operator surface.
 * Anything else renders as plain text — silently degrading rather than
 * routing into the wrong place.
 */
private data class ResourceLink(val deepLink: DeepLink, val surface: AegisSurface)

private fun resolveResourceLink(resourceType: String, resourceId: String): ResourceLink? =
    when (resourceType.lowercase()) {
        "plan" -> ResourceLink(DeepLink(planId = resourceId), AegisSurface.PLAN_CONFIGURATOR)
        "quote" -> ResourceLink(DeepLink(quoteId = resourceId), AegisSurface.QUOTES)
        else -> null
    }

/**
 * Resource column cell: the type is always plain; the id is rendered as
 * a clickable underlined brand-colored link when [resolveResourceLink]
 * recognises the type, and as plain secondary text otherwise.
 */
@Composable
private fun ResourceCell(resourceType: String, resourceId: String?) {
    val deepLink = LocalAegisDeepLink.current
    val router = LocalSurfaceRouter.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(resourceType, fontSize = 13.sp, color = AegisColors.textBody)
        if (resourceId != null) {
            Spacer(Modifier.width(AegisSpacing.s2))
            val link = resolveResourceLink(resourceType, resourceId)
            if (link != null) {
                Text(
                    resourceId,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.brand,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        deepLink.value = link.deepLink
                        router(link.surface)
                    }
                )
            } else {
                Text(
                    resourceId,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.textSecondary
                )
            }
        }
    }
}

/**
 * Drawer variant of the resource-id row. Closes the drawer (via
 * [onBeforeNavigate]) before routing, so the operator doesn't return to
 * a stale drawer overlaying the destination surface.
 */
@Composable
private fun ResourceIdLedgerRow(
    resourceType: String,
    resourceId: String?,
    onBeforeNavigate: () -> Unit,
) {
    val deepLink = LocalAegisDeepLink.current
    val router = LocalSurfaceRouter.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Resource ID", fontSize = 13.sp, color = AegisColors.textSecondary)
        if (resourceId == null) {
            Text(
                "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AegisColors.textBody
            )
        } else {
            val link = resolveResourceLink(resourceType, resourceId)
            if (link != null) {
                Text(
                    resourceId,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = AegisColors.brand,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        onBeforeNavigate()
                        deepLink.value = link.deepLink
                        router(link.surface)
                    }
                )
            } else {
                Text(
                    resourceId,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = AegisColors.textBody
                )
            }
        }
    }
}
