package com.rate.sdk.ui.operator.surface

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rate.core.base.model.PageRequest
import com.rate.core.base.model.SortDir
import com.rate.core.base.model.SortSpec
import com.rate.core.money.formatRupees
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.quoting.network.QuoteSummaryDto
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisDrawer
import com.rate.sdk.ui.kit.components.AegisEmptyState
import com.rate.sdk.ui.kit.components.AegisHDivider
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.AegisStatus
import com.rate.sdk.ui.kit.components.AegisStatusPill
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/** In-memory validity filter for the explorer. */
private enum class ValidityFilter(val label: String) { All("All"), Valid("Valid"), Invalid("Invalid") }

/**
 * Quote Explorer — relocated from aegis. Search + validity filter + drill-down drawer over the
 * real saved-quote ledger ([QuoteApi]). Reads a deep page on open and filters client-side; the
 * drawer rehydrates the full priced result on selection.
 */
@Composable
fun QuoteExplorerSurface(quotes: QuoteApi, modifier: Modifier = Modifier) {
    var all by remember { mutableStateOf<List<QuoteSummaryDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf(ValidityFilter.All) }
    var selected by remember { mutableStateOf<QuoteSummaryDto?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            quotes.list(PageRequest(page = 0, size = 300, sort = listOf(SortSpec("createdAt", SortDir.DESC))))
        }.onSuccess { all = it.items; loadError = null }
            .onFailure { loadError = it.message ?: "Server unreachable" }
        loading = false
    }

    val filtered = remember(search, validity, all) {
        all.filter { q ->
            val matchesSearch = search.isBlank() ||
                q.id.contains(search, true) ||
                q.request.planId.contains(search, true) ||
                q.request.primaryAge.toString() == search.trim()
            val matchesValidity = when (validity) {
                ValidityFilter.All -> true
                ValidityFilter.Valid -> q.isValid
                ValidityFilter.Invalid -> !q.isValid
            }
            matchesSearch && matchesValidity
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text("Quote Explorer", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "${filtered.size} of ${all.size} quotes" + (loadError?.let { " — $it" } ?: ""),
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )
        loadError?.let {
            AegisCallout(kind = CalloutKind.WARN, title = "Could not load quotes", body = it)
        }

        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "Search by quote ID, plan, or age",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text(
                        "Status:",
                        style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    ValidityFilter.entries.forEach { v ->
                        AegisChip(label = v.label, selected = validity == v, onClick = { validity = v })
                    }
                }
            }
        }

        AegisCard {
            AegisTable(
                items = filtered,
                onRowClick = { selected = it },
                columns = listOf(
                    AegisColumn<QuoteSummaryDto>(header = "Quote ID", weight = 1.7f) {
                        Text(it.id, style = AegisTypography.mono14)
                    },
                    AegisColumn(header = "Created", weight = 1.0f) {
                        Text(it.createdAt.toString().take(19).replace('T', ' '),
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "Plan", weight = 1.4f) {
                        Text(it.request.planId, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Age", weight = 0.4f, align = TextAlign.End) {
                        Text(it.request.primaryAge.toString(), style = AegisTypography.body)
                    },
                    AegisColumn(header = "Family", weight = 0.6f) {
                        Text(it.request.familyType, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Zone", weight = 0.7f) {
                        Text(it.request.zone, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "Tenure", weight = 0.7f) {
                        Text(it.request.tenure.label, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Status", weight = 0.7f) {
                        AegisStatusPill(if (it.isValid) AegisStatus.Approved else AegisStatus.Rejected)
                    },
                    AegisColumn(header = "Total (incl. GST)", weight = 1.2f, align = TextAlign.End, mono = true) {
                        Text(
                            if (it.isValid) formatRupees(it.totalIncludingGst) else "—",
                            style = AegisTypography.money,
                        )
                    },
                ),
                emptyState = {
                    AegisEmptyState(
                        title = if (loading) "Loading…" else "No quotes match your filters",
                        helper = if (loading) "Fetching the ledger." else "Adjust the chips above or clear the search box.",
                    )
                },
            )
        }
    }

    QuoteDetailDrawer(selected) { selected = null }
}

@Composable
private fun QuoteDetailDrawer(summary: QuoteSummaryDto?, onClose: () -> Unit) {
    AegisDrawer(
        open = summary != null,
        onClose = onClose,
        title = summary?.id ?: "Quote details",
        subtitle = summary?.request?.planId,
    ) {
        summary?.let { q ->
            Column(
                Modifier.fillMaxWidth().padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                LedgerRow("Quote ID", q.id)
                LedgerRow("Created", q.createdAt.toString().take(19).replace('T', ' '))
                LedgerRow("Plan ID", q.request.planId)
                LedgerRow("Primary age", q.request.primaryAge.toString())
                LedgerRow("Family type", q.request.familyType)
                LedgerRow("Zone", q.request.zone)
                LedgerRow("Tenure", q.request.tenure.label)
                LedgerRow("Sum insured", formatRupees(q.request.sumInsured.toDouble()))
                AegisHDivider()
                if (q.isValid) {
                    val preTax = q.totalIncludingGst / 1.18
                    LedgerRow("Sub-total (pre-tax)", formatRupees(preTax, 2))
                    LedgerRow("GST (18%)", formatRupees(q.totalIncludingGst - preTax, 2))
                    LedgerRow("Total payable", formatRupees(q.totalIncludingGst), bold = true)
                } else {
                    AegisCallout(
                        kind = CalloutKind.WARN,
                        title = "Quote is invalid",
                        body = "The engine returned isValid=false for this request.",
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
        Text(
            value,
            style = if (bold) AegisTypography.body.copy(color = AegisColors.textPrimary, fontWeight = AegisTypography.h3.fontWeight)
            else AegisTypography.body.copy(color = AegisColors.textBody),
        )
    }
}
