package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/** Row density modes. Pass 4 §4.11. */
enum class TableDensity(val rowHeight: Dp, val vPad: Dp) {
    Comfortable(rowHeight = 52.dp, vPad = 14.dp),
    Compact(rowHeight = 36.dp, vPad = 8.dp),
    Mini(rowHeight = 28.dp, vPad = 4.dp),
}

/**
 * AegisColumn — declarative column descriptor.
 *
 *   AegisColumn("Quote ID", weight = 1.2f) { Text(it.id) }
 *   AegisColumn("Total", weight = 1f, align = TextAlign.End, mono = true) {
 *       Text(it.total.formatIndian(), style = AegisTypography.money)
 *   }
 *
 * - `weight` distributes horizontal space proportional to other columns.
 * - `align` controls header + cell alignment; numeric columns should use End.
 * - `mono` switches the header style to monospace (signals number-shaped data).
 * - `pinned` reserves columns at the start that don't scroll horizontally — a
 *   later ship will wire actual horizontal-scroll; the flag is preserved as part
 *   of the API contract.
 * - `sortable` is preserved as part of the API contract — sort indicators paint
 *   when true, but the actual sort logic ships later per spec.
 */
data class AegisColumn<T>(
    val header: String,
    val weight: Float = 1f,
    val align: TextAlign = TextAlign.Start,
    val mono: Boolean = false,
    val pinned: Boolean = false,
    val sortable: Boolean = false,
    val cell: @Composable (T) -> Unit,
)

/**
 * AegisTable — generic, polished tabular surface.
 *
 *   AegisTable(
 *       items = quotes,
 *       columns = listOf(
 *           AegisColumn("Quote ID") { Text(it.id) },
 *           AegisColumn("Status") { AegisStatusPill(it.status) },
 *           AegisColumn("Total", align = TextAlign.End, mono = true) {
 *               Text(it.total.formatIndian(), style = AegisTypography.money)
 *           },
 *       ),
 *       selection = selected,
 *       onSelectionChange = { selected = it },
 *       density = TableDensity.Compact,
 *   )
 */
@Composable
fun <T> AegisTable(
    items: List<T>,
    columns: List<AegisColumn<T>>,
    modifier: Modifier = Modifier,
    onRowClick: ((T) -> Unit)? = null,
    density: TableDensity = TableDensity.Comfortable,
    selection: Set<T>? = null,
    onSelectionChange: ((Set<T>) -> Unit)? = null,
    emptyState: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    rowKey: ((T) -> Any)? = null,
) {
    val tableShape = AegisRadii.shapeLg
    val showCheckboxes = selection != null && onSelectionChange != null
    val sel = selection ?: emptySet()
    val allSelected = items.isNotEmpty() && items.all { it in sel }

    Column(
        modifier
            .fillMaxWidth()
            .background(AegisColors.surface, tableShape)
            .border(1.dp, AegisColors.border, tableShape),
    ) {
        // Header row
        Row(
            Modifier
                .fillMaxWidth()
                .background(AegisColors.slate2)
                .padding(horizontal = AegisSpacing.s4, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCheckboxes) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { checked ->
                        onSelectionChange?.invoke(if (checked) items.toSet() else emptySet())
                    },
                    colors = CheckboxDefaults.colors(checkedColor = AegisColors.indigo500),
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = if (allSelected) "Deselect all rows" else "Select all rows" },
                )
            }
            columns.forEach { col ->
                val style = (if (col.mono) AegisTypography.mono14 else AegisTypography.small)
                    .copy(
                        color = AegisColors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                Text(
                    text = col.header.uppercase(),
                    style = style,
                    textAlign = col.align,
                    modifier = Modifier.weight(col.weight).padding(horizontal = 6.dp),
                )
            }
        }
        AegisHDivider()

        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = AegisSpacing.s6),
                contentAlignment = Alignment.Center,
            ) {
                if (emptyState != null) emptyState()
                else {
                    AegisEmptyState(
                        title = "No rows to display",
                        helper = "Adjust the filters above or import some data to get started.",
                    )
                }
            }
        } else if (maxHeight != null) {
            // A finite height was requested → virtualize with a LazyColumn capped
            // at [maxHeight] so the table scrolls internally. This is only valid
            // because the height is bounded; use it when the table owns a fixed
            // slice of the screen rather than flowing inside a scrolling page.
            LazyColumn(modifier = Modifier.heightIn(max = maxHeight)) {
                items(
                    count = items.size,
                    key = if (rowKey != null) { idx -> rowKey(items[idx]) } else null,
                ) { idx ->
                    TableBodyRow(
                        item = items[idx],
                        idx = idx,
                        lastIndex = items.lastIndex,
                        columns = columns,
                        density = density,
                        sel = sel,
                        showCheckbox = showCheckboxes,
                        onRowClick = onRowClick,
                        onSelectionChange = onSelectionChange,
                    )
                }
            }
        } else {
            // No height bound → the table is embedded in a scrolling page (the
            // common case: operator surfaces wrap their content in a
            // verticalScroll). A LazyColumn here is handed an infinite max-height
            // by the parent scroll and throws "Vertically scrollable component was
            // measured with an infinity maximum height". A plain Column lays every
            // row out eagerly, which is the correct behaviour here — the page
            // scroll owns overflow and these operator tables are bounded in size.
            // Lazy virtualization is only needed (and only legal) when a finite
            // maxHeight is supplied, handled in the branch above.
            Column(Modifier.fillMaxWidth()) {
                items.forEachIndexed { idx, item ->
                    TableBodyRow(
                        item = item,
                        idx = idx,
                        lastIndex = items.lastIndex,
                        columns = columns,
                        density = density,
                        sel = sel,
                        showCheckbox = showCheckboxes,
                        onRowClick = onRowClick,
                        onSelectionChange = onSelectionChange,
                    )
                }
            }
        }
    }
}

