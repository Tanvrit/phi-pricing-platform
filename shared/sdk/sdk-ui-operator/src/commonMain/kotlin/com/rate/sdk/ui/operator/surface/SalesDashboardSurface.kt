package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rate.core.money.Money
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.rfq.RfqStatus
import com.rate.sdk.ui.kit.components.AegisBadge
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisHDivider
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.viewmodel.SalesDashboardSource
import com.rate.sdk.ui.operator.viewmodel.SalesDashboardViewModel
import com.rate.sdk.ui.operator.viewmodel.SalesKpis

/**
 * Sales Dashboard (Dorian slide 15) — a KPI + pipeline overview computed from the live RFQ funnel
 * (resource "rfqs") and the experience-rating ledger (resource "experience-ratings") via the
 * generic [ConfigAdminApi]. A top row of headline KPI tiles, an RFQ-by-status funnel breakdown with
 * colour-coded badges, and a "recently modified RFQs" table.
 *
 * Every monetary figure is rendered exclusively via [Money.formatIndian] (₹-grouped rupees) — never
 * raw paise — which is the explicit fix for the competitor's "87105064608" un-formatted-paise bug.
 */
@Composable
fun SalesDashboardSurface(api: ConfigAdminApi, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val vm = remember { SalesDashboardViewModel(api, scope) }
    LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5),
    ) {
        Text("Sales Dashboard", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "Group RFQ pipeline funnel and experience-rating performance, computed live from the sales ledger.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        when (vm.source) {
            SalesDashboardSource.LOADING -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Pulling the RFQ pipeline and experience-rating ledger from the server.",
            )
            SalesDashboardSource.LIVE -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "Tiles below are aggregated from the saved RFQ and experience-rating records.",
            )
            SalesDashboardSource.ERROR -> AegisCallout(
                kind = CalloutKind.WARN,
                title = "Server unreachable",
                body = vm.errorMessage ?: "Could not load the sales ledger.",
            )
        }

        KpiRow(vm.kpis)
        StatusBreakdownCard(vm.kpis)
        RecentRfqsCard(vm.recentRfqs, vm.source)
    }
}

/** Top row of six headline KPI tiles. */
@Composable
private fun KpiRow(kpis: SalesKpis) {
    Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
        KpiTile(
            label = "Total RFQs",
            value = kpis.totalRfqs.toString(),
            helper = "Requests-for-quote in the pipeline",
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Requested SI",
            value = kpis.totalRequestedSi.formatIndian(showSymbol = true, showDecimals = false),
            helper = "Total sum insured requested across all RFQs",
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Win rate",
            value = kpis.winRatePct?.let { "${it.toInt()}%" } ?: "—",
            helper = "Managed ÷ (managed + lost)",
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Avg loss ratio",
            value = kpis.avgLossRatioPct?.let { "${it.toInt()}%" } ?: "—",
            helper = "Mean claims-to-premium across ratings",
            modifier = Modifier.weight(1f),
        )
        KpiTile(
            label = "Pipeline premium",
            value = kpis.pipelinePremium.formatIndian(showSymbol = true, showDecimals = false),
            helper = "Premium collected across experience ratings",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiTile(label: String, value: String, helper: String, modifier: Modifier = Modifier) {
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
            Text(value, style = AegisTypography.moneyL.copy(color = AegisColors.textBody))
            Text(helper, style = AegisTypography.micro.copy(color = AegisColors.textTertiary))
        }
    }
}

/** RFQ-by-status funnel breakdown: every lifecycle status with a count + colour-coded badge. */
@Composable
private fun StatusBreakdownCard(kpis: SalesKpis) {
    AegisCard(
        title = "RFQs by status",
        subtitle = "Where every request-for-quote sits in the sales funnel right now.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            RfqStatus.entries.forEachIndexed { idx, status ->
                if (idx > 0) AegisHDivider()
                val count = kpis.statusCounts[status] ?: 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = AegisSpacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                    ) {
                        AegisBadge(label = statusLabel(status), tone = statusTone(status))
                        Text(
                            statusHelp(status),
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                        )
                    }
                    Text(
                        count.toString(),
                        style = AegisTypography.money.copy(color = AegisColors.textPrimary),
                    )
                }
            }
        }
    }
}

