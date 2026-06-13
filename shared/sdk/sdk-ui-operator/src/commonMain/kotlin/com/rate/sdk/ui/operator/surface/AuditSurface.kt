package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.core.base.json.AppJson
import com.rate.sdk.audit.model.AuditEvent
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisDrawer
import com.rate.sdk.ui.kit.components.AegisEmptyState
import com.rate.sdk.ui.kit.components.AegisHDivider
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.AuditReadApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Audit-log surface — relocated from aegis AuditEventsSurface. Tails the immutable, hash-chained
 * audit events from the server (read-only). The chain's integrity is provable client-side, but the
 * append path is server-only; this surface just renders the tail with seq + actor + hashes.
 *
 * On top of the raw tail this surface adds the investigator affordances an auditor actually needs:
 *  - a filter bar (free text + entity / action / actor chips + a from→to date range) with a live
 *    "Showing X of Y events" count, filtering client-side over the loaded page so it works even
 *    against a server that ignores the optional query params;
 *  - a per-event detail drawer that renders a before→after field diff when the payload carries
 *    `before`/`after` objects, and otherwise pretty-prints the payload in a bounded scroller;
 *  - a CSV export of the currently-filtered events, copied to the host clipboard (with a
 *    selectable fallback panel in case the platform clipboard write is unavailable).
 */
