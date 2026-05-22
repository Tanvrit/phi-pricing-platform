package com.rate.aegis.surfaces.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.*
import com.rate.aegis.data.FakeAegisRepo
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
    val kpis = FakeAegisRepo.homeKpis
    val recentQuotes = FakeAegisRepo.quotes.sortedByDescending { it.createdAt }.take(10)
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas)
            .verticalScroll(rememberScrollState()).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5)
    ) {
        Text("Home", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        Text("PRUHealth Aegis — operations snapshot. Tiles update from the audit_event " +
                "stream + quotes table; right now this surface reads from the synthetic repo.",
            fontSize = 14.sp, color = AegisColors.textSecondary)

        // KPI tiles ─────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            KpiTile(
                label = "Quotes today",
                value = kpis.quotesToday.toString(),
                delta = "${signed(kpis.quotesToday - kpis.quotesYesterday)} vs yesterday",
                deltaPositive = kpis.quotesToday >= kpis.quotesYesterday,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "GWP this month",
                value = formatRupees(kpis.gwpThisMonth),
                delta = "${pctSigned(kpis.gwpThisMonth, kpis.gwpLastMonth)} vs last month",
                deltaPositive = kpis.gwpThisMonth >= kpis.gwpLastMonth,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Conversion rate",
                value = "${kpis.conversionRatePct.format1()}%",
                delta = "${signedPct(kpis.conversionRateDeltaPct)} vs trailing 7d",
                deltaPositive = kpis.conversionRateDeltaPct >= 0,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "Active plans",
                value = kpis.activePlans.toString(),
                delta = "${kpis.draftPlans} draft · ${kpis.retiredPlans} retired",
                deltaPositive = true,
                modifier = Modifier.weight(1f)
            )
            KpiTile(
                label = "UW backlog",
                value = kpis.uwBacklog.toString(),
                delta = if (kpis.uwBreaches > 0)
                    "${kpis.uwBreaches} SLA-breached" else "All within SLA",
                deltaPositive = kpis.uwBreaches == 0,
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
            val q = FakeAegisRepo.quotes
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

        // Recent activity table ────────────────────────────────────────────
        AegisCard(title = "Recent quotes") {
            AegisTable(
                items = recentQuotes,
                columns = listOf(
                    AegisColumn<FakeAegisRepo.FakeQuote>(
                        header = "Quote ID", weight = 1.6f,
                        cell = { Text(it.id, fontSize = 13.sp, color = AegisColors.textBody) }
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
private fun KpiTile(
    label: String,
    value: String,
    delta: String,
    deltaPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val deltaColor = if (deltaPositive) AegisColors.success700 else AegisColors.danger700
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(label, fontSize = 12.sp, color = AegisColors.textSecondary, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
            Text(delta, fontSize = 11.sp, color = deltaColor)
        }
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
