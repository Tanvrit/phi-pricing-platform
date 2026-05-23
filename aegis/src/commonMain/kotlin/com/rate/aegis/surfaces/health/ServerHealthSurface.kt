package com.rate.aegis.surfaces.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.components.*
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * Aegis SERVER HEALTH surface — operator's in-app readout of the server's
 * `/health` and `/metrics` endpoints. Replaces "curl localhost" with a live
 * dashboard.
 *
 * Two independent polling loops:
 *   - `/health` every 5s — fast enough to spot the server going down mid-shift
 *     without hammering it. Each round-trip is timed via [Clock.System.now]
 *     bracketing the call so we report wall-clock latency, not server-side
 *     timing.
 *   - `/metrics` every 10s — Prometheus text format is heavier and rarely
 *     changes that quickly. Parsed into a `Map<String, Double>` by splitting
 *     each non-comment line on the first whitespace and stripping any
 *     `{label="…"}` block from the metric name for the display row.
 */
@Composable
fun ServerHealthSurface() {
    val client = rememberApiClient()

    // /health state
    var healthOk by remember { mutableStateOf<Boolean?>(null) }    // null = loading
    var healthLatencyMs by remember { mutableStateOf<Long?>(null) }
    var healthFields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var healthError by remember { mutableStateOf<String?>(null) }

    // /metrics state
    var metricsRaw by remember { mutableStateOf<String?>(null) }
    var metricsMap by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var metricsError by remember { mutableStateOf<String?>(null) }

    var rawExpanded by remember { mutableStateOf(false) }

    val refreshTick by LocalRefreshTicker.current

    // ── /health poller (5s) ──────────────────────────────────────────────
    LaunchedEffect(client, refreshTick) {
        while (coroutineContext.isActive) {
            val before = Clock.System.now()
            runCatching { client.health() }
                .onSuccess { payload ->
                    val after = Clock.System.now()
                    healthLatencyMs = (after - before).inWholeMilliseconds
                    healthOk = true
                    healthError = null
                    healthFields = payload.mapValues { (_, v) -> jsonElementToDisplay(v) }
                }
                .onFailure { t ->
                    val after = Clock.System.now()
                    healthLatencyMs = (after - before).inWholeMilliseconds
                    healthOk = false
                    healthError = t.message ?: t::class.simpleName ?: "unknown error"
                }
            delay(5_000L)
        }
    }

    // ── /metrics poller (10s) ────────────────────────────────────────────
    LaunchedEffect(client, refreshTick) {
        while (coroutineContext.isActive) {
            runCatching { client.metricsText() }
                .onSuccess { text ->
                    metricsRaw = text
                    metricsMap = parsePrometheus(text)
                    metricsError = null
                }
                .onFailure { t ->
                    metricsError = t.message ?: t::class.simpleName ?: "unknown error"
                }
            delay(10_000L)
        }
    }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text("Server Health", fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody)
        Text(
            "Live readout of /health and /metrics. Auto-refreshes every 5s.",
            fontSize = 13.sp, color = AegisColors.textSecondary
        )

        // ── 2. Status card ──────────────────────────────────────────────
        AegisCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
                val dotColor = when (healthOk) {
                    true -> AegisColors.success500
                    false -> AegisColors.danger500
                    null -> AegisColors.slate5
                }
                Box(Modifier.size(16.dp).background(dotColor, CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val headline = when (healthOk) {
                        true -> "Server reachable in ${healthLatencyMs ?: 0} ms"
                        false -> "Server unreachable: ${healthError ?: "unknown error"}"
                        null -> "Probing server…"
                    }
                    val headlineColor = when (healthOk) {
                        true -> AegisColors.success700
                        false -> AegisColors.danger700
                        null -> AegisColors.textSecondary
                    }
                    Text(headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = headlineColor)
                    Text(client.baseUrl, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, color = AegisColors.textTertiary)
                }
            }
        }

        // ── 3. Health detail card ───────────────────────────────────────
        AegisCard(title = "Health payload",
            subtitle = "Every field returned by GET /health") {
            if (healthFields.isEmpty()) {
                Text(
                    if (healthOk == false) "(no payload — last request failed)" else "Waiting for first response…",
                    fontSize = 13.sp, color = AegisColors.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    healthFields.entries.forEachIndexed { idx, (k, v) ->
                        KeyValueRow(k, v)
                        if (idx < healthFields.size - 1) AegisHDivider()
                    }
                }
            }
        }

        // ── 3b. OTP activity card ───────────────────────────────────────
        // Pinned above the noisy top-20 metrics list so an abuse spike (lots
        // of `otp_rate_limited_total`) is immediately visible. Each tile shows
        // "—" until the server has been hit at least once or if the build
        // predates these counters (graceful degradation).
        AegisCard(
            title = "OTP activity",
            subtitle = "Send/verify counters and the per-mobile rate-limit signal",
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                OtpTile("Sent", "otp_sent_total", metricsMap, Modifier.weight(1f))
                OtpTile("Verified", "otp_verify_success_total", metricsMap, Modifier.weight(1f))
                OtpTile("Failed", "otp_verify_failure_total", metricsMap, Modifier.weight(1f))
                OtpTile("Rate-limited", "otp_rate_limited_total", metricsMap, Modifier.weight(1f))
            }
        }

        // ── 3c. Idempotency activity card ───────────────────────────────
        // Mirrors the OTP card above. Useful for diagnosing client retry
        // storms — a high `idempotent_replay_total` relative to
        // `idempotent_new_total` means clients are aggressively retrying
        // POSTs, and a non-zero `idempotent_conflict_total` means a client
        // is reusing keys with mutated bodies (almost always a bug).
        AegisCard(
            title = "Idempotency activity",
            subtitle = "Idempotency-Key replay vs. fresh request counters",
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                OtpTile("New", "idempotent_new_total", metricsMap, Modifier.weight(1f))
                OtpTile("Replay", "idempotent_replay_total", metricsMap, Modifier.weight(1f))
                OtpTile("Conflict", "idempotent_conflict_total", metricsMap, Modifier.weight(1f))
            }
        }

        // ── 4. Metrics card ─────────────────────────────────────────────
        AegisCard(
            title = "Prometheus metrics",
            subtitle = if (metricsError != null) "Last fetch failed: $metricsError"
                       else "${metricsMap.size} metrics returned · top 20 by absolute value"
        ) {
            if (metricsMap.isEmpty() && metricsError == null) {
                Text("Waiting for first /metrics response…", fontSize = 13.sp,
                    color = AegisColors.textSecondary)
            } else if (metricsMap.isEmpty()) {
                Text("(no metrics parsed)", fontSize = 13.sp,
                    color = AegisColors.textSecondary)
            } else {
                val top = remember(metricsMap) {
                    metricsMap.entries
                        .sortedByDescending { abs(it.value) }
                        .take(20)
                }
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s1)) {
                    top.forEachIndexed { idx, e ->
                        MetricRow(stripLabels(e.key), e.value)
                        if (idx < top.size - 1) AegisHDivider()
                    }
                }
            }
        }

        // ── 5. Raw fold-out ─────────────────────────────────────────────
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Raw /metrics text",
            body = if (rawExpanded) "Showing the unparsed exposition format below — useful for double-checking line shapes."
                   else "Collapsed by default; expand to inspect the exposition format directly."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            AegisButton(
                label = if (rawExpanded) "Hide raw" else "Show raw",
                onClick = { rawExpanded = !rawExpanded },
                variant = AegisButtonVariant.Secondary,
                size = AegisButtonSize.Sm,
            )
        }
        if (rawExpanded) {
            AegisCard(padding = PaddingValues(0.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(AegisColors.surfaceMuted, RoundedCornerShape(AegisRadii.rMd))
                        .verticalScroll(rememberScrollState())
                        .padding(AegisSpacing.s4)
                ) {
                    Text(
                        metricsRaw ?: "(nothing fetched yet)",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AegisColors.textBody,
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────

/**
 * Parse the Prometheus text exposition format into a flat name→value map.
 *
 * Each non-comment, non-blank line is of the form
 *   `metric_name 1.0`
 *   `metric_name{label="x"} 1.0`
 *   `metric_name 1.0 1700000000000`  (optional timestamp ignored)
 *
 * Strategy: skip lines starting with `#`, split on the first whitespace into
 * `name + rest`, take the first token of `rest` as the value, parse it as a
 * Double. Lines that fail parsing are silently dropped — operators looking for
 * the literal bytes use the raw fold-out below. If two lines share the same
 * stripped name (e.g. two label sets), the larger absolute value wins so the
 * top-20 list still reflects something meaningful.
 */
internal fun parsePrometheus(text: String): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        // Split on the first run of whitespace.
        val ws = line.indexOfFirst { it == ' ' || it == '\t' }
        if (ws <= 0) continue
        val name = line.substring(0, ws)
        val rest = line.substring(ws + 1).trim()
        if (rest.isEmpty()) continue
        // Value is the first token of `rest`; the optional timestamp follows.
        val valEnd = rest.indexOfFirst { it == ' ' || it == '\t' }
        val valStr = if (valEnd < 0) rest else rest.substring(0, valEnd)
        val value = valStr.toDoubleOrNull() ?: continue
        val prev = out[name]
        if (prev == null || abs(value) > abs(prev)) out[name] = value
    }
    return out
}

/** Drop `{...}` label block from `metric_name{plan="X"}` → `metric_name`. */
internal fun stripLabels(name: String): String {
    val brace = name.indexOf('{')
    return if (brace < 0) name else name.substring(0, brace)
}

/** Coerce a JsonElement health-field value into a short display string. */
private fun jsonElementToDisplay(el: JsonElement): String = when (el) {
    is JsonPrimitive -> el.contentOrNull ?: el.toString()
    is JsonObject -> el.toString()
    else -> el.toString()
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = AegisColors.textBody, fontFamily = FontFamily.Monospace)
    }
}