@Composable
fun AuditSurface(audit: AuditReadApi, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var events by remember { mutableStateOf<List<AuditEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Filter state
    var search by remember { mutableStateOf("") }
    var entityFilter by remember { mutableStateOf<String?>(null) }
    var actionFilter by remember { mutableStateOf<String?>(null) }
    var actorFilter by remember { mutableStateOf<String?>(null) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }

    var selected by remember { mutableStateOf<AuditEvent?>(null) }
    var exportCsv by remember { mutableStateOf<String?>(null) }
    var exportCopied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { audit.recent(limit = 200) }
                .onSuccess { events = it; error = null }
                .onFailure { error = it.message ?: "Server unreachable" }
            loading = false
        }
    }

    // Distinct facet values for the chip rows, derived from the loaded page.
    val entityFacets = remember(events) { events.map { it.entity }.distinct().sorted() }
    val actionFacets = remember(events) { events.map { it.action }.distinct().sorted() }
    val actorFacets = remember(events) {
        events.map { it.actor.subject ?: "system" }.distinct().sorted()
    }

    val filtered = remember(events, search, entityFilter, actionFilter, actorFilter, fromDate, toDate) {
        val from = fromDate.trim()
        val to = toDate.trim()
        events.filter { e ->
            val actorName = e.actor.subject ?: "system"
            val matchesSearch = search.isBlank() ||
                e.action.contains(search, true) ||
                e.entity.contains(search, true) ||
                (e.entityId?.contains(search, true) == true) ||
                actorName.contains(search, true) ||
                e.hash.contains(search, true) ||
                e.seq.toString() == search.trim()
            val matchesEntity = entityFilter == null || e.entity == entityFilter
            val matchesAction = actionFilter == null || e.action == actionFilter
            val matchesActor = actorFilter == null || actorName == actorFilter
            // Date range is an ISO-date prefix compare on the event instant ("2026-06-10…").
            val day = e.at.toString().take(10)
            val matchesFrom = from.isEmpty() || day >= from
            val matchesTo = to.isEmpty() || day <= to
            matchesSearch && matchesEntity && matchesAction && matchesActor && matchesFrom && matchesTo
        }
    }

    val anyFilterActive = search.isNotBlank() || entityFilter != null || actionFilter != null ||
        actorFilter != null || fromDate.isNotBlank() || toDate.isNotBlank()

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text("Audit Log", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "Append-only, hash-chained record of every config change and quote action.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        error?.let { AegisCallout(kind = CalloutKind.WARN, title = "Could not load the audit chain", body = it) }

        // ── Filter bar ──────────────────────────────────────────────────────
        AegisCard(
            title = "Filters",
            subtitle = "Showing ${filtered.size} of ${events.size} events",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    if (anyFilterActive) {
                        AegisButton(
                            label = "Clear",
                            onClick = {
                                search = ""; entityFilter = null; actionFilter = null
                                actorFilter = null; fromDate = ""; toDate = ""
                            },
                            variant = AegisButtonVariant.Ghost,
                            size = AegisButtonSize.Sm,
                        )
                    }
                    AegisButton(
                        label = "Export CSV",
                        onClick = {
                            val csv = buildCsv(filtered)
                            exportCsv = csv
                            exportCopied = runCatching {
                                clipboard.setText(AnnotatedString(csv)); true
                            }.getOrDefault(false)
                        },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                        enabled = filtered.isNotEmpty(),
                    )
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "Search by action, entity, entity id, actor, seq, or hash",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisInput(
                        value = fromDate,
                        onValueChange = { fromDate = it },
                        label = "From (YYYY-MM-DD)",
                        placeholder = "2026-06-01",
                        modifier = Modifier.weight(1f),
                    )
                    AegisInput(
                        value = toDate,
                        onValueChange = { toDate = it },
                        label = "To (YYYY-MM-DD)",
                        placeholder = "2026-06-30",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (entityFacets.isNotEmpty()) {
                    FacetRow(
                        label = "Entity",
                        values = entityFacets,
                        selected = entityFilter,
                        onSelect = { entityFilter = if (entityFilter == it) null else it },
                    )
                }
                if (actionFacets.isNotEmpty()) {
                    FacetRow(
                        label = "Action",
                        values = actionFacets,
                        selected = actionFilter,
                        onSelect = { actionFilter = if (actionFilter == it) null else it },
                    )
                }
                if (actorFacets.isNotEmpty()) {
                    FacetRow(
                        label = "Actor",
                        values = actorFacets,
                        selected = actorFilter,
                        onSelect = { actorFilter = if (actorFilter == it) null else it },
                    )
                }
            }
        }

        exportCsv?.let { csv ->
            AegisCard(
                title = if (exportCopied) "Copied ${filtered.size} events to clipboard" else "Export CSV",
                subtitle = if (exportCopied) "Paste into a spreadsheet, or copy the text below."
                else "Clipboard was unavailable — select the text below and copy it manually.",
                action = {
                    AegisButton(
                        label = "Dismiss",
                        onClick = { exportCsv = null; exportCopied = false },
                        variant = AegisButtonVariant.Ghost,
                        size = AegisButtonSize.Sm,
                    )
                },
            ) {
                if (exportCopied) {
                    AegisCallout(
                        kind = CalloutKind.SUCCESS,
                        title = "On your clipboard",
                        body = "${filtered.size} filtered events as CSV, ready to paste.",
                    )
                } else {
                    SelectionContainer {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .background(AegisColors.slate2, AegisRadii.shapeMd)
                                .border(1.dp, AegisColors.border, AegisRadii.shapeMd)
                                .padding(AegisSpacing.s3),
                        ) {
                            Text(csv, style = AegisTypography.mono14.copy(color = AegisColors.textBody))
                        }
                    }
                }
            }
        }

        AegisCard(title = "Recent events") {
            AegisTable(
                items = filtered,
                onRowClick = { selected = it },
                columns = listOf(
                    AegisColumn<AuditEvent>(header = "Seq", weight = 0.5f, align = TextAlign.End, mono = true) {
                        Text(it.seq.toString(), style = AegisTypography.mono14)
                    },
                    AegisColumn(header = "At", weight = 1.1f) {
                        Text(it.at.toString().take(19).replace('T', ' '),
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "Action", weight = 1.3f) {
                        Text(it.action, style = AegisTypography.body.copy(color = AegisColors.textBody))
                    },
                    AegisColumn(header = "Entity", weight = 0.9f) {
                        Text(it.entity, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Entity id", weight = 1.2f) {
                        Text(it.entityId ?: "—", style = AegisTypography.mono14.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "Actor", weight = 1.0f) {
                        Text(
                            it.actor.subject ?: "system",
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                        )
                    },
                    AegisColumn(header = "Hash", weight = 1.2f, mono = true) {
                        Text(it.hash.take(12) + "…", style = AegisTypography.mono14.copy(color = AegisColors.textTertiary))
                    },
                ),
                emptyState = {
                    AegisEmptyState(
                        title = when {
                            loading -> "Loading audit chain…"
                            events.isEmpty() -> "No audit events yet"
                            else -> "No events match your filters"
                        },
                        helper = when {
                            loading -> "Tailing the latest 200 immutable records."
                            events.isEmpty() -> "Config changes and quote actions will show up here."
                            else -> "Adjust the chips above, widen the date range, or clear the search box."
                        },
                    )
                },
            )
        }
    }

    AuditDetailDrawer(selected) { selected = null }
}

/** A label + a wrapping row of selectable facet chips (single-select, click again to clear). */
@Composable
private fun FacetRow(
    label: String,
    values: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Text(
            label,
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
        )
        // FlowRow isn't in the kit; a horizontally-scrolling row keeps it bounded and
        // avoids nesting an unbounded layout inside the page's verticalScroll.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
        ) {
            values.forEach { v ->
                AegisChip(label = v, selected = selected == v, onClick = { onSelect(v) })
            }
        }
    }
}