/** Recently modified RFQs: the 8 most-recently-touched records as a compact table. */
@Composable
private fun RecentRfqsCard(rfqs: List<Rfq>, source: SalesDashboardSource) {
    AegisCard(
        title = "Recently modified RFQs",
        subtitle = "The 8 most recently updated requests-for-quote.",
    ) {
        AegisTable(
            items = rfqs,
            columns = listOf(
                AegisColumn<Rfq>(header = "RFQ #", weight = 1.0f) {
                    Text(it.rfqNumber, style = AegisTypography.mono14.copy(color = AegisColors.textBody))
                },
                AegisColumn(header = "Client", weight = 1.8f) {
                    Text(it.clientName, style = AegisTypography.body.copy(color = AegisColors.textBody))
                },
                AegisColumn(header = "Status", weight = 1.1f) {
                    AegisBadge(label = statusLabel(it.rfqStatus), tone = statusTone(it.rfqStatus))
                },
                AegisColumn(header = "Sum insured", weight = 1.1f, align = TextAlign.End, mono = true) {
                    Text(
                        it.requestedSumInsured.formatIndian(showSymbol = true, showDecimals = false),
                        style = AegisTypography.money.copy(color = AegisColors.textBody),
                    )
                },
                AegisColumn(header = "Lives", weight = 0.6f, align = TextAlign.End) {
                    Text(it.lives.toString(), style = AegisTypography.body.copy(color = AegisColors.textBody))
                },
                AegisColumn(header = "Updated", weight = 1.0f) {
                    Text(
                        it.updatedAt.toString().take(10),
                        style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                    )
                },
            ),
            emptyState = {
                Text(
                    if (source == SalesDashboardSource.LOADING) "Loading the RFQ pipeline…" else "No RFQs in the pipeline yet.",
                    style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                )
            },
        )
    }
}

/** Short uppercase label shown inside the status badge. */
private fun statusLabel(status: RfqStatus): String = when (status) {
    RfqStatus.SUBMITTED -> "SUBMITTED"
    RfqStatus.ESTIMATED_PRICE -> "ESTIMATED"
    RfqStatus.CONSOLIDATED_PRICE -> "CONSOLIDATED"
    RfqStatus.MANAGED -> "MANAGED"
    RfqStatus.CLOSED -> "CLOSED"
    RfqStatus.LOST -> "LOST"
}

/** One-line meaning of each funnel stage. */
private fun statusHelp(status: RfqStatus): String = when (status) {
    RfqStatus.SUBMITTED -> "New enquiry received"
    RfqStatus.ESTIMATED_PRICE -> "Indicative price shared"
    RfqStatus.CONSOLIDATED_PRICE -> "Final price firmed up"
    RfqStatus.MANAGED -> "Won and being managed"
    RfqStatus.CLOSED -> "Closed out"
    RfqStatus.LOST -> "Lost to a competitor"
}

/** Colour-codes each stage: won = success, lost = danger, in-flight = info/warn, neutral otherwise. */
private fun statusTone(status: RfqStatus): AegisBadgeTone = when (status) {
    RfqStatus.SUBMITTED -> AegisBadgeTone.Neutral
    RfqStatus.ESTIMATED_PRICE -> AegisBadgeTone.Info
    RfqStatus.CONSOLIDATED_PRICE -> AegisBadgeTone.Brand
    RfqStatus.MANAGED -> AegisBadgeTone.Success
    RfqStatus.CLOSED -> AegisBadgeTone.Warn
    RfqStatus.LOST -> AegisBadgeTone.Danger
}
