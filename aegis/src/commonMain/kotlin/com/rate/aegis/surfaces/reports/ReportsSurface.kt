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
import com.rate.aegis.LocalRefreshTicker
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
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
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
    var bucket by remember { mutableStateOf(TimeBucket.Day) }
    var dateFilter by remember { mutableStateOf<DateFilter>(DateFilter.AllTime) }

    // The date-range chip row pre-filters the quote ledger before every
    // downstream aggregation. The funnel section deliberately ignores this
    // filter because its data source is sessions, not quotes — see the note
    // rendered above the funnel card.
    val quotes = remember(dashboard.quotes, dateFilter) {
        if (dateFilter is DateFilter.AllTime) dashboard.quotes
        else dashboard.quotes.filter { dateFilter.matches(it.createdAt) }
    }
    val unfilteredCount = dashboard.quotes.size

    val series = remember(quotes, bucket) { aggregateTimeSeries(quotes, bucket) }
    val planDist = remember(quotes) { aggregatePlans(quotes) }
    val ageDist = remember(quotes) { aggregateAges(quotes) }
    val siDist = remember(quotes) { aggregateSI(quotes) }
    val validCount = quotes.count { it.isValid }
    val invalidCount = quotes.size - validCount
    val filterSuffix = if (dateFilter is DateFilter.AllTime) "" else " (${dateFilter.label.lowercase()})"

    // Funnel data is fetched here at the parent scope so the top-level
    // "Export all" button can read the same per-screen counts the funnel
    // card renders. The funnel section composable receives these as params.
    val client = rememberApiClient()
    var funnelSessions by remember { mutableStateOf<List<RedactedSession>>(emptyList()) }
    var funnelLoading by remember { mutableStateOf(false) }
    var funnelError by remember { mutableStateOf<String?>(null) }
    val refreshTick by LocalRefreshTicker.current
    LaunchedEffect(client, refreshTick) {
        funnelLoading = true; funnelError = null
        runCatching { client.listBuyOnlineSessions(limit = 500) }
            .onSuccess { funnelSessions = it }
            .onFailure { funnelError = it.message ?: it::class.simpleName ?: "Unknown error" }
        funnelLoading = false
    }
    val funnelRows: List<FunnelRow> = if (funnelSessions.isEmpty()) emptyList() else {
        val perScreen = funnelSessions.groupBy { it.currentScreen }.mapValues { it.value.size }
        BUYONLINE_SCREEN_ORDER.map { name -> FunnelRow(label = name, count = perScreen[name] ?: 0) }
    }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas)
            .verticalScroll(rememberScrollState()).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5)
    ) {
        // ── Title + helper + Export-all ───────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Reports", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
            val anyData = series.isNotEmpty() || planDist.isNotEmpty() ||
                    ageDist.any { it.count > 0 } || siDist.any { it.count > 0 } ||
                    quotes.isNotEmpty() || funnelRows.isNotEmpty()
            AegisButton(
                label = "Export all (7 sections)",
                variant = AegisButtonVariant.Primary,
                size = AegisButtonSize.Sm,
                enabled = anyData,
                onClick = {
                    val sb = StringBuilder()
                    sb.append("# aegis reports bundle · generated ").append(todayIsoDate()).append('\n')

                    fun section(title: String, headers: List<String>, rows: List<List<Any?>>) {
                        sb.append("# ").append(title).append('\n')
                        sb.append(buildCsv(headers, rows))
                        sb.append('\n')
                    }

                    // 1) Quotes per bucket
                    section(
                        "Quotes per ${bucket.singular}",
                        listOf(bucket.label, "Count"),
                        series.map { listOf(it.label, it.count) }
                    )
                    // 2) GWP per bucket
                    section(
                        "GWP per ${bucket.singular}",
                        listOf(bucket.label, "GWP (incl. GST)"),
                        series.map { listOf(it.label, it.gwp) }
                    )
                    // 3) Plan distribution
                    val planTotal = planDist.sumOf { it.count }
                    section(
                        "Plan distribution",
                        listOf("Plan", "Count", "Share %"),
                        planDist.map { p ->
                            val sharePct = if (planTotal > 0) (p.count * 100.0 / planTotal) else 0.0
                            listOf(p.label, p.count, sharePct)
                        }
                    )
                    // 4) Age distribution
                    val ageTotal = ageDist.sumOf { it.count }
                    section(
                        "Age distribution",
                        listOf("Age band", "Count", "Share %"),
                        ageDist.map { r ->
                            val sharePct = if (ageTotal > 0) (r.count * 100.0 / ageTotal) else 0.0
                            listOf(r.label, r.count, sharePct)
                        }
                    )
                    // 5) Sum-insured distribution
                    val siTotal = siDist.sumOf { it.count }
                    section(
                        "Sum-insured distribution",
                        listOf("SI band", "Count", "Share %"),
                        siDist.map { r ->
                            val sharePct = if (siTotal > 0) (r.count * 100.0 / siTotal) else 0.0
                            listOf(r.label, r.count, sharePct)
                        }
                    )
                    // 6) Validity
                    val total = quotes.size
                    val validPct = if (total > 0) (validCount * 100.0 / total) else 0.0
                    val invalidPct = if (total > 0) (invalidCount * 100.0 / total) else 0.0
                    section(
                        "Validity",
                        listOf("Status", "Count", "Share %"),
                        listOf(
                            listOf("Valid", validCount, validPct),
                            listOf("Invalid", invalidCount, invalidPct),
                        )
                    )
                    // 7) Customer journey funnel
                    val sessionsTotal = funnelSessions.size
                    section(
                        "Customer journey funnel",
                        listOf("Stage order", "Screen", "Sessions", "Share %"),
                        funnelRows.mapIndexed { idx, row ->
                            val sharePct = if (sessionsTotal > 0)
                                (row.count * 100.0 / sessionsTotal) else 0.0
                            listOf(idx + 1, row.label, row.count, sharePct)
                        }
                    )

                    saveCsv("aegis-reports-all-${todayIsoDate()}.csv", sb.toString())
                }
            )
        }
        Text(
            "Time-bucketed analytics over the live quote stream — volume, GWP, " +
                    "plan mix, age + sum-insured distribution, and validity ratio.",
            fontSize = 13.sp, color = AegisColors.textSecondary
        )

        // ── Date-range chips ──────────────────────────────────────────────
        // Operator-picked window applied to the quote ledger only. The funnel
        // card (sessions → screen) bypasses this filter by design (v1).
        AegisCard(
            title = "Date range",
            subtitle = if (dateFilter is DateFilter.AllTime)
                "Showing $unfilteredCount quotes across all time."
            else
                "Showing ${quotes.size} of $unfilteredCount quotes · ${dateFilter.label}.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                DateFilter.ALL.forEach { df ->
                    AegisChip(
                        label = df.label,
                        selected = dateFilter::class == df::class,
                        onClick = { dateFilter = df }
                    )
                }
            }
        }

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
                "No quotes in the current dataset${filterSuffix}."
            else
                "Aggregating ${quotes.size} quotes${filterSuffix} across ${series.size} ${bucket.label.lowercase()} buckets.",
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

        // ── Quote total distribution (histogram over all polled quotes) ──
        QuoteTotalDistributionCard(quotes = quotes, cap = quotes.size)

        // ── GWP-per-bucket ────────────────────────────────────────────────
        AegisCard(
            title = "GWP per ${bucket.singular}",
            subtitle = "Sum of total (incl. GST) for valid quotes only${filterSuffix}.",
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
            subtitle = "Share of quotes per plan, sorted by volume${filterSuffix}.",
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
            subtitle = "Primary-life age bands across all quotes${filterSuffix}.",
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
            subtitle = "Cover amount bucketed across the quote ledger${filterSuffix}.",
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
            subtitle = "Engine-accepted quotes vs invalid ones${filterSuffix}.",
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
        if (dateFilter !is DateFilter.AllTime) {
            Text(
                "Note: the date-range filter applies to quotes only — the funnel below is over all buyonline sessions.",
                fontSize = 12.sp,
                color = AegisColors.textSecondary
            )
        }
        CustomerJourneyFunnelSection(
            sessions = funnelSessions,
            loading = funnelLoading,
            error = funnelError,
            funnelRows = funnelRows,
        )

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
private fun CustomerJourneyFunnelSection(
    sessions: List<RedactedSession>,
    loading: Boolean,
    error: String?,
    funnelRows: List<FunnelRow>,
) {
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
                // Time-to-completion stat over sessions that reached a terminal
                // screen. Computed at composition time off the loaded sessions
                // list — see [computeCompletionStats] for the duration math.
                val completion = computeCompletionStats(sessions)

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
                    CompletionStatsRow(completion)
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

/** Canonical terminal screens. A session reaching either one is "completed". */
private val BUYONLINE_TERMINAL_SCREENS = setOf("ApplicationComplete", "Satisfaction")

/**
 * Aggregate time-to-completion for sessions that reached a terminal screen.
 * [completedCount] is the count regardless of whether timestamps parsed; the
 * [avgMillis] / [medianMillis] are computed only over sessions whose
 * `createdAtIso` and `updatedAtIso` both parse via [Instant.parse]. If no
 * durations could be computed, both are null.
 */
private data class CompletionStats(
    val completedCount: Int,
    val avgMillis: Long?,
    val medianMillis: Long?,
)

/**
 * Compute time-to-completion stats. A session counts as completed when its
 * `currentScreen` is in [BUYONLINE_TERMINAL_SCREENS]. We pull duration from
 * `updatedAtIso - createdAtIso` over completed sessions only — drop-outs would
 * skew the average toward "stuck halfway through" forever. Sessions with
 * unparseable or missing timestamps are silently excluded from the avg/median
 * math (but still counted in [completedCount]); negative durations (clock
 * skew, replayed migrations) are also dropped.
 */
private fun computeCompletionStats(sessions: List<RedactedSession>): CompletionStats {
    val completed = sessions.filter { it.currentScreen in BUYONLINE_TERMINAL_SCREENS }
    val durations: List<Long> = completed.mapNotNull { s ->
        val start = runCatching { Instant.parse(s.createdAtIso) }.getOrNull()
        val end = runCatching { Instant.parse(s.updatedAtIso) }.getOrNull()
        if (start == null || end == null) return@mapNotNull null
        val delta = end.toEpochMilliseconds() - start.toEpochMilliseconds()
        if (delta < 0L) null else delta
    }
    if (durations.isEmpty()) {
        return CompletionStats(completedCount = completed.size, avgMillis = null, medianMillis = null)
    }
    val avg = durations.sum() / durations.size
    val sorted = durations.sorted()
    val median = if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        // Even length — average the two middle elements. Integer division is
        // fine here; we only render minutes anyway.
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
    return CompletionStats(completedCount = completed.size, avgMillis = avg, medianMillis = median)
}

/**
 * Format a millisecond duration as `HH:MM`. Hours can exceed 99 — long-running
 * sessions are normal in this funnel (customers walk away and resume). For
 * durations under a minute we still surface `00:00` rather than rounding to
 * something misleading; the "Sessions completed" pill makes the existence of
 * the sample obvious so this is unambiguous in context.
 */
private fun formatHoursMinutes(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    val hh = if (hours < 10L) "0$hours" else hours.toString()
    val mm = if (minutes < 10L) "0$minutes" else minutes.toString()
    return "$hh:$mm"
}

@Composable
private fun CompletionStatsRow(stats: CompletionStats) {
    if (stats.completedCount == 0) {
        Text(
            "No completed sessions yet — averages appear once customers reach the end of the journey.",
            fontSize = 12.sp,
            color = AegisColors.textSecondary
        )
        return
    }
    val avgText = stats.avgMillis?.let { formatHoursMinutes(it) } ?: "—"
    val medianText = stats.medianMillis?.let { formatHoursMinutes(it) } ?: "—"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatPill(label = "Sessions completed", value = stats.completedCount.toString())
        StatPill(label = "Avg time-to-completion", value = avgText)
        StatPill(label = "Median", value = medianText)
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(
        Modifier
            .background(AegisColors.slate2, RoundedCornerShape(8.dp))
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = AegisColors.textSecondary)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
    }
}

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

// ── Quote total distribution histogram ──────────────────────────────────
private const val QUOTE_TOTAL_BUCKET_COUNT = 10
private const val QUOTE_TOTAL_LABEL_ROUNDING = 10_000.0   // ₹10k granularity

private data class QuoteTotalBucket(
    val low: Double,
    val high: Double,
    val count: Int,
)

private fun roundToTenK(v: Double): Double {
    if (v <= 0.0) return 0.0
    return (kotlin.math.round(v / QUOTE_TOTAL_LABEL_ROUNDING)) * QUOTE_TOTAL_LABEL_ROUNDING
}

/**
 * Compute 10 equal-width buckets over [totals]. Last bucket is inclusive of the
 * max so the peak quote always lands in bucket 9 rather than overflowing to 10.
 * Caller guarantees `totals.size >= 2` and a non-zero spread.
 */
private fun computeQuoteTotalBuckets(totals: List<Double>): List<QuoteTotalBucket> {
    val minV = totals.min()
    val maxV = totals.max()
    val width = (maxV - minV) / QUOTE_TOTAL_BUCKET_COUNT
    val counts = IntArray(QUOTE_TOTAL_BUCKET_COUNT)
    totals.forEach { v ->
        val idx = if (v >= maxV) QUOTE_TOTAL_BUCKET_COUNT - 1
                  else ((v - minV) / width).toInt().coerceIn(0, QUOTE_TOTAL_BUCKET_COUNT - 1)
        counts[idx] = counts[idx] + 1
    }
    return (0 until QUOTE_TOTAL_BUCKET_COUNT).map { i ->
        val lo = minV + width * i
        val hi = if (i == QUOTE_TOTAL_BUCKET_COUNT - 1) maxV else minV + width * (i + 1)
        QuoteTotalBucket(low = lo, high = hi, count = counts[i])
    }
}

@Composable
private fun QuoteTotalDistributionCard(
    quotes: List<FakeAegisRepo.FakeQuote>,
    cap: Int,
) {
    val validTotals = quotes
        .filter { it.isValid }
        .map { it.totalIncludingGst }
        .filter { it > 0.0 }

    // Pre-compute buckets so both the card body and the Export CSV action can
    // read the same numbers. We only build them when there's enough spread to
    // chart — otherwise the action stays disabled and the body shows a hint.
    val spread: Double = if (validTotals.size >= 2) validTotals.max() - validTotals.min() else 0.0
    val buckets: List<QuoteTotalBucket> =
        if (validTotals.size >= 2 && spread > 0.0) computeQuoteTotalBuckets(validTotals) else emptyList()

    AegisCard(
        title = "Quote total distribution",
        subtitle = "Premium spread across all polled quotes (post-GST).",
        action = {
            AegisButton(
                label = "Export CSV",
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                enabled = buckets.isNotEmpty(),
                onClick = {
                    val csv = buildCsv(
                        headers = listOf("Bucket low", "Bucket high", "Count"),
                        rows = buckets.map { listOf(it.low, it.high, it.count) }
                    )
                    saveCsv(
                        "aegis-reports-quote-distribution-${todayIsoDate()}.csv",
                        csv
                    )
                }
            )
        }
    ) {
        when {
            validTotals.size < 2 -> EmptyHint("Not enough valid quotes to chart.")
            spread <= 0.0 -> {
                // Degenerate: every valid quote has the same total. Show the
                // summary line but skip the bars (10 equal-width buckets would
                // all collapse to width 0).
                val only = validTotals.first()
                Text(
                    "${validTotals.size} valid quotes · all at ${formatRupees(only)}",
                    fontSize = 13.sp,
                    color = AegisColors.textSecondary
                )
            }
            else -> {
                val minV = validTotals.min()
                val maxV = validTotals.max()
                val avg = validTotals.sum() / validTotals.size
                // Median: for odd N take the middle element, for even N average
                // the two middle elements. Sort once and reuse.
                val sorted = validTotals.sorted()
                val median = if (sorted.size % 2 == 1) {
                    sorted[sorted.size / 2]
                } else {
                    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                }
                val peak = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    Text(
                        "${validTotals.size} valid quotes · ${formatRupees(minV)} to ${formatRupees(maxV)} · " +
                                "avg ${formatRupees(avg)} · median ${formatRupees(median)}",
                        fontSize = 13.sp,
                        color = AegisColors.textSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                        buckets.forEach { b ->
                            val ratio = b.count.toDouble() / peak
                            val isPeak = b.count == peak && b.count > 0
                            BarRow(
                                label = "${formatRupees(roundToTenK(b.low))} – ${formatRupees(roundToTenK(b.high))}",
                                ratio = ratio,
                                valueText = b.count.toString(),
                                barColor = if (isPeak) AegisColors.indigo500
                                           else AegisColors.info500
                            )
                        }
                    }
                    Text(
                        "All quotes from the last $cap polled.",
                        fontSize = 12.sp,
                        color = AegisColors.textSecondary
                    )
                }
            }
        }
    }
}

