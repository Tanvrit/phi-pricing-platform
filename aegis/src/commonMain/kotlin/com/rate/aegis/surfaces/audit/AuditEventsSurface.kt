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
import com.rate.aegis.business.calculator.api.AuditVerify
import com.rate.aegis.business.calculator.api.IdempotencyRow
import com.rate.aegis.components.*
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

    // Chain integrity check — operator-initiated only. Fires once on first
    // composition and can be re-fired via the card's "Re-verify" button.
    // NEVER polled: the verify endpoint walks the whole chain server-side
    // and is too expensive for the 30s refresh cadence used by the events
    // list. `verifyTrigger` is a bump-counter keying the LaunchedEffect so
    // the operator can re-fire on demand.
    var verifyResult by remember { mutableStateOf<AuditVerify?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var verifyTrigger by remember { mutableStateOf(0) }

    // Idempotency cache snapshot — fetched once on composition. Same 403-→-WARN
    // contract as the chain-integrity card (the route is gated by `audit.verify`).
    var idemRows by remember { mutableStateOf<List<IdempotencyRow>>(emptyList()) }
    var idemError by remember { mutableStateOf<String?>(null) }
    var idemLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(client, verifyTrigger) {
        verifying = true
        runCatching { client.verifyAuditChain() }
            .onSuccess { v ->
                verifyResult = v
                verifyError = null
            }
            .onFailure { t ->
                verifyResult = null
                verifyError = t.message ?: t::class.simpleName ?: "unknown error"
            }
        verifying = false
    }

    LaunchedEffect(client) {
        runCatching { client.listIdempotencyKeys(limit = 100) }
            .onSuccess { r ->
                idemRows = r
                idemError = null
                idemLoaded = true
            }
            .onFailure { t ->
                idemError = t.message ?: t::class.simpleName ?: "unknown error"
                idemLoaded = true
            }
    }

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

        ChainIntegrityCard(
            verifying = verifying,
            verifyResult = verifyResult,
            verifyError = verifyError,
            onReverify = { if (!verifying) verifyTrigger++ },
        )

        IdempotencyCacheCard(
            rows = idemRows,
            error = idemError,
            loaded = idemLoaded,
        )

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
                if (row.action == "plan.upserted") {
                    val parsed = remember(row.payloadJson) {
                        row.payloadJson?.let {
                            runCatching { Json.parseToJsonElement(it) }
                                .getOrNull() as? JsonObject
                        }
                    }
                    if (parsed == null) {
                        Text("(no payload)", fontSize = 13.sp, color = AegisColors.textSecondary)
                    } else {
                        val isCreate =
                            parsed["isCreate"]?.jsonPrimitive?.booleanOrNull ?: false
                        val planId = parsed["planId"]?.jsonPrimitive?.contentOrNull
                        val changes = parsed["changes"] as? JsonObject
                        PlanDiffView(planId = planId, isCreate = isCreate, changes = changes)
                    }
                } else {
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
}

/**
 * Top-of-surface "Chain integrity" card. Renders the result of one
 * `GET /api/audit/verify` call (fires on open, re-fires on operator click).
 *
 * Error rendering rules:
 *  - 403 (scope-gated): WARN callout — the surface is reachable but the
 *    operator hasn't been granted the `audit.verify` scope yet. The error
 *    string returned by [ApiClient.verifyAuditChain] leads with
 *    "HTTP 403 Forbidden" plus the server's `{errorCode, scope, identity}`
 *    JSON body verbatim — we substring-detect "403" to pick the WARN tone.
 *  - Anything else (network, 5xx): DANGER — surface unreachable / server bug.
 */
@Composable
private fun ChainIntegrityCard(
    verifying: Boolean,
    verifyResult: AuditVerify?,
    verifyError: String?,
    onReverify: () -> Unit,
) {
    AegisCard(
        title = "Chain integrity",
        subtitle = "Walks the hash chain server-side and confirms no row has been tampered with.",
        action = {
            AegisButton(
                label = if (verifying) "Verifying…" else "Re-verify",
                onClick = onReverify,
                variant = AegisButtonVariant.Secondary,
                size = AegisButtonSize.Sm,
                loading = verifying,
                enabled = !verifying,
            )
        },
    ) {
        when {
            verifying && verifyResult == null && verifyError == null -> {
                Text(
                    "Verifying chain… walking every audit row server-side.",
                    fontSize = 13.sp,
                    color = AegisColors.textSecondary,
                )
            }
            verifyError != null -> {
                // 403 is an expected pre-bootstrap state (operator hasn't been
                // granted `audit.verify` scope yet), not a tamper signal —
                // route it to WARN so the operator sees actionable guidance
                // instead of a red alarm.
                val is403 = verifyError.contains("403")
                AegisCallout(
                    kind = if (is403) CalloutKind.WARN else CalloutKind.DANGER,
                    title = if (is403)
                        "Insufficient scope (403)"
                    else
                        "Chain verification unavailable",
                    body = if (is403)
                        "The current operator identity does not have the `audit.verify` scope. " +
                                "Set your identity in Settings or ask an admin to grant the scope. " +
                                "Detail: $verifyError"
                    else
                        "Could not run the chain check: $verifyError",
                )
            }
            verifyResult != null && verifyResult.ok -> {
                AegisCallout(
                    kind = CalloutKind.SUCCESS,
                    title = "Chain integrity verified",
                    body = "${verifyResult.rowsChecked} rows checked — every prev_hash matches the " +
                            "previous row's this_hash. No tampering detected.",
                )
            }
            verifyResult != null && !verifyResult.ok -> {
                val breakAt = verifyResult.breakAtId?.let { "#$it" } ?: "(unknown id)"
                val reason = verifyResult.reason ?: "(no reason returned)"
                AegisCallout(
                    kind = CalloutKind.DANGER,
                    title = "Chain integrity FAILED — break at row $breakAt",
                    body = "Checked ${verifyResult.rowsChecked} rows before the break. Reason: $reason. " +
                            "Treat this as a tamper signal: do NOT trust newer rows until the chain is reconciled.",
                )
            }
            else -> {
                Text(
                    "No verification has run yet.",
                    fontSize = 13.sp,
                    color = AegisColors.textSecondary,
                )
            }
        }
    }
}

/**
 * "Idempotency cache" diagnostic card. Fetches `/api/audit/idempotency` once
 * on composition (no polling — the cache mutates only on writes, and operators
 * already get a refresh on surface re-entry). Mirrors [ChainIntegrityCard]'s
 * 403-→-WARN routing so an operator without the `audit.verify` scope gets a
 * "Insufficient scope" callout instead of a red alarm.
 */
@Composable
private fun IdempotencyCacheCard(
    rows: List<IdempotencyRow>,
    error: String?,
    loaded: Boolean,
) {
    AegisCard(
        title = "Idempotency cache",
        subtitle = "Recent client-replay-protection entries — read-only snapshot. " +
                "Fetched once on surface open; re-enter to refresh.",
    ) {
        when {
            !loaded -> Text(
                "Loading idempotency cache snapshot…",
                fontSize = 13.sp,
                color = AegisColors.textSecondary,
            )
            error != null -> {
                val is403 = error.contains("403")
                AegisCallout(
                    kind = if (is403) CalloutKind.WARN else CalloutKind.DANGER,
                    title = if (is403) "Insufficient scope (403)"
                            else "Idempotency listing unavailable",
                    body = if (is403)
                        "The current operator identity does not have the `audit.verify` scope " +
                                "(reused for this diagnostic). Set your identity in Settings or ask an admin " +
                                "to grant the scope. Detail: $error"
                    else
                        "Could not fetch the idempotency cache: $error",
                )
            }
            rows.isEmpty() -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Cache is empty",
                body = "No idempotency-key entries to display. Entries are recorded only for " +
                        "writes that carry an `Idempotency-Key` header and expire after 24h.",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                AegisCallout(
                    kind = CalloutKind.SUCCESS,
                    title = "Live data",
                    body = "${rows.size} entries — newest first.",
                )
                AegisTable(
                    items = rows,
                    columns = listOf(
                        AegisColumn<IdempotencyRow>(
                            header = "Key", weight = 1.1f, mono = true,
                            cell = {
                                Text(
                                    it.key.take(12) + if (it.key.length > 12) "…" else "",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AegisColors.textBody,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Route", weight = 1.6f,
                            cell = {
                                Text(it.routeKey, fontSize = 13.sp, color = AegisColors.textBody)
                            }
                        ),
                        AegisColumn(
                            header = "Req hash", weight = 0.7f, mono = true,
                            cell = {
                                Text(
                                    it.requestHash.take(8),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AegisColors.textSecondary,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Status", weight = 0.5f, mono = true,
                            cell = {
                                Text(
                                    if (it.responseStatus == 0) "—" else it.responseStatus.toString(),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AegisColors.textBody,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Created", weight = 0.7f,
                            cell = {
                                Text(
                                    extractHms(it.createdAt),
                                    fontSize = 13.sp,
                                    color = AegisColors.textSecondary,
                                )
                            }
                        ),
                        AegisColumn(
                            header = "Expires", weight = 0.7f,
                            cell = {
                                Text(
                                    extractHms(it.expiresAt),
                                    fontSize = 13.sp,
                                    color = AegisColors.textSecondary,
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
 * Pulls `HH:MM:SS` out of an ISO-8601 instant (e.g. `2026-05-22T10:42:11Z` →
 * `10:42:11`). Returns the raw string if the input doesn't look ISO-shaped —
 * we'd rather show something legible than a blank cell on a malformed value.
 */
private fun extractHms(iso: String): String {
    val t = iso.indexOf('T')
    if (t < 0 || t + 9 > iso.length) return iso
    return iso.substring(t + 1, t + 9)
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

/** Max display length for one side of a diff value before truncation. */
private const val DIFF_VALUE_MAX = 50

/**
 * Renders a structured `plan.upserted` audit payload as a field-by-field diff.
 *
 * For creates we show a single "Created" badge — every field would otherwise
 * be "(none) -> <value>", which buries the signal. For updates we list only
 * the fields whose values actually changed; the server already filters those
 * out, so an empty changes object renders as "No field changes" (which can
 * happen when a write is functionally a no-op).
 */
@Composable
private fun PlanDiffView(planId: String?, isCreate: Boolean, changes: JsonObject?) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Plan ${planId ?: "?"}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textBody
            )
            Spacer(Modifier.width(AegisSpacing.s2))
            Text(
                if (isCreate) "Created" else "Updated",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCreate) AegisColors.brand else AegisColors.textSecondary
            )
        }
        // The server keys creates with a marker; drop it before counting "real" changes.
        val realChanges = changes?.entries
            ?.filter { it.key != "__create" }
            .orEmpty()
        when {
            isCreate -> Text(
                "New plan record — no prior version to diff against.",
                fontSize = 12.sp,
                color = AegisColors.textSecondary
            )
            realChanges.isEmpty() -> Text(
                "No field changes recorded.",
                fontSize = 12.sp,
                color = AegisColors.textSecondary
            )
            else -> realChanges.forEach { (field, entry) ->
                val obj = entry as? JsonObject
                DiffRow(field = field, old = obj?.get("old"), new = obj?.get("new"))
            }
        }
    }
}

/**
 * One diff row: field name on the left, "old -> new" rendered in mono on the
 * right. Long values (list serializations) are truncated to keep the drawer
 * scannable; the raw payload is still searchable from the audit table.
 */
@Composable
private fun DiffRow(field: String, old: JsonElement?, new: JsonElement?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            field,
            fontSize = 12.sp,
            color = AegisColors.textSecondary,
            modifier = Modifier.padding(end = AegisSpacing.s3)
        )
        Text(
            "${renderDiffValue(old)} → ${renderDiffValue(new)}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AegisColors.textBody
        )
    }
}

/**
 * Render a diff side as a compact string. Primitives are unquoted (so a
 * string "LIVE" reads as `LIVE`, not `"LIVE"`); structured values fall back
 * to their JSON encoding. Truncated past [DIFF_VALUE_MAX] chars with an
 * ellipsis suffix so wide list serializations don't wrap forever.
 */
private fun renderDiffValue(el: JsonElement?): String {
    if (el == null) return "(none)"
    val raw = when (el) {
        is JsonPrimitive -> el.content
        else -> el.toString()
    }
    return if (raw.length > DIFF_VALUE_MAX) raw.take(DIFF_VALUE_MAX - 1) + "…" else raw
}
