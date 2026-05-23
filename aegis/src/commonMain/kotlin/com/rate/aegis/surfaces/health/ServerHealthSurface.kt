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
import com.rate.aegis.AEGIS_VERSION
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.business.calculator.api.DbPoolStats
import com.rate.aegis.business.calculator.api.ServerConfigInfo
import com.rate.aegis.components.*
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

    // Server config snapshot — fetched once on first composition. Single-shot
    // by design (the prompt forbids a per-card refresh button); the global
    // Refresh button at the shell level re-keys all `LaunchedEffect`s via
    // `LocalRefreshTicker`, which is enough to pick up changes (e.g. operator
    // restarted the server with a new port/CORS list).
    var configInfo by remember { mutableStateOf<ServerConfigInfo?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }
    var configLoaded by remember { mutableStateOf(false) }

    // DB pool snapshot — polled every 5s like /health. Kept on a separate
    // poller (not folded into the config-card single-shot) because pool
    // counters move continuously and the operator wants live feedback when
    // chasing a connection-pool exhaustion ticket.
    var dbPoolStats by remember { mutableStateOf<DbPoolStats?>(null) }
    var dbPoolError by remember { mutableStateOf<String?>(null) }

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

    // ── /api/admin/config (single fetch per refresh) ─────────────────────
    // Keyed on `refreshTick` so the shell-level Refresh button picks up any
    // operator-side env changes without us wiring a per-card button.
    LaunchedEffect(client, refreshTick) {
        configLoaded = false
        runCatching { client.getServerConfig() }
            .onSuccess {
                configInfo = it
                configError = null
            }
            .onFailure { t ->
                configError = t.message ?: t::class.simpleName ?: "unknown error"
            }
        configLoaded = true
    }

    // ── /api/admin/db-pool poller (5s) ───────────────────────────────────
    // Independent of /health and /metrics — same 5s cadence as /health so
    // the surface stays cheap (one extra round-trip per cycle) while still
    // giving operators near-real-time visibility into connection-pool
    // exhaustion.
    LaunchedEffect(client, refreshTick) {
        while (coroutineContext.isActive) {
            runCatching { client.getDbPoolStats() }
                .onSuccess {
                    dbPoolStats = it
                    dbPoolError = null
                }
                .onFailure { t ->
                    dbPoolError = t.message ?: t::class.simpleName ?: "unknown error"
                }
            delay(5_000L)
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

        // ── 1. Build mismatch callout (top-of-surface) ──────────────────
        // If the operator's local Aegis build doesn't match the server's
        // reported version, surface this loudly above everything else —
        // mismatched builds are a frequent root cause of "endpoint behaves
        // differently than the code says it should" tickets.
        val cfg = configInfo
        if (cfg != null && cfg.version != AEGIS_VERSION) {
            AegisCallout(
                kind = CalloutKind.WARN,
                title = "Build mismatch",
                body = "Aegis $AEGIS_VERSION ↔ Server ${cfg.version}. " +
                        "Restart the older component to align."
            )
        }

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

        // ── 3d. Server config card ──────────────────────────────────────
        // Read-only readout of `/api/admin/config`. The endpoint is gated by
        // `audit.verify`; a 403 routes to a WARN callout instead of a red
        // alarm (operator just hasn't been granted the scope yet). Secrets
        // (DB password, OTP token secret) are intentionally NOT in the wire
        // payload — see `ServerConfigInfo` defence-in-depth note.
        ServerConfigCard(
            info = configInfo,
            error = configError,
            loaded = configLoaded,
        )

        // ── 3e. DB connection pool card ─────────────────────────────────
        // Live HikariCP counters polled every 5s. The "Waiting" tile turns
        // red when non-zero — that's the canonical signal that the pool is
        // exhausted (threads blocked in `getConnection()`), almost always a
        // queryplan / slow-query problem rather than something to "fix" by
        // raising the pool size.
        DbPoolCard(stats = dbPoolStats, error = dbPoolError)

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
 * Format a server-uptime [kotlin.time.Duration] into a short human label.
 *
 * Buckets:
 *   - days  > 0 → "2d 14h 22m"   (long-running production server)
 *   - hours > 0 → "3h 07m"       (a fresh restart or QA box)
 *   - mins  > 0 → "12m 30s"      (just after a crash-loop bounce)
 *   - else      → "42s"          (literally just came up)
 *
 * No locale formatting, no `String.format` — both keep the helper safe on
 * JVM, JS, and WASM. Negative durations (clock skew between operator laptop
 * and server) are clamped to 0 so the UI never shows "-1d".
 */
internal fun formatUptime(d: kotlin.time.Duration): String {
    val totalSeconds = d.inWholeSeconds.coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
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

/**
 * "Server config" diagnostic card — read-only snapshot of `/api/admin/config`.
 *
 * Three rendering states:
 *   - `!loaded`            → "Loading server config…" hint
 *   - `error != null`      → WARN callout (insufficient-scope 403) or DANGER
 *                            callout (network / 5xx). Substring-detect "403"
 *                            mirrors the contract used by [IdempotencyCacheCard]
 *                            and [ChainIntegrityCard] in AuditEventsSurface so
 *                            unbootstrapped operators get actionable guidance
 *                            instead of a red alarm.
 *   - `info != null`       → 7 inline key-value rows.
 *
 * CORS list is comma-joined and soft-truncated at ~80 chars so a long allow-
 * list doesn't blow out the row height; the full list still goes over the wire
 * and is available via the raw `/api/admin/config` curl.
 */
@Composable
private fun ServerConfigCard(
    info: ServerConfigInfo?,
    error: String?,
    loaded: Boolean,
) {
    AegisCard(
        title = "Server config",
        subtitle = "Effective runtime settings — read-only snapshot. " +
                "Fetched once on surface open; the global Refresh button re-fetches.",
    ) {
        when {
            !loaded -> Text(
                "Loading server config…",
                fontSize = 13.sp,
                color = AegisColors.textSecondary,
            )
            error != null -> {
                val is403 = error.contains("403")
                AegisCallout(
                    kind = if (is403) CalloutKind.WARN else CalloutKind.DANGER,
                    title = if (is403) "Insufficient scope (403)"
                            else "Server config unavailable",
                    body = if (is403)
                        "The current operator identity does not have the `audit.verify` scope " +
                                "(reused for this diagnostic). Set your identity in Settings or ask " +
                                "an admin to grant the scope. Detail: $error"
                    else
                        "Could not fetch /api/admin/config: $error",
                )
            }
            info != null -> {
                val corsDisplay = run {
                    val joined = if (info.corsOrigins.isEmpty()) "(none)"
                                 else info.corsOrigins.joinToString(", ")
                    if (joined.length <= 80) joined else joined.take(77) + "…"
                }
                // ── Uptime tick ────────────────────────────────────────
                // The server config is fetched once per global refresh, but
                // we still want the uptime row to advance every minute. A
                // local 60s ticker just bumps a counter that the `remember`
                // key reads — we never re-hit /api/admin/config.
                var tick by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (isActive) {
                        delay(60_000L)
                        tick++
                    }
                }
                val uptimeText = remember(info.startedAtIso, tick) {
                    val started = runCatching { Instant.parse(info.startedAtIso) }
                        .getOrNull()
                    if (started == null) "—"
                    else formatUptime(Clock.System.now() - started)
                }
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    val rows = listOf(
                        "Version" to info.version,
                        "Port" to info.port.toString(),
                        "DB host" to info.dbHost,
                        "CORS origins" to corsDisplay,
                        "OTP TTL" to "${info.otpTtlSec}s",
                        "Idempotency TTL" to "${info.idempotencyTtlHours}h",
                        "Started at" to info.startedAtIso,
                        "Uptime" to uptimeText,
                    )
                    rows.forEachIndexed { idx, (label, value) ->
                        ServerConfigRow(label, value)
                        if (idx < rows.size - 1) AegisHDivider()
                    }
                }
            }
            else -> Text(
                "(no config payload returned)",
                fontSize = 13.sp,
                color = AegisColors.textSecondary,
            )
        }
    }
}

/**
 * Inline key-value row inside [ServerConfigCard]. Same visual shape as
 * [KeyValueRow] above but kept private here so the prompt's "DO NOT touch
 * other surfaces" constraint stays intact — no risk of accidentally drifting
 * the shared one.
 */
@Composable
private fun ServerConfigRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * "DB connection pool" diagnostic card — five tiles wired to `/api/admin/db-pool`.
 *
 * Tile layout (left → right): Active, Idle, Total, Waiting, Max. "Waiting"
 * is colour-coded red when non-zero to flag connection-pool exhaustion at a
 * glance — that's the row operators care about most when a slow-query incident
 * is in progress.
 *
 * Same scope-gating contract as [ServerConfigCard]: a 403 routes to a WARN
 * callout (operator just hasn't been granted `audit.verify`); a network / 5xx
 * routes to DANGER.
 */
@Composable
private fun DbPoolCard(stats: DbPoolStats?, error: String?) {
    AegisCard(
        title = "DB connection pool",
        subtitle = "Live HikariCP counters. Auto-refreshes every 5s. " +
                "Waiting > 0 means threads are blocked in getConnection() — pool exhausted.",
    ) {
        when {
            error != null -> {
                val is403 = error.contains("403")
                AegisCallout(
                    kind = if (is403) CalloutKind.WARN else CalloutKind.DANGER,
                    title = if (is403) "Insufficient scope (403)"
                            else "DB pool stats unavailable",
                    body = if (is403)
                        "The current operator identity does not have the `audit.verify` scope " +
                                "(reused for this diagnostic). Set your identity in Settings or ask " +
                                "an admin to grant the scope. Detail: $error"
                    else
                        "Could not fetch /api/admin/db-pool: $error",
                )
            }
            stats == null -> Text(
                "Waiting for first /api/admin/db-pool response…",
                fontSize = 13.sp,
                color = AegisColors.textSecondary,
            )
            else -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                ) {
                    DbPoolTile("Active", stats.active.toString(), Modifier.weight(1f))
                    DbPoolTile("Idle", stats.idle.toString(), Modifier.weight(1f))
                    DbPoolTile("Total", stats.total.toString(), Modifier.weight(1f))
                    DbPoolTile(
                        label = "Waiting",
                        value = stats.threadsAwaiting.toString(),
                        modifier = Modifier.weight(1f),
                        // Red when non-zero: this is the canonical "pool
                        // exhausted" signal. We light both the value and the
                        // label so the tile reads as alarming even at a
                        // glance, not just on close inspection.
                        valueColor = if (stats.threadsAwaiting > 0)
                            AegisColors.danger700 else AegisColors.textPrimary,
                        labelColor = if (stats.threadsAwaiting > 0)
                            AegisColors.danger500 else AegisColors.textSecondary,
                    )
                    DbPoolTile("Max", stats.maxPoolSize.toString(), Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One stat tile inside [DbPoolCard]. Same visual shape as [OtpTile] above but
 * kept private and accepts override colours so the "Waiting" tile can light
 * up red when the pool is exhausted. No metric-name footer — the labels here
 * (Active / Idle / Total / Waiting / Max) are self-explanatory and the extra
 * mono-text line would just add visual noise.
 */
@Composable
private fun DbPoolTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = AegisColors.textPrimary,
    labelColor: androidx.compose.ui.graphics.Color = AegisColors.textSecondary,
) {
    Column(
        modifier
            .background(AegisColors.surfaceMuted, RoundedCornerShape(AegisRadii.rMd))
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
        )
    }
}
