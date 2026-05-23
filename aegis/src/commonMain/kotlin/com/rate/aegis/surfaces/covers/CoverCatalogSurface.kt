@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.rate.aegis.surfaces.covers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.DeepLink
import com.rate.aegis.LocalAegisDeepLink
import com.rate.aegis.components.*
import com.rate.aegis.theme.*
import com.rate.domain.data.CoverCatalog
import com.rate.domain.data.CoverMeta
import com.rate.domain.data.ParamDef

/**
 * Cover Catalog — BUSINESS-shell surface (parallel iteration a).
 *
 * Browser for the actuarial cover catalogue exposed by `CoverCatalog`. Mirrors
 * the QuoteExplorerSurface pattern: 28sp title, helper text, search input,
 * filter chips, AegisTable, and a drill-down AegisDrawer.
 *
 * Data source is the static `CoverCatalog.ALL + CoverCatalog.DISCOUNTS` list —
 * roughly fifty entries spanning accumulating covers, flat covers,
 * pre-calculated covers, and pure-discount lines. There is no lifecycle yet,
 * so every row paints `AegisStatus.Live`.
 *
 * Grouping strategy: chips toggle between "All", "Covers" (isDiscount = false),
 * and "Discounts" (isDiscount = true). This piggy-backs on the `isDiscount`
 * flag the calculator already uses and avoids inventing a new taxonomy.
 */