/**
 * One small tile inside the "OTP activity" card. Shows the integer counter
 * value (or "—" when the metric is missing from the parsed map) plus the
 * metric name in small mono text so operators can correlate with `/metrics`.
 */
@Composable
private fun OtpTile(
    label: String,
    metricName: String,
    metricsMap: Map<String, Double>,
    modifier: Modifier = Modifier,
) {
    val raw = metricsMap[metricName]
    val display = if (raw == null) "—" else {
        val l = raw.toLong()
        if (l.toDouble() == raw) l.toString() else raw.toString()
    }
    Column(
        modifier
            .background(AegisColors.surfaceMuted, RoundedCornerShape(AegisRadii.rMd))
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 11.sp, color = AegisColors.textSecondary,
            fontWeight = FontWeight.Medium)
        Text(
            display,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textPrimary,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            metricName,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = AegisColors.textTertiary,
        )
    }
}

@Composable
private fun MetricRow(name: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = AegisColors.textBody,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMetric(value),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textPrimary,
        )
    }
}

/**
 * Compact metric formatter — no `String.format` so we stay JVM-free.
 * Whole-number doubles render integer-style (`142`); fractional values render
 * via the JVM/JS default `toString` (matches Prometheus scrape output).
 */
internal fun formatMetric(v: Double): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return if (v > 0) "+Inf" else "-Inf"
    val l = v.toLong()
    return if (l.toDouble() == v) l.toString() else v.toString()
}