/**
 * One table body row + trailing divider. Shared by both the LazyColumn (bounded
 * [maxHeight]) and plain-Column (flows in a scrolling page) render paths so row
 * markup stays identical regardless of which container is used.
 */
@Composable
private fun <T> TableBodyRow(
    item: T,
    idx: Int,
    lastIndex: Int,
    columns: List<AegisColumn<T>>,
    density: TableDensity,
    sel: Set<T>,
    showCheckbox: Boolean,
    onRowClick: ((T) -> Unit)?,
    onSelectionChange: ((Set<T>) -> Unit)?,
) {
    AegisTableRow(
        item = item,
        columns = columns,
        density = density,
        isSelected = item in sel,
        showCheckbox = showCheckbox,
        zebra = idx % 2 == 1,
        onRowClick = onRowClick,
        onSelectChange = { checked ->
            if (onSelectionChange != null) {
                val next = if (checked) sel + item else sel - item
                onSelectionChange(next)
            }
        },
    )
    if (idx < lastIndex) AegisHDivider()
}

@Composable
private fun <T> AegisTableRow(
    item: T,
    columns: List<AegisColumn<T>>,
    density: TableDensity,
    isSelected: Boolean,
    showCheckbox: Boolean,
    zebra: Boolean,
    onRowClick: ((T) -> Unit)?,
    onSelectChange: (Boolean) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val bg = when {
        isSelected -> AegisColors.indigo50
        hovered -> AegisColors.slate2
        zebra -> AegisColors.slate1
        else -> AegisColors.surface
    }
    val rowMod = Modifier
        .fillMaxWidth()
        .heightIn(min = density.rowHeight)
        .background(bg)
        .hoverable(hoverSource)
        .let { mod -> if (onRowClick != null) mod.clickable { onRowClick(item) } else mod }
        .padding(horizontal = AegisSpacing.s4, vertical = density.vPad)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectChange,
                colors = CheckboxDefaults.colors(checkedColor = AegisColors.indigo500),
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = if (isSelected) "Selected" else "Not selected" },
            )
        }
        columns.forEach { col ->
            Box(
                Modifier
                    .weight(col.weight)
                    .padding(horizontal = 6.dp),
                contentAlignment = when (col.align) {
                    TextAlign.End -> Alignment.CenterEnd
                    TextAlign.Center -> Alignment.Center
                    else -> Alignment.CenterStart
                },
            ) { col.cell(item) }
        }
    }
}
