package com.rate.sdk.ui.operator.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import com.rate.core.money.Money
import com.rate.sdk.ui.kit.components.AegisBulkBar
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisConfirmDialog
import com.rate.sdk.ui.kit.components.AegisDrawer
import com.rate.sdk.ui.kit.components.AegisEmptyState
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.AegisStatus
import com.rate.sdk.ui.kit.components.AegisStatusPill
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.components.showToast
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.model.EntityDescriptor
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.registry.TypedEntityDescriptor
import com.rate.sdk.ui.operator.viewmodel.ConfigEntityViewModel

/**
 * The generic, metadata-driven ADMIN-CRUD config editor. ONE screen that lists / searches /
 * creates / edits / soft-deletes / restores / publishes ANY admin entity, driven entirely by the
 * entity's [TypedEntityDescriptor]. The operator console mounts this once per entity in the
 * registry, so all ~25 admin surfaces share identical wiring.
 *
 * - A filter bar (search + include-deleted + New button) sits above an [AegisTable] of rows whose
 *   columns come from [EntityDescriptor.listColumns].
 * - Clicking a row (or "New") opens an [AegisDrawer] with a metadata-rendered form
 *   ([ConfigEntityForm]); save runs optimistic update / create and refreshes the list.
 * - A status pill, soft-delete/restore and publish-draft affordances are rendered per row.
 */