/**
 * Event detail + before→after diff drawer. If the payload is a JSON object carrying `before` and
 * `after` objects, render a per-key old→new diff (only changed/added/removed keys). Otherwise
 * pretty-print the whole payload. Both render inside a bounded-height scroller.
 */
@Composable
private fun AuditDetailDrawer(event: AuditEvent?, onClose: () -> Unit) {
    AegisDrawer(
        open = event != null,
        onClose = onClose,
        title = event?.action ?: "Audit event",
        subtitle = event?.let { "#${it.seq} · ${it.entity}${it.entityId?.let { id -> " · $id" } ?: ""}" },
    ) {
        event?.let { e ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                MetaRow("Sequence", "#${e.seq}")
                MetaRow("At", e.at.toString().take(19).replace('T', ' '))
                MetaRow("Action", e.action)
                MetaRow("Entity", e.entity)
                MetaRow("Entity id", e.entityId ?: "—")
                MetaRow("Actor", e.actor.subject ?: "system")
                e.actor.role?.let { MetaRow("Role", it) }
                e.actor.requestId?.let { MetaRow("Request id", it) }
                MetaRow("Prev hash", e.prevHash.take(20) + if (e.prevHash.length > 20) "…" else "")
                MetaRow("Hash", e.hash.take(20) + if (e.hash.length > 20) "…" else "")

                AegisHDivider()

                val diff = remember(e.payloadJson) { computeDiff(e.payloadJson) }
                when (diff) {
                    is PayloadView.Diff -> {
                        Text(
                            "Changes",
                            style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
                        )
                        if (diff.rows.isEmpty()) {
                            Text(
                                "No field-level changes recorded.",
                                style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                            )
                        } else {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                            ) {
                                diff.rows.forEach { DiffRow(it) }
                            }
                        }
                    }
                    is PayloadView.Pretty -> {
                        Text(
                            "Payload",
                            style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
                        )
                        SelectionContainer {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(AegisColors.slate2, AegisRadii.shapeMd)
                                    .border(1.dp, AegisColors.border, AegisRadii.shapeMd)
                                    .padding(AegisSpacing.s3),
                            ) {
                                Text(
                                    diff.text,
                                    style = AegisTypography.mono14.copy(color = AegisColors.textBody),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
        SelectionContainer {
            Text(value, style = AegisTypography.body.copy(color = AegisColors.textBody))
        }
    }
}

@Composable
private fun DiffRow(row: DiffEntry) {
    val (toneBg, toneBorder, tag) = when (row.kind) {
        DiffKind.Added -> Triple(AegisColors.success50, AegisColors.success500, "added")
        DiffKind.Removed -> Triple(AegisColors.danger50, AegisColors.danger500, "removed")
        DiffKind.Changed -> Triple(AegisColors.warn50, AegisColors.warn500, "changed")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(toneBg, AegisRadii.shapeMd)
            .border(1.dp, toneBorder, AegisRadii.shapeMd)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.key,
                style = AegisTypography.body.copy(
                    color = AegisColors.textPrimary,
                    fontWeight = AegisTypography.h3.fontWeight,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .background(AegisColors.surface, AegisRadii.shapePill)
                    .border(1.dp, toneBorder, AegisRadii.shapePill)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .wrapContentWidth(),
            ) {
                Text(tag.uppercase(), style = AegisTypography.micro.copy(color = AegisColors.textSecondary))
            }
        }
        if (row.kind != DiffKind.Added) {
            SelectionContainer {
                Text(
                    "− ${row.before ?: "null"}",
                    style = AegisTypography.mono14.copy(color = AegisColors.danger700),
                )
            }
        }
        if (row.kind != DiffKind.Removed) {
            SelectionContainer {
                Text(
                    "+ ${row.after ?: "null"}",
                    style = AegisTypography.mono14.copy(color = AegisColors.success700),
                )
            }
        }
    }
}

// ── Pure-KMP helpers ───────────────────────────────────────────────────────

private enum class DiffKind { Added, Removed, Changed }

private data class DiffEntry(
    val key: String,
    val kind: DiffKind,
    val before: String?,
    val after: String?,
)

private sealed interface PayloadView {
    data class Diff(val rows: List<DiffEntry>) : PayloadView
    data class Pretty(val text: String) : PayloadView
}

/** Pretty-printing instance — reuses [AppJson] flags so decode parity with the wire holds. */
private val prettyJson: Json = Json(from = AppJson.json) { prettyPrint = true }

/**
 * Inspect a canonical payload string: if it is an object holding `before`+`after` objects, build a
 * per-key diff; otherwise return a pretty-printed view. Parse failures fall back to the raw string.
 */
private fun computeDiff(payloadJson: String): PayloadView {
    val element: JsonElement = runCatching { AppJson.json.parseToJsonElement(payloadJson) }
        .getOrElse { return PayloadView.Pretty(payloadJson) }

    val obj = element as? JsonObject ?: return PayloadView.Pretty(pretty(element, payloadJson))
    val before = obj["before"] as? JsonObject
    val after = obj["after"] as? JsonObject
    if (before == null && after == null) {
        return PayloadView.Pretty(pretty(element, payloadJson))
    }

    val b = before ?: JsonObject(emptyMap())
    val a = after ?: JsonObject(emptyMap())
    val keys = (b.keys + a.keys).distinct().sorted()
    val rows = buildList {
        keys.forEach { k ->
            val bv = b[k]
            val av = a[k]
            when {
                bv == null && av != null -> add(DiffEntry(k, DiffKind.Added, null, render(av)))
                bv != null && av == null -> add(DiffEntry(k, DiffKind.Removed, render(bv), null))
                bv != null && av != null && bv != av ->
                    add(DiffEntry(k, DiffKind.Changed, render(bv), render(av)))
                // unchanged → omit
            }
        }
    }
    return PayloadView.Diff(rows)
}

/** Pretty-print [element]; if re-serialisation throws, fall back to [raw]. */
private fun pretty(element: JsonElement, raw: String): String =
    runCatching { prettyJson.encodeToString(JsonElement.serializer(), element) }.getOrDefault(raw)

/** Human-readable scalar rendering — unquote primitives, pretty-print nested structures. */
private fun render(value: JsonElement): String = when (value) {
    is JsonNull -> "null"
    is JsonPrimitive -> value.content
    else -> runCatching { prettyJson.encodeToString(JsonElement.serializer(), value) }
        .getOrDefault(value.toString())
}

/** Serialise the filtered events to RFC-4180-ish CSV (commas/quotes/newlines escaped). */
private fun buildCsv(events: List<AuditEvent>): String {
    val header = listOf("seq", "at", "action", "entity", "entityId", "actor", "role", "prevHash", "hash", "payload")
    val sb = StringBuilder()
    sb.append(header.joinToString(",") { csvCell(it) })
    sb.append('\n')
    events.forEach { e ->
        val cells = listOf(
            e.seq.toString(),
            e.at.toString(),
            e.action,
            e.entity,
            e.entityId ?: "",
            e.actor.subject ?: "",
            e.actor.role ?: "",
            e.prevHash,
            e.hash,
            e.payloadJson,
        )
        sb.append(cells.joinToString(",") { csvCell(it) })
        sb.append('\n')
    }
    return sb.toString()
}

/** Quote a CSV cell when it contains a comma, quote, or newline; double internal quotes. */
private fun csvCell(raw: String): String {
    val needsQuote = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuote) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
}
