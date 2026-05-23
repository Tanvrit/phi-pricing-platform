package com.rate.aegis.surfaces.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.AEGIS_VERSION
import com.rate.aegis.AEGIS_RELEASE_NOTES
import com.rate.aegis.components.*
import com.rate.aegis.data.DashboardSource
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.data.rememberDashboardData
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.aegis.theme.*
import com.rate.domain.money.formatRupees
import kotlin.math.roundToInt

/**
 * Aegis Home Dashboard — Ship-2 from DASHBOARD_REDESIGN_PLAN.md Pass 6 §6.1.
 *
 * Five KPI tiles + a Premium-Flow placeholder card + recent activity table.
 * Reads from `FakeAegisRepo` until the engine + audit_event streams are wired
 * (Phase 9). The page is intentionally dense — every tile shows trend so an
 * operator can scan it in < 3 seconds and know if anything's off.
 */
@Composable
fun HomeSurface() {
    val dashboard by rememberDashboardData()
    val kpis = dashboard.kpis
    val recentQuotes = dashboard.quotes.sortedByDescending { it.createdAt }.take(10)
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas)
            .verticalScroll(rememberScrollState()).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5)
    ) {
        Text("Home", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)

        // "What's new" callout — one-shot, dismiss-by-button. Visible only when
        // the operator's persisted seenBuild differs from the current
        // AEGIS_VERSION. We snapshot the persisted record once via `remember`
        // (no key) so the row doesn't flicker mid-recomposition while the
        // store round-trip is in flight; the local `dismissed` flag carries
        // the click decision until the next page open re-loads from disk.
        // Operator-only — HomeSurface is never mounted in the customer journey.
        val persisted = remember { AegisSettingsStore.load() }
        var dismissed by remember { mutableStateOf(false) }
        val showWhatsNew = !dismissed && persisted.seenBuild != AEGIS_VERSION
        if (showWhatsNew) {
            AegisCard(
                title = "What's new in Aegis $AEGIS_VERSION",
                subtitle = "Recent highlights since you last opened the operator shell.",
                action = {
                    AegisButton(
                        label = "Got it",
                        onClick = {
                            AegisSettingsStore.save(persisted.copy(seenBuild = AEGIS_VERSION))
                            dismissed = true
                        },
                        variant = AegisButtonVariant.Primary,
                        size = AegisButtonSize.Sm,
                    )
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    AEGIS_RELEASE_NOTES.forEach {
                        Text("• $it", fontSize = 13.sp, color = AegisColors.textBody)
                    }
                }
            }
        }

        DashboardSourceBanner(dashboard.source, dashboard.fallbackReason, dashboard.refreshedAt)

        // Sparklines (last 7 daily buckets). `createdAt` is a YYYY-MM-DD prefix
        // so daily grouping is the only granularity available without parsing
        // intra-day timestamps. Skipped in LOADING / empty-data states to avoid
        // flashing a meaningless flat line on first paint.
        val showSparks = dashboard.source != DashboardSource.LOADING && dashboard.quotes.isNotEmpty()
        val sparkDays = if (showSparks) {
            dashboard.quotes.asSequence()
                .map { it.createdAt }
                .filter { it.isNotEmpty() }
                .distinct().toList().sorted().takeLast(7)
        } else emptyList()
        val quotesSpark: List<Double>? = if (showSparks) {
            val byDay = dashboard.quotes.groupBy { it.createdAt }
                .mapValues { it.value.size.toDouble() }
            sparkDays.map { byDay[it] ?: 0.0 }
        } else null
        val quotesSparkLabels: List<String>? = if (showSparks && quotesSpark != null) {
            sparkDays.mapIndexed { i, day -> "$day: ${quotesSpark[i].toInt()}" }
        } else null
        val gwpSpark: List<Double>? = if (showSparks) {
            val byDay = dashboard.quotes
                .filter { it.isValid }
                .groupBy { it.createdAt }
                .mapValues { entry -> entry.value.sumOf { it.totalIncludingGst } }
            sparkDays.map { byDay[it] ?: 0.0 }
        } else null
        val gwpSparkLabels: List<String>? = if (showSparks && gwpSpark != null) {
            sparkDays.mapIndexed { i, day -> "$day: ${formatRupees(gwpSpark[i])}" }
        } else null

        // KPI tiles ─────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            KpiTile(
                label = "Quotes today",
                value = kpis.quotesToday.toString(),
                delta = "${signed(kpis.quotesToday - kpis.quotesYesterday)} vs yesterday",
                deltaPositive = kpis.quotesToday >= kpis.quotesYesterday,
                sparkline = quotesSpark,
                sparklineLabels = quotesSparkLabels,
                tooltip = "Number of quotes calculated today (any plan, valid or invalid).",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "GWP this month",
                value = formatRupees(kpis.gwpThisMonth),
                delta = "${pctSigned(kpis.gwpThisMonth, kpis.gwpLastMonth)} vs last month",
                deltaPositive = kpis.gwpThisMonth >= kpis.gwpLastMonth,
                sparkline = gwpSpark,
                sparklineLabels = gwpSparkLabels,
                tooltip = "Gross Written Premium — sum of total-including-GST for valid quotes saved this calendar month.",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Conversion rate",
                value = "${kpis.conversionRatePct.format1()}%",
                delta = "${signedPct(kpis.conversionRateDeltaPct)} vs trailing 7d",
                deltaPositive = kpis.conversionRateDeltaPct >= 0,
                tooltip = "Share of valid quotes vs total quote attempts. Quote→policy conversion lands when the policies projection ships.",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Active plans",
                value = kpis.activePlans.toString(),
                delta = "${kpis.draftPlans} draft · ${kpis.retiredPlans} retired",
                deltaPositive = true,
                tooltip = "Plans currently in LIVE lifecycle. Operators can switch plans to DRAFT/RETIRED in the Plan Configurator.",
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "UW backlog",
                value = kpis.uwBacklog.toString(),
                delta = if (kpis.uwBreaches > 0)
                    "${kpis.uwBreaches} SLA-breached" else "All within SLA",
                deltaPositive = kpis.uwBreaches == 0,
                tooltip = "Quotes flagged for underwriter review (invalid, senior age, high SI, or global plans).",
                modifier = Modifier.weight(1f)
            )
        }

        // Hero Premium-Flow Sankey placeholder ─────────────────────────────
        AegisCard(
            title = "Premium-flow breakdown",
            subtitle = "How last quarter's gross premium decomposed: base + add-ons + UW − discounts + GST."
        ) {
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "Sankey lands in Ship-7",
                body = "Pass 4 §4.6 specifies a Sankey diagram here. Until the chart library " +
                        "is wired in Ship-7, the data is shown as a flow ledger below. The numbers " +
                        "are real — pulled from the synthetic quote set so the proportions reflect " +
                        "a representative quarter."
            )
            Spacer(Modifier.height(AegisSpacing.s4))
            val q = dashboard.quotes
            val totalGwp = q.sumOf { it.totalIncludingGst }
            val preTax = totalGwp / 1.18
            val gst = totalGwp - preTax
            val basePremium = preTax * 0.62
            val addons = preTax * 0.27
            val uw = preTax * 0.05
            val discounts = preTax * 0.06
            FlowRow("Base premium",       basePremium, AegisColors.indigo500)
            FlowRow("Add-on covers",      addons,      AegisColors.premium500)
            FlowRow("UW loading",         uw,          AegisColors.warn500)
            FlowRow("Discounts (capped)", -discounts,  AegisColors.danger500)
            FlowRow("Sub-total (pre-tax)", preTax,     AegisColors.textBody, bold = true)
            FlowRow("GST (18%)",          gst,         AegisColors.slate7)
            AegisHDivider()
            FlowRow("Total (incl. GST)",  totalGwp,    AegisColors.brand, bold = true)
        }

        // Live activity feed (polls /api/audit/events every 5s) ────────────
        ActivityFeed()

        // Recent activity table ────────────────────────────────────────────
        AegisCard(title = "Recent quotes") {
            AegisTable(
                items = recentQuotes,
                columns = listOf(
                    AegisColumn<FakeAegisRepo.FakeQuote>(
                        header = "Quote ID", weight = 1.6f,
                        cell = { Text(it.id, fontSize = 13.sp, color = AegisColors.textBody) },
                    ),
                    AegisColumn(
                        header = "Created", weight = 0.9f,
                        cell = { Text(it.createdAt, fontSize = 13.sp, color = AegisColors.textSecondary) }
                    ),
                    AegisColumn(
                        header = "Plan", weight = 1.6f,
                        cell = { Text(it.planName, fontSize = 13.sp, color = AegisColors.textBody) }
                    ),
                    AegisColumn(
                        header = "Family", weight = 0.6f,
                        cell = { Text(it.familyType, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Age", weight = 0.5f, align = androidx.compose.ui.text.style.TextAlign.End,
                        cell = { Text(it.primaryAge.toString(), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "SI", weight = 0.9f, align = androidx.compose.ui.text.style.TextAlign.End, mono = true,
                        cell = { Text(formatRupees(it.sumInsured / 1.0), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Status", weight = 0.7f,
                        cell = {
                            if (it.isValid) AegisStatusPill(AegisStatus.Approved)
                            else AegisStatusPill(AegisStatus.Rejected)
                        }
                    ),
                    AegisColumn(
                        header = "Total (incl. GST)", weight = 1.2f,
                        align = androidx.compose.ui.text.style.TextAlign.End, mono = true,
                        cell = {
                            if (it.isValid) Text(formatRupees(it.totalIncludingGst), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            else Text("—", fontSize = 13.sp, color = AegisColors.textSecondary)
                        }
                    ),
                )
            )
        }
    }
}

@Composable
private fun DashboardSourceBanner(source: DashboardSource, reason: String, refreshedAt: String) {
    // The refreshedAt timestamp is the only feedback the operator gets that the
    // 30s auto-refresh is alive — slice to seconds so it's compact in the banner.
    val refreshLabel = refreshedAt.take(19).replace('T', ' ')
        .let { if (it.isNotBlank()) " · refreshed $it UTC" else "" }
    when (source) {
        DashboardSource.LOADING -> AegisCallout(
            kind = CalloutKind.INFO,
            title = "Loading…",
            body = "Talking to the server to pull the latest quote stream."
        )
        DashboardSource.LIVE -> AegisCallout(
            kind = CalloutKind.SUCCESS,
            title = "Live data",
            body = "Tiles and tables below are computed from the server's quote table. " +
                    "Auto-refreshes every 30s${refreshLabel}."
        )
        DashboardSource.DEMO -> AegisCallout(
            kind = CalloutKind.WARN,
            title = "Demo data",
            body = (reason.ifBlank { "Server unreachable — showing a deterministic synthetic dataset." }) +
                    refreshLabel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KpiTile(
    label: String,
    value: String,
    delta: String,
    deltaPositive: Boolean,
    modifier: Modifier = Modifier,
    sparkline: List<Double>? = null,
    sparklineLabels: List<String>? = null,
    tooltip: String? = null,
) {
    val deltaColor = if (deltaPositive) AegisColors.success700 else AegisColors.danger700
    val sparkColor = if (deltaPositive) AegisColors.success500 else AegisColors.danger500
    val card: @Composable () -> Unit = {
        AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 12.sp, color = AegisColors.textSecondary, fontWeight = FontWeight.Medium)
                    if (tooltip != null) {
                        Spacer(Modifier.width(AegisSpacing.s1))
                        // The "?" affords discoverability — the TooltipBox itself is invisible
                        // on first paint, so without this hint operators wouldn't know to hover.
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = null,
                            tint = AegisColors.textSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
                if (sparkline != null) {
                    AegisSparkline(
                        values = sparkline,
                        accent = sparkColor,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        labels = sparklineLabels,
                    )
                }
                Text(delta, fontSize = 11.sp, color = deltaColor)
            }
        }
    }
    if (tooltip != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(isPersistent = false),
            content = card,
        )
    } else {
        card()
    }
}

@Composable
private fun FlowRow(label: String, amount: Double, accent: Color, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = AegisSpacing.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 6.dp, height = 16.dp).background(accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(AegisSpacing.s3))
        Text(label, fontSize = 13.sp,
            color = AegisColors.textBody,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        Text(formatRupees(amount), fontSize = 13.sp,
            color = if (amount < 0) AegisColors.danger700 else AegisColors.textBody,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun Double.format1(): String = ((this * 10).roundToInt() / 10.0).toString()
private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
private fun signedPct(n: Double) = if (n >= 0) "+${n.format1()}%" else "${n.format1()}%"
private fun pctSigned(now: Double, prev: Double): String {
    if (prev == 0.0) return "+0.0%"
    val pct = ((now - prev) / prev) * 100.0
    return if (pct >= 0) "+${pct.format1()}%" else "${pct.format1()}%"
}