@Composable
fun ConfigEntityScreen(
    descriptor: TypedEntityDescriptor<*>,
    api: ConfigAdminApi,
    actor: String?,
    modifier: Modifier = Modifier,
    /** When true the create drawer opens automatically on first composition (hub "Quick add"). */
    openInCreate: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    // Recreate the VM when the entity type changes (nav switch).
    val vm = remember(descriptor.id) { ConfigEntityViewModel(descriptor, api, scope, actor) }
    LaunchedEffect(descriptor.id) {
        vm.refresh()
        if (openInCreate) vm.startCreate()
    }

    val spec: EntityDescriptor = vm.spec

    // Multi-row selection (kept by entity id so it survives list re-fetches).
    var selectedIds by remember(descriptor.id) { mutableStateOf<Set<Any>>(emptySet()) }
    // Selection is reconciled against the loaded page on every recomposition so
    // ids that drop off the list (deleted/filtered) don't linger in the bulk bar.
    val selectedRows = remember(vm.items, selectedIds) {
        vm.items.filter { spec.idOf(it) in selectedIds }
    }
    // Drop stale ids whenever the loaded page no longer contains them.
    LaunchedEffect(vm.items) {
        val live = vm.items.mapTo(mutableSetOf()) { spec.idOf(it) }
        val pruned = selectedIds.filterTo(mutableSetOf()) { it in live }
        if (pruned.size != selectedIds.size) selectedIds = pruned
    }

    // Confirm-dialog state: a single row pending delete, or the bulk batch.
    var pendingDelete by remember(descriptor.id) { mutableStateOf<Any?>(null) }
    var pendingBulkDelete by remember(descriptor.id) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(spec.plural, style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(spec.description, style = AegisTypography.body.copy(color = AegisColors.textSecondary))

        vm.error?.let {
            AegisCallout(
                kind = CalloutKind.WARN,
                title = "Could not reach the admin API",
                body = "$it — showing whatever loaded. The admin CRUD routes are served by the " +
                    "server over /api/admin/${spec.id}/…",
            )
        }

        // ── Filter bar ─────────────────────────────────────────────────────
        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = vm.search,
                    onValueChange = { vm.setSearch(it) },
                    label = "Search ${spec.plural.lowercase()}",
                    helper = "Live search — matches across all fields as you type.",
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AegisButton(
                        label = "Search",
                        onClick = { vm.applySearch() },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                    )
                    AegisChip(
                        label = "Include deleted",
                        selected = vm.includeDeleted,
                        onClick = { vm.setIncludeDeleted(!vm.includeDeleted) },
                    )
                    Box(Modifier.weight(1f))
                    AegisButton(
                        label = "New ${spec.singular}",
                        onClick = { vm.startCreate() },
                        size = AegisButtonSize.Sm,
                    )
                }
                Text(
                    resultCountLine(
                        page = vm.page,
                        pageSize = vm.pageSize,
                        loaded = vm.items.size,
                        total = vm.total,
                        singular = spec.singular,
                        plural = spec.plural,
                    ),
                    style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                )
            }
        }

        // ── Bulk action bar (only when rows are selected) ──────────────────
        if (selectedRows.isNotEmpty()) {
            val anyDeleted = selectedRows.any { spec.isDeleted(it) }
            val anyLive = selectedRows.any { !spec.isDeleted(it) }
            AegisBulkBar(
                count = selectedRows.size,
                onClear = { selectedIds = emptySet() },
                label = "${grouped(selectedRows.size.toLong())} ${noun(selectedRows.size, spec.singular, spec.plural)} selected",
            ) {
                if (anyDeleted) {
                    AegisButton(
                        label = "Restore",
                        onClick = {
                            val batch = selectedRows.filter { spec.isDeleted(it) }
                            vm.bulkRestore(batch) { ok, n ->
                                selectedIds = emptySet()
                                showToast(
                                    if (ok == n) "$ok restored" else "$ok of $n restored",
                                    if (ok == n) CalloutKind.SUCCESS else CalloutKind.WARN,
                                )
                            }
                        },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                    )
                }
                if (anyLive) {
                    AegisButton(
                        label = "Delete",
                        onClick = { pendingBulkDelete = true },
                        variant = AegisButtonVariant.Danger,
                        size = AegisButtonSize.Sm,
                    )
                }
            }
        }

        // ── List table ──────────────────────────────────────────────────────
        AegisCard {
            val cols = buildList {
                spec.listColumns.forEach { (header, extract) ->
                    add(
                        AegisColumn<Any>(header = header, weight = 1.2f) {
                            Text(extract(it), style = AegisTypography.body.copy(color = AegisColors.textBody))
                        },
                    )
                }
                add(
                    AegisColumn<Any>(header = "Status", weight = 0.8f) {
                        AegisStatusPill(statusToPill(spec.statusOf(it), spec.isDeleted(it)))
                    },
                )
                add(
                    AegisColumn<Any>(header = "Actions", weight = 1.4f, align = TextAlign.End) { row ->
                        RowActions(
                            descriptor = spec,
                            entity = row,
                            onEdit = { vm.startEdit(row) },
                            onDelete = { pendingDelete = row },
                            onRestore = {
                                vm.restore(row) { ok ->
                                    showToast(
                                        if (ok) "${spec.singular} restored" else "Restore failed",
                                        if (ok) CalloutKind.SUCCESS else CalloutKind.DANGER,
                                    )
                                }
                            },
                            onPublish = {
                                vm.publishDraft(row) { ok ->
                                    showToast(
                                        if (ok) "Draft published" else "Publish failed",
                                        if (ok) CalloutKind.SUCCESS else CalloutKind.DANGER,
                                    )
                                }
                            },
                        )
                    },
                )
            }
            AegisTable(
                items = vm.items,
                columns = cols,
                onRowClick = { vm.startEdit(it) },
                rowKey = { spec.idOf(it) },
                selection = selectedRows.toSet(),
                onSelectionChange = { next ->
                    // The table emits row objects; translate to ids and merge with
                    // any selection that lives on other (currently unloaded) pages.
                    val loadedIds = vm.items.mapTo(mutableSetOf()) { spec.idOf(it) }
                    val nextLoadedIds = next.mapTo(mutableSetOf()) { spec.idOf(it) }
                    selectedIds = (selectedIds - loadedIds) + nextLoadedIds
                },
                emptyState = {
                    AegisEmptyState(
                        title = if (vm.loading) "Loading…" else "No ${spec.plural.lowercase()} yet",
                        helper = if (vm.loading) "Fetching from the admin API."
                        else "Create the first ${spec.singular.lowercase()} or import a seed bundle.",
                        action = {
                            if (!vm.loading) {
                                AegisButton(
                                    label = "New ${spec.singular}",
                                    onClick = { vm.startCreate() },
                                    variant = AegisButtonVariant.Secondary,
                                    size = AegisButtonSize.Sm,
                                )
                            }
                        },
                    )
                },
            )
            if (vm.total > vm.pageSize) {
                Row(
                    Modifier.fillMaxWidth().padding(top = AegisSpacing.s3),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AegisButton(
                        label = "Previous",
                        onClick = { vm.prevPage() },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                        enabled = vm.page > 0,
                    )
                    AegisButton(
                        label = "Next",
                        onClick = { vm.nextPage() },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                        enabled = (vm.page + 1).toLong() * vm.pageSize < vm.total,
                    )
                }
            }
        }
    }

    // ── Create / edit drawer ──────────────────────────────────────────────
    val editing = vm.editing
    AegisDrawer(
        open = editing != null,
        onClose = { vm.closeEditor() },
        title = if (vm.isNew) "New ${spec.singular}" else "Edit ${spec.singular}",
        subtitle = editing?.let { spec.titleOf(it) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                AegisButton(
                    label = "Cancel",
                    onClick = { vm.closeEditor() },
                    variant = AegisButtonVariant.Secondary,
                    size = AegisButtonSize.Sm,
                )
                AegisButton(
                    label = if (vm.isNew) "Create" else "Save",
                    onClick = {
                        vm.save { ok ->
                            if (ok) showToast("${spec.singular} saved", CalloutKind.SUCCESS)
                        }
                    },
                    loading = vm.saving,
                    size = AegisButtonSize.Sm,
                )
            }
        },
    ) {
        if (editing != null) {
            ConfigEntityForm(
                descriptor = spec,
                buffer = vm.buffer,
                errors = vm.formErrors,
                onFieldChange = { k, v -> vm.setField(k, v) },
            )
        }
    }

    // ── Single-row delete confirmation ─────────────────────────────────────
    val toDelete = pendingDelete
    AegisConfirmDialog(
        open = toDelete != null,
        title = "Delete ${spec.singular.lowercase()}?",
        body = "This soft-deletes ${toDelete?.let { spec.titleOf(it) } ?: "it"}; you can restore it later.",
        confirmLabel = "Delete",
        onConfirm = {
            val row = toDelete
            pendingDelete = null
            if (row != null) {
                vm.softDelete(row) { ok ->
                    showToast(
                        if (ok) "${spec.singular} deleted" else "Delete failed",
                        if (ok) CalloutKind.SUCCESS else CalloutKind.DANGER,
                    )
                }
            }
        },
        onCancel = { pendingDelete = null },
    )

    // ── Bulk delete confirmation ───────────────────────────────────────────
    val bulkTargets = selectedRows.filter { !spec.isDeleted(it) }
    AegisConfirmDialog(
        open = pendingBulkDelete,
        title = "Delete ${grouped(bulkTargets.size.toLong())} ${noun(bulkTargets.size, spec.singular, spec.plural)}?",
        body = "This soft-deletes the selected ${noun(bulkTargets.size, spec.singular, spec.plural).lowercase()}; you can restore them later.",
        confirmLabel = "Delete ${bulkTargets.size}",
        onConfirm = {
            val batch = bulkTargets
            pendingBulkDelete = false
            vm.bulkSoftDelete(batch) { ok, n ->
                selectedIds = emptySet()
                showToast(
                    if (ok == n) "$ok deleted" else "$ok of $n deleted",
                    if (ok == n) CalloutKind.SUCCESS else CalloutKind.WARN,
                )
            }
        },
        onCancel = { pendingBulkDelete = false },
    )
}

