package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rate.core.money.formatRupees
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisStatus
import com.rate.sdk.ui.kit.components.AegisStatusPill
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.quoting.network.QuoteSummaryDto
import com.rate.sdk.ui.operator.viewmodel.DashboardSource
import com.rate.sdk.ui.operator.viewmodel.DashboardViewModel

/**
 * Operator home dashboard — relocated from aegis HomeSurface. Five KPI tiles + a recent-quotes
 * table, computed from the real saved-quote ledger via [QuoteApi] (the synthetic FakeAegisRepo is
 * retired). Graceful ERROR banner when the server is unreachable.
 */
@Composable
fun DashboardSurface(quotes: QuoteApi, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val vm = remember { DashboardViewModel(quotes, scope) }
    LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5),
    ) {
        Text("Home", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))

        when (vm.source) {
            DashboardSource.LOADING -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Pulling the latest quote stream from the server.",
            )
            DashboardSource.LIVE -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "Tiles below are computed from the server's saved-quote ledger.",
            )
            DashboardSource.ERROR -> AegisCallout(
                kind = CalloutKind.WARN,
                title = "Server unreachable",
                body = vm.errorMessage ?: "Could not load the quote ledger.",
            )
        }

        val kpis = vm.kpis
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            KpiTile("Quotes today", kpis.quotesToday.toString(), Modifier.weight(1f))
            KpiTile("GWP (recent)", formatRupees(kpis.gwpRecent), Modifier.weight(1f))
            KpiTile("Conversion", "${kpis.conversionRatePct.toInt()}%", Modifier.weight(1f))
            KpiTile("Valid quotes", kpis.validCount.toString(), Modifier.weight(1f))
            KpiTile("Invalid", kpis.invalidCount.toString(), Modifier.weight(1f))
        }

        AegisCard(title = "Recent quotes") {
            AegisTable(
                items = vm.recent,
                columns = listOf(
                    AegisColumn<QuoteSummaryDto>(header = "Quote ID", weight = 1.6f) {
                        Text(it.id, style = AegisTypography.mono14.copy(color = AegisColors.textBody))
                    },
                    AegisColumn(header = "Created", weight = 1.0f) {
                        Text(it.createdAt.toString().take(19).replace('T', ' '),
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                    },
                    AegisColumn(header = "Plan", weight = 1.4f) {
                        Text(it.request.planId, style = AegisTypography.body.copy(color = AegisColors.textBody))
                    },
                    AegisColumn(header = "Age", weight = 0.5f, align = TextAlign.End) {
                        Text(it.request.primaryAge.toString(), style = AegisTypography.body)
                    },
                    AegisColumn(header = "Family", weight = 0.7f) {
                        Text(it.request.familyType, style = AegisTypography.body)
                    },
                    AegisColumn(header = "Status", weight = 0.7f) {
                        AegisStatusPill(if (it.isValid) AegisStatus.Approved else AegisStatus.Rejected)
                    },
                    AegisColumn(header = "Total (incl. GST)", weight = 1.2f, align = TextAlign.End, mono = true) {
                        Text(
                            if (it.isValid) formatRupees(it.totalIncludingGst) else "—",
                            style = AegisTypography.money.copy(color = AegisColors.textBody),
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, modifier: Modifier = Modifier) {
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
            Text(value, style = AegisTypography.moneyL.copy(color = AegisColors.textBody))
        }
    }
}