@Composable
fun CoverCatalogSurface() {
    val all: List<CoverMeta> = remember { CoverCatalog.ALL + CoverCatalog.DISCOUNTS }

    var search by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf(CoverGroupFilter.All) }
    var selected by remember { mutableStateOf<CoverMeta?>(null) }

    // Deep-link from command palette: open the matching cover's drawer.
    // `all` is built from static :shared catalogues so it's always populated;
    // we still key on it for symmetry with the async surfaces.
    val deepLink = LocalAegisDeepLink.current
    LaunchedEffect(deepLink.value.coverId, all) {
        val target = deepLink.value.coverId
        if (target != null) {
            val match = all.firstOrNull { it.id == target }
            if (match != null) {
                selected = match
                deepLink.value = DeepLink.NONE
            }
        }
    }

    val filtered = remember(search, groupFilter, all) {
        val q = search.trim()
        all.filter { c ->
            val matchesSearch = q.isBlank() ||
                    c.id.contains(q, ignoreCase = true) ||
                    c.name.contains(q, ignoreCase = true)
            val matchesGroup = when (groupFilter) {
                CoverGroupFilter.All -> true
                CoverGroupFilter.Covers -> !c.isDiscount
                CoverGroupFilter.Discounts -> c.isDiscount
            }
            matchesSearch && matchesGroup
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Cover Catalog",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "All ${all.size} covers the actuarial engine knows about. " +
                    "Click a row to inspect. Showing ${filtered.size}.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "Search by cover ID or display name",
                    helper = "Try \"maternity\", \"co_pay\", \"disc_\", \"loyalty\"",
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                ) {
                    Text(
                        "Group:",
                        fontSize = 13.sp,
                        color = AegisColors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    CoverGroupFilter.entries.forEach { g ->
                        AegisChip(
                            label = "${g.label} (${g.count(all)})",
                            selected = groupFilter == g,
                            onClick = { groupFilter = g },
                        )
                    }
                }
            }
        }

        AegisCard {
            AegisTable(
                items = filtered,
                onRowClick = { selected = it },
                rowKey = { it.id },
                columns = listOf(
                    AegisColumn<CoverMeta>(
                        header = "ID", weight = 1.4f, mono = true,
                        cell = { Text(it.id, fontSize = 13.sp, color = AegisColors.textBody) },
                    ),
                    AegisColumn(
                        header = "Display name", weight = 2.4f,
                        cell = { Text(it.name, fontSize = 13.sp) },
                    ),
                    AegisColumn(
                        header = "Kind", weight = 0.9f,
                        cell = {
                            val tone = if (it.isDiscount) AegisChipTone.Info else AegisChipTone.Neutral
                            AegisChip(
                                label = if (it.isDiscount) "Discount" else "Cover",
                                tone = tone,
                                selected = false,
                            )
                        },
                    ),
                    AegisColumn(
                        header = "Params", weight = 1.6f,
                        cell = {
                            val summary = paramSummary(it)
                            Text(
                                summary,
                                fontSize = 13.sp,
                                color = if (summary == "—") AegisColors.textSecondary
                                else AegisColors.textBody,
                            )
                        },
                    ),
                    AegisColumn(
                        header = "Status", weight = 0.8f,
                        cell = { AegisStatusPill(AegisStatus.Live) },
                    ),
                ),
                emptyState = {
                    AegisEmptyState(
                        title = "No covers match your filters",
                        helper = "Clear the search box or pick a different group chip.",
                    )
                },
            )
        }
    }

    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.name ?: "Cover details",
        subtitle = selected?.id,
    ) {
        selected?.let { c ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AegisStatusPill(AegisStatus.Live)
                    AegisChip(
                        label = if (c.isDiscount) "Discount" else "Cover",
                        tone = if (c.isDiscount) AegisChipTone.Info else AegisChipTone.Neutral,
                        selected = false,
                    )
                }
                AegisHDivider()
                LedgerRow("ID", c.id, mono = true)
                LedgerRow("Display name", c.name)
                LedgerRow("Kind", if (c.isDiscount) "Discount" else "Cover")
                LedgerRow(
                    "Excel aliases",
                    if (c.xlsNames.isEmpty()) "—" else c.xlsNames.size.toString(),
                )
                AegisHDivider()

                Text(
                    "Description",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary,
                )
                Text(
                    c.description.ifBlank { "No description provided." },
                    fontSize = 14.sp,
                    color = AegisColors.textBody,
                )

                AegisHDivider()
                Text(
                    "Parameters",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textSecondary,
                )
                val params = listOfNotNull(c.param1, c.param2)
                if (params.isEmpty()) {
                    AegisCallout(
                        kind = CalloutKind.INFO,
                        title = "No parameters",
                        body = "This cover applies as-is — the engine reads its rate directly " +
                                "from the rate tables without any configurable knobs.",
                    )
                } else {
                    params.forEach { p -> ParamCard(p) }
                }

                if (c.xlsNames.isNotEmpty()) {
                    AegisHDivider()
                    Text(
                        "Excel display names",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                    )
                    Text(
                        "Aliases recognised by the Sheet1 importer when matching covers " +
                                "from Rate_Calculator_v7.0.xlsm.",
                        fontSize = 12.sp,
                        color = AegisColors.textSecondary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s1)) {
                        c.xlsNames.forEach { alias ->
                            Text(
                                "• $alias",
                                fontSize = 13.sp,
                                color = AegisColors.textBody,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CoverGroupFilter(val label: String) {
    All("All") {
        override fun count(all: List<CoverMeta>): Int = all.size
    },
    Covers("Covers") {
        override fun count(all: List<CoverMeta>): Int = all.count { !it.isDiscount }
    },
    Discounts("Discounts") {
        override fun count(all: List<CoverMeta>): Int = all.count { it.isDiscount }
    };

    abstract fun count(all: List<CoverMeta>): Int
}

private fun paramSummary(c: CoverMeta): String {
    val p1 = c.param1
    val p2 = c.param2
    return when {
        p1 == null && p2 == null -> "—"
        p1 != null && p2 != null -> "${p1.name} +1"
        p1 != null -> "${p1.name} (${p1.options.size})"
        p2 != null -> "${p2.name} (${p2.options.size})"
        else -> "—"
    }
}

@Composable
private fun ParamCard(p: ParamDef) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AegisColors.slate2, AegisRadii.shapeMd)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                p.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textBody,
            )
            Text(
                "${p.options.size} option${if (p.options.size == 1) "" else "s"}",
                fontSize = 12.sp,
                color = AegisColors.textSecondary,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
        ) {
            p.options.forEach { opt ->
                AegisChip(label = opt, selected = false)
            }
        }
    }
}

@Composable
private fun LedgerRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
        )
    }
}