private fun statusToPill(status: String, deleted: Boolean): AegisStatus = when {
    deleted -> AegisStatus.Retired
    status == "DRAFT" -> AegisStatus.Draft
    status == "RETIRED" -> AegisStatus.Retired
    else -> AegisStatus.Live
}

/** Thousands-separated (Indian grouping) integer, e.g. 12345 → "12,345". */
private fun grouped(n: Long): String =
    Money.fromRupees(n).formatIndian(showSymbol = false, showDecimals = false)

/** Grammar-correct singular/plural noun for a count. */
private fun noun(count: Int, singular: String, plural: String): String =
    if (count == 1) singular else plural

/**
 * "Showing X–Y of Z plurals" — derived from the VM's page/size/total and the
 * number of rows actually loaded. Falls back to a singular "1 singular" / empty
 * states so the line is always grammatical and thousands-separated.
 */
private fun resultCountLine(
    page: Int,
    pageSize: Int,
    loaded: Int,
    total: Long,
    singular: String,
    plural: String,
): String {
    if (total <= 0L || loaded == 0) return "No ${plural.lowercase()}"
    val start = page.toLong() * pageSize + 1
    val end = start + loaded - 1
    val noun = if (total == 1L) singular.lowercase() else plural.lowercase()
    return "Showing ${grouped(start)}–${grouped(end)} of ${grouped(total)} $noun"
}

@Composable
private fun RowActions(
    descriptor: EntityDescriptor,
    entity: Any,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        AegisButton(label = "Edit", onClick = onEdit, variant = AegisButtonVariant.Ghost, size = AegisButtonSize.Sm)
        if (descriptor.statusOf(entity) == "DRAFT") {
            AegisButton(label = "Publish", onClick = onPublish, variant = AegisButtonVariant.Secondary, size = AegisButtonSize.Sm)
        }
        if (descriptor.isDeleted(entity)) {
            AegisButton(label = "Restore", onClick = onRestore, variant = AegisButtonVariant.Secondary, size = AegisButtonSize.Sm)
        } else {
            AegisButton(label = "Delete", onClick = onDelete, variant = AegisButtonVariant.Danger, size = AegisButtonSize.Sm)
        }
    }
}
