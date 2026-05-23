package com.rate.aegis.surfaces.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonSize
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisChip
import com.rate.aegis.components.AegisHDivider
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.DashboardSource
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.data.rememberDashboardData
import com.rate.aegis.business.calculator.api.RedactedSession
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.util.buildCsv
import com.rate.aegis.util.saveCsv
import com.rate.aegis.util.todayIsoDate
import com.rate.domain.money.formatRupees
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.roundToInt

/**
 * Aegis Reports — Ship-X analytics surface.
 *
 * Aggregates the live quote stream (via `rememberDashboardData()`) into
 * time-bucketed and categorical horizontal-bar visualisations. No chart
 * library: every bar is just a `Box` whose width is a fraction of the
 * available row width, sized off the bucket-vs-max ratio.
 *
 * Reads ONLY from `DashboardData.quotes` — there is no parallel fetch path,
 * so an empty / loading dataset just renders empty distributions.
 */
@Composable
fun ReportsSurface() {
    val dashboard by rememberDashboardData()
    val quotes = dashboard.quotes
    var bucket by remember { mutableStateOf(TimeBucket.Day) }

    val series = remember(quotes, bucket) { aggregateTimeSeries(quotes, bucket) }
    val planDist = remember(quotes) { aggregatePlans(quotes) }
    val ageDist = remember(quotes) { aggregateAges(quotes) }
    val siDist = remember(quotes) { aggregateSI(quotes) }
    val validCount = quotes.count { it.isValid }
    val invalidCount = quotes.size - validCount

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas)
            .verticalScroll(rememberScrollState()).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5)
    ) {
        // ── Title + helper ────────────────────────────────────────────────
        Text("Reports", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        Text(
            "Time-bucketed analytics over the live quote stream — volume, GWP, " +
                    "plan mix, age + sum-insured distribution, and validity ratio.",
            fontSize = 13.sp, color = AegisColors.textSecondary
        )

        // ── Source banner ─────────────────────────────────────────────────
        ReportsSourceBanner(
            source = dashboard.source,
            reason = dashboard.fallbackReason,
            refreshedAt = dashboard.refreshedAt,
            totalQuotes = quotes.size,
            bucketCount = series.size,
            bucketLabel = bucket.label.lowercase()
        )

        // ── Bucket toggle ─────────────────────────────────────────────────
        AegisCard(title = "Time bucket", subtitle = "How to group the quote stream below.") {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                TimeBucket.entries.forEach { b ->
                    AegisChip(
                        label = b.label,
                        selected = bucket == b,
                        onClick = { bucket = b }
                    )
                }
            }
        }

        // ── Quotes-per-bucket ─────────────────────────────────────────────
        AegisCard(
            title = "Quotes per ${bucket.singular}",
            subtitle = if (series.isEmpty())
                "No quotes in the current dataset."
            else
                "${series.size} ${bucket.label.lowercase()} buckets, ${quotes.size} quotes.",
            action = {
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = series.isNotEmpty(),
                    onClick = {
                        val csv = buildCsv(
                            headers = listOf(bucket.label, "Count"),
                            rows = series.map { listOf(it.label, it.count) }
                        )
                        saveCsv("aegis-reports-quotes-per-bucket-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            if (series.isEmpty()) {
                EmptyHint("Waiting on quotes from the server.")
            } else {
                val maxCount = series.maxOf { it.count }
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    series.forEach { bkt ->
                        BarRow(
                            label = bkt.label,
                            ratio = if (maxCount == 0) 0.0 else bkt.count.toDouble() / maxCount,
                            valueText = bkt.count.toString(),
                            barColor = AegisColors.indigo500
                        )
                    }
                }
            }
        }

        // ── GWP-per-bucket ────────────────────────────────────────────────
        AegisCard(
            title = "GWP per ${bucket.singular}",
            subtitle = "Sum of total (incl. GST) for valid quotes only.",
            action = {
                val hasGwp = series.any { it.gwp > 0.0 }
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = hasGwp,
                    onClick = {
                        val csv = buildCsv(
                            headers = listOf(bucket.label, "GWP (incl. GST)"),
                            rows = series.map { listOf(it.label, it.gwp) }
                        )
                        saveCsv("aegis-reports-gwp-per-bucket-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            if (series.isEmpty() || series.all { it.gwp == 0.0 }) {
                EmptyHint("No GWP to chart — either no quotes, or none are valid.")
            } else {
                val maxGwp = series.maxOf { it.gwp }
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    series.forEach { bkt ->
                        BarRow(
                            label = bkt.label,
                            ratio = if (maxGwp == 0.0) 0.0 else bkt.gwp / maxGwp,
                            valueText = formatRupees(bkt.gwp),
                            barColor = AegisColors.premium500
                        )
                    }
                }
            }
        }

        // ── Plan distribution ────────────────────────────────────────────
        AegisCard(
            title = "Plan distribution",
            subtitle = "Share of quotes per plan, sorted by volume.",
            action = {
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = planDist.isNotEmpty(),
                    onClick = {
                        val total = planDist.sumOf { it.count }
                        val csv = buildCsv(
                            headers = listOf("Plan", "Count", "Share %"),
                            rows = planDist.map { p ->
                                val sharePct = if (total > 0) (p.count * 100.0 / total) else 0.0
                                listOf(p.label, p.count, sharePct)
                            }
                        )
                        saveCsv("aegis-reports-plan-distribution-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            if (planDist.isEmpty()) {
                EmptyHint("No plans to chart yet.")
            } else {
                val total = planDist.sumOf { it.count }
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    planDist.forEach { p ->
                        val ratio = if (total == 0) 0.0 else p.count.toDouble() / total
                        BarRow(
                            label = p.label,
                            ratio = ratio,
                            valueText = "${p.count} · ${formatPct(ratio * 100.0)}%",
                            barColor = AegisColors.indigo500
                        )
                    }
                }
            }
        }

        // ── Age distribution ─────────────────────────────────────────────
        AegisCard(
            title = "Age distribution",
            subtitle = "Primary-life age bands across all quotes.",
            action = {
                val ageTotal = ageDist.sumOf { it.count }
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = ageTotal > 0,
                    onClick = {
                        val csv = buildCsv(
                            headers = listOf("Age band", "Count", "Share %"),
                            rows = ageDist.map { r ->
                                val sharePct = if (ageTotal > 0) (r.count * 100.0 / ageTotal) else 0.0
                                listOf(r.label, r.count, sharePct)
                            }
                        )
                        saveCsv("aegis-reports-age-distribution-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            DistributionList(ageDist, AegisColors.info500)
        }

        // ── SI distribution ──────────────────────────────────────────────
        AegisCard(
            title = "Sum-insured distribution",
            subtitle = "Cover amount bucketed across the quote ledger.",
            action = {
                val siTotal = siDist.sumOf { it.count }
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = siTotal > 0,
                    onClick = {
                        val csv = buildCsv(
                            headers = listOf("SI band", "Count", "Share %"),
                            rows = siDist.map { r ->
                                val sharePct = if (siTotal > 0) (r.count * 100.0 / siTotal) else 0.0
                                listOf(r.label, r.count, sharePct)
                            }
                        )
                        saveCsv("aegis-reports-si-distribution-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            DistributionList(siDist, AegisColors.success500)
        }

        // ── Validity ratio ───────────────────────────────────────────────
        AegisCard(
            title = "Validity",
            subtitle = "Engine-accepted quotes vs invalid ones.",
            action = {
                AegisButton(
                    label = "Export CSV",
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    enabled = quotes.isNotEmpty(),
                    onClick = {
                        val total = quotes.size
                        val validPct = if (total > 0) (validCount * 100.0 / total) else 0.0
                        val invalidPct = if (total > 0) (invalidCount * 100.0 / total) else 0.0
                        val csv = buildCsv(
                            headers = listOf("Status", "Count", "Share %"),
                            rows = listOf(
                                listOf("Valid", validCount, validPct),
                                listOf("Invalid", invalidCount, invalidPct),
                            )
                        )
                        saveCsv("aegis-reports-validity-${todayIsoDate()}.csv", csv)
                    }
                )
            }
        ) {
            val total = quotes.size
            if (total == 0) {
                EmptyHint("No quotes yet — validity unknown.")
            } else {
                val validRatio = validCount.toDouble() / total
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
                    ) {
                        Text("$validCount", fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
                        Text("/ $total valid", fontSize = 14.sp, color = AegisColors.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp))
                        Spacer(Modifier.width(AegisSpacing.s3))
                        Text("${formatPct(validRatio * 100.0)}%", fontSize = 18.sp,
                            color = if (validRatio >= 0.85) AegisColors.success700 else AegisColors.warn700,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 10.dp))
                    }
                    // Segmented bar — green for valid, red for invalid. We stack two
                    // boxes in a Row that share the available width via weight()
                    // so the segments stay proportional at any window size.
                    Row(
                        Modifier.fillMaxWidth().height(18.dp)
                            .background(AegisColors.slate3, RoundedCornerShape(9.dp))
                    ) {
                        if (validCount > 0) {
                            Box(
                                Modifier.weight(validCount.toFloat())
                                    .fillMaxWidth().height(18.dp)
                                    .background(AegisColors.success500, RoundedCornerShape(9.dp))
                            )
                        }
                        if (invalidCount > 0) {
                            Box(
                                Modifier.weight(invalidCount.toFloat())
                                    .fillMaxWidth().height(18.dp)
                                    .background(AegisColors.danger500, RoundedCornerShape(9.dp))
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
                        LegendDot(AegisColors.success500, "Valid · $validCount")
                        LegendDot(AegisColors.danger500, "Invalid · $invalidCount")
                    }
                }
            }
        }

        // ── Customer journey funnel ──────────────────────────────────────
        CustomerJourneyFunnelSection()

        // ── Footer ───────────────────────────────────────────────────────
        AegisHDivider()
        Text(
            "Aggregates over the last ${quotes.size} quotes returned by the server (default 200).",
            fontSize = 12.sp, color = AegisColors.textSecondary
        )
    }
}

// ── Customer journey funnel ──────────────────────────────────────────────
/**
 * Renders the 22-screen buyonline funnel using `/api/buy-online/sessions`.
 * One bar per screen; the bar's width tracks `count / maxCount` so the most
 * popular stage anchors the visual scale. Drop-off between consecutive
 * screens is computed in declaration order (Landing → … → Satisfaction).
 *
 * Privacy: the wire payload is server-redacted ([RedactedSession]) — mobile is
 * masked to its last 4 digits, pincode to its first 3, and PED/CI membership
 * collapses to a count. This surface only ever needed `currentScreen` for the
 * funnel groupBy, so the redaction is fully transparent to the visualisation.
 */
@Composable
private fun CustomerJourneyFunnelSection() {
    val client = rememberApiClient()
    var sessions by remember { mutableStateOf<List<RedactedSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client) {
        loading = true; error = null
        runCatching { client.listBuyOnlineSessions(limit = 500) }
            .onSuccess { sessions = it }
            .onFailure { error = it.message ?: it::class.simpleName ?: "Unknown error" }
        loading = false
    }

    // Compute funnel rows at the card scope so the export-CSV action sees the
    // same per-screen counts the bars render. Pre-population (empty sessions)
    // collapses to an empty list so the button can disable itself.
    val funnelRows: List<FunnelRow> = if (sessions.isEmpty()) emptyList() else {
        val perScreen = sessions.groupBy { it.currentScreen }.mapValues { it.value.size }
        BUYONLINE_SCREEN_ORDER.map { name -> FunnelRow(label = name, count = perScreen[name] ?: 0) }
    }

    AegisCard(
        title = "Customer journey funnel",
        subtitle = "Where buyonline sessions sit today, and where they drop off.",
        action = {
            val sessionsTotal = sessions.size
            AegisButton(
                label = "Export CSV",
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                enabled = funnelRows.isNotEmpty(),
                onClick = {
                    val csv = buildCsv(
                        headers = listOf("Stage order", "Screen", "Sessions", "Share %"),
                        rows = funnelRows.mapIndexed { idx, row ->
                            val sharePct = if (sessionsTotal > 0)
                                (row.count * 100.0 / sessionsTotal) else 0.0
                            listOf(idx + 1, row.label, row.count, sharePct)
                        }
                    )
                    saveCsv("aegis-reports-customer-journey-funnel-${todayIsoDate()}.csv", csv)
                }
            )
        }
    ) {
        when {
            loading -> EmptyHint("Loading buyonline sessions…")
            error != null -> AegisCallout(
                kind = CalloutKind.WARN,
                title = "Could not load sessions",
                body = error ?: "Unknown error fetching /api/buy-online/sessions."
            )
            sessions.isEmpty() -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "No sessions yet",
                body = "The funnel populates once customers begin the buyonline journey."
            )
            else -> {
                val rows = funnelRows
                val maxCount = rows.maxOfOrNull { it.count } ?: 0
                // Largest consecutive drop in the canonical order — that's the
                // "worst" leak in the funnel from the operator's POV.
                val biggestDrop = computeBiggestDrop(rows)

                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text(
                        buildString {
                            append("${sessions.size} sessions tracked")
                            if (biggestDrop != null) {
                                append(" · ${biggestDrop.lostCount} dropped at ${biggestDrop.fromLabel} → ${biggestDrop.toLabel}")
                            }
                        },
                        fontSize = 13.sp,
                        color = AegisColors.textSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        rows.forEach { row ->
                            FunnelBarRow(
                                label = row.label,
                                count = row.count,
                                maxCount = maxCount
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Canonical declaration order from BuyOnlineScreen sealed-class. */
private val BUYONLINE_SCREEN_ORDER: List<String> = listOf(
    "Landing", "Otp", "GetStarted", "PreExistingDisease", "CriticalIllness",
    "PlanLoading", "Eligibility", "Quote", "AddOns", "PlanSummary",
    "PersonalDetails", "LifestyleQuestions", "MedicalQuestions",
    "Payment", "PaymentSuccess", "KycMethod", "KycDetails", "KycOtp",
    "BankDetails", "KycSubmitted", "ApplicationComplete", "Satisfaction"
)

private data class FunnelRow(val label: String, val count: Int)
private data class FunnelDrop(val fromLabel: String, val toLabel: String, val lostCount: Int)

/**
 * Largest consecutive drop in declaration order. Counts are stage-occupancy
 * (where sessions currently sit) not cumulative reached-this-stage tallies,
 * but the drop heuristic is still useful — a big negative delta between
 * adjacent screens flags a stage where many sessions stall.
 */
private fun computeBiggestDrop(rows: List<FunnelRow>): FunnelDrop? {
    if (rows.size < 2) return null
    var best: FunnelDrop? = null
    for (i in 0 until rows.size - 1) {
        val lost = rows[i].count - rows[i + 1].count
        if (lost <= 0) continue
        val current = best
        if (current == null || lost > current.lostCount) {
            best = FunnelDrop(rows[i].label, rows[i + 1].label, lost)
        }
    }
    return best
}

@Composable
private fun FunnelBarRow(label: String, count: Int, maxCount: Int) {
    val ratio: Float = when {
        maxCount <= 0 -> 0f
        count <= 0 -> 0f
        else -> (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
    }
    Row(
        Modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = AegisColors.textBody,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(160.dp)
        )
        Box(
            Modifier.weight(1f).height(14.dp)
                .background(AegisColors.slate2, RoundedCornerShape(3.dp))
        ) {
            // Zero-count rows render a thin dim sliver so the rhythm holds.
            if (count == 0) {
                Box(
                    Modifier.fillMaxWidth(0.01f).height(14.dp)
                        .background(AegisColors.slate3, RoundedCornerShape(3.dp))
                )
            } else {
                val widthFraction = if (ratio < 0.02f) 0.02f else ratio
                Box(
                    Modifier.fillMaxWidth(widthFraction).height(14.dp)
                        .background(AegisColors.indigo500, RoundedCornerShape(3.dp))
                )
            }
        }
        Text(
            count.toString(),
            fontSize = 12.sp,
            color = AegisColors.textBody,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(56.dp)
        )
    }
}

// ── Source banner ────────────────────────────────────────────────────────
@Composable
private fun ReportsSourceBanner(
    source: DashboardSource,
    reason: String,
    refreshedAt: String,
    totalQuotes: Int,
    bucketCount: Int,
    bucketLabel: String,
) {
    val refreshLabel = refreshedAt.take(19).replace('T', ' ')
        .let { if (it.isNotBlank()) " · refreshed $it UTC" else "" }
    when (source) {
        DashboardSource.LOADING -> AegisCallout(
            kind = CalloutKind.INFO,
            title = "Loading…",
            body = "Pulling the quote stream from the server."
        )
        DashboardSource.LIVE -> AegisCallout(
            kind = CalloutKind.SUCCESS,
            title = "Live data",
            body = "Aggregating $totalQuotes quotes across $bucketCount $bucketLabel buckets." +
                    refreshLabel
        )
        DashboardSource.DEMO -> AegisCallout(
            kind = CalloutKind.WARN,
            title = "Demo data",
            body = (reason.ifBlank { "Server unreachable — showing a deterministic synthetic dataset." }) +
                    " Aggregating $totalQuotes quotes across $bucketCount $bucketLabel buckets." +
                    refreshLabel
        )
    }
}

// ── Bar row primitive ────────────────────────────────────────────────────
@Composable
private fun BarRow(
    label: String,
    ratio: Double,
    valueText: String,
    barColor: Color,
) {
    val safeRatio = ratio.toFloat().coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
    ) {
        // Fixed-width label so all bars start at the same x.
        Text(
            label,
            fontSize = 12.sp,
            color = AegisColors.textBody,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(120.dp)
        )
        // The track + filled bar live in a Box that takes the remaining width.
        Box(
            Modifier.weight(1f).height(18.dp)
                .background(AegisColors.slate2, RoundedCornerShape(4.dp))
        ) {
            // A zero-width bar still renders as a faint nub — give it a 2dp min
            // so empty buckets are distinguishable from un-populated rows.
            val widthFraction = if (safeRatio < 0.01f) 0.01f else safeRatio
            Box(
                Modifier.fillMaxWidth(widthFraction).height(18.dp)
                    .background(barColor, RoundedCornerShape(4.dp))
            )
        }
        Text(
            valueText,
            fontSize = 12.sp,
            color = AegisColors.textBody,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp)
        )
    }
}

@Composable
private fun DistributionList(
    rows: List<Distribution>,
    color: Color,
) {
    if (rows.isEmpty() || rows.all { it.count == 0 }) {
        EmptyHint("No data to bucket yet.")
        return
    }
    val total = rows.sumOf { it.count }
    val maxCount = rows.maxOf { it.count }
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        rows.forEach { r ->
            val ratio = if (maxCount == 0) 0.0 else r.count.toDouble() / maxCount
            val pct = if (total == 0) 0.0 else r.count.toDouble() / total * 100.0
            BarRow(
                label = r.label,
                ratio = ratio,
                valueText = "${r.count} · ${formatPct(pct)}%",
                barColor = color
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(10.dp).height(10.dp).background(color, RoundedCornerShape(5.dp)))
        Text(text, fontSize = 12.sp, color = AegisColors.textSecondary)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, fontSize = 13.sp, color = AegisColors.textSecondary)
}

// ── Aggregation logic ────────────────────────────────────────────────────
private data class TimeBucketRow(
    val key: String,
    val label: String,
    val count: Int,
    val gwp: Double,
)

private data class Distribution(val label: String, val count: Int)

private enum class TimeBucket(val label: String, val singular: String) {
    Day("Day", "day"),
    Week("Week", "week"),
    Month("Month", "month"),
}

/**
 * Group quotes into time buckets. Buckets are derived directly from the
 * `createdAt` ISO date prefix (`YYYY-MM-DD`).
 *
 *   Day   → first 10 chars (the date itself).
 *   Month → first 7 chars (`YYYY-MM`).
 *   Week  → ISO Monday of the date, computed via kotlinx-datetime. If the
 *           date string can't be parsed we fall back to bucketing by the
 *           day prefix so the row still appears.
 *
 * Returns the rows sorted ascending by key so chronological order is
 * preserved in the visualisation.
 */
private fun aggregateTimeSeries(
    quotes: List<FakeAegisRepo.FakeQuote>,
    bucket: TimeBucket,
): List<TimeBucketRow> {
    if (quotes.isEmpty()) return emptyList()
    val keyed = quotes.mapNotNull { q ->
        val raw = q.createdAt.take(10)
        if (raw.length != 10) return@mapNotNull null
        val key = when (bucket) {
            TimeBucket.Day -> raw
            TimeBucket.Month -> raw.take(7)
            TimeBucket.Week -> weekKey(raw) ?: raw
        }
        key to q
    }
    return keyed.groupBy { it.first }
        .map { (key, pairs) ->
            val rows = pairs.map { it.second }
            TimeBucketRow(
                key = key,
                label = when (bucket) {
                    TimeBucket.Day -> key
                    TimeBucket.Month -> key       // already YYYY-MM
                    TimeBucket.Week -> "wk of $key"
                },
                count = rows.size,
                gwp = rows.filter { it.isValid }.sumOf { it.totalIncludingGst }
            )
        }
        .sortedBy { it.key }
}

/**
 * For a `YYYY-MM-DD` string, return the Monday of that ISO week as `YYYY-MM-DD`.
 * Returns null if parsing fails (so the caller can fall back to day-grouping).
 */
private fun weekKey(yyyyMmDd: String): String? {
    val parsed = runCatching { LocalDate.parse(yyyyMmDd) }.getOrNull() ?: return null
    val daysSinceMonday = (parsed.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
    val monday = parsed.minus(daysSinceMonday, kotlinx.datetime.DateTimeUnit.DAY)
    return monday.toString()
}

private fun aggregatePlans(quotes: List<FakeAegisRepo.FakeQuote>): List<Distribution> =
    quotes.groupBy { it.planId }
        .map { (planId, rows) ->
            val name = rows.first().planName.ifBlank { planId }
            Distribution(label = name, count = rows.size)
        }
        .sortedByDescending { it.count }

private fun aggregateAges(quotes: List<FakeAegisRepo.FakeQuote>): List<Distribution> {
    val bands = listOf(
        "<26"   to { a: Int -> a < 26 },
        "26-35" to { a: Int -> a in 26..35 },
        "36-45" to { a: Int -> a in 36..45 },
        "46-55" to { a: Int -> a in 46..55 },
        "56-65" to { a: Int -> a in 56..65 },
        "65+"   to { a: Int -> a > 65 },
    )
    return bands.map { (label, pred) ->
        Distribution(label, quotes.count { pred(it.primaryAge) })
    }
}

private fun aggregateSI(quotes: List<FakeAegisRepo.FakeQuote>): List<Distribution> {
    val ten   = 10L * 100_000L         // ₹10L
    val twoFv = 25L * 100_000L
    val fifty = 50L * 100_000L
    val cr    = 100L * 100_000L        // ₹1Cr
    val bands = listOf(
        "≤ ₹10L"  to { si: Long -> si <= ten },
        "≤ ₹25L"  to { si: Long -> si in (ten + 1)..twoFv },
        "≤ ₹50L"  to { si: Long -> si in (twoFv + 1)..fifty },
        "≤ ₹1Cr"  to { si: Long -> si in (fifty + 1)..cr },
        "> ₹1Cr"  to { si: Long -> si > cr },
    )
    return bands.map { (label, pred) ->
        Distribution(label, quotes.count { pred(it.sumInsured) })
    }
}

/** One-decimal percentage formatting that doesn't rely on `String.format`. */
private fun formatPct(value: Double): String {
    val rounded = (value * 10.0).roundToInt()
    val whole = rounded / 10
    val frac = (if (rounded < 0) -rounded else rounded) % 10
    return "$whole.$frac"
}