// ── Date-range filter ───────────────────────────────────────────────────
/**
 * Operator-selectable date window over the `createdAt` (YYYY-MM-DD) field of
 * [FakeAegisRepo.FakeQuote]. All windows are inclusive on both ends and resolve
 * "today" via [Clock.System.todayIn] in UTC — matching the server's `createdAt`
 * timezone semantics from `LiveDashboardRepo`.
 *
 * Malformed / unparseable `createdAt` strings are excluded when a non-AllTime
 * filter is active. That keeps the aggregations honest (we'd rather drop a
 * row than misplace it in the wrong bucket) but means a buggy upstream date
 * format will silently shrink the dataset — fine for a v1.
 */
private sealed class DateFilter(val label: String) {
    object AllTime : DateFilter("All time")
    object Today : DateFilter("Today")
    object Last7Days : DateFilter("Last 7 days")
    object Last30Days : DateFilter("Last 30 days")
    object ThisMonth : DateFilter("This month")
    object LastMonth : DateFilter("Last month")

    fun matches(createdAt: String): Boolean {
        if (this is AllTime) return true
        val date = runCatching { LocalDate.parse(createdAt.take(10)) }.getOrNull() ?: return false
        val today = Clock.System.todayIn(TimeZone.UTC)
        return when (this) {
            is AllTime -> true
            is Today -> date == today
            is Last7Days -> {
                val start = today.minus(6, DateTimeUnit.DAY) // inclusive 7-day window
                date in start..today
            }
            is Last30Days -> {
                val start = today.minus(29, DateTimeUnit.DAY)
                date in start..today
            }
            is ThisMonth -> date.year == today.year && date.month == today.month
            is LastMonth -> {
                val firstOfThis = LocalDate(today.year, today.month, 1)
                val lastOfLast = firstOfThis.minus(1, DateTimeUnit.DAY)
                date.year == lastOfLast.year && date.month == lastOfLast.month
            }
        }
    }

    companion object {
        val ALL: List<DateFilter> = listOf(AllTime, Today, Last7Days, Last30Days, ThisMonth, LastMonth)
    }
}

/** One-decimal percentage formatting that doesn't rely on `String.format`. */
private fun formatPct(value: Double): String {
    val rounded = (value * 10.0).roundToInt()
    val whole = rounded / 10
    val frac = (if (rounded < 0) -rounded else rounded) % 10
    return "$whole.$frac"
}
