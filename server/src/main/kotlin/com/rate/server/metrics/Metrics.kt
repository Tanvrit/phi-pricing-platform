package com.rate.server.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Tiny, dependency-free Prometheus-compatible metrics accumulator. Mirrors the subset of the
 * Prometheus client model we actually need (counter + histogram + gauge) without dragging in a
 * real client library.
 *
 * RELOCATED verbatim from the monolith's `Metrics`, MINUS the HikariCP gauge binding (the
 * re-arch is Mongo-only — there is no JDBC pool). Gauges are now registered as named
 * `() -> Number` suppliers via [registerGauge], so the persistence layer can publish Mongo
 * connection / db stats without this module depending on the driver.
 *
 * Concurrency model:
 *   - Counters use `LongAdder` per label combination (lock-free).
 *   - Histograms use bucketed counters + a CAS double sum.
 *   - Gauges read live values via a registered supplier each [render].
 *
 * Cardinality safety: paths are normalised via [normalisePath] before being recorded.
 */
object Metrics {

    // ── Counters ──────────────────────────────────────────────────────────────
    private val httpRequestsTotal     = ConcurrentHashMap<List<String>, LongAdder>()
    private val quoteCalculationTotal = ConcurrentHashMap<List<String>, LongAdder>()
    private val otpSentTotal          = ConcurrentHashMap<List<String>, LongAdder>()
    private val otpVerifiedTotal      = ConcurrentHashMap<List<String>, LongAdder>()

    // ── Flat (no-label) OTP counters the operator Server-Health surface pins ────
    private val otpSentCounter           = AtomicLong(0)
    private val otpVerifySuccessCounter  = AtomicLong(0)
    private val otpVerifyFailureCounter  = AtomicLong(0)
    private val otpRateLimitedCounter    = AtomicLong(0)

    // ── Flat idempotency counters ───────────────────────────────────────────────
    private val idemNewCounter      = AtomicLong(0)
    private val idemReplayCounter   = AtomicLong(0)
    private val idemConflictCounter = AtomicLong(0)

    // ── Histogram (rate_http_request_duration_seconds) ────────────────────────
    private val httpDurationBuckets = ConcurrentHashMap<List<String>, HistogramState>()
    private val BUCKETS = doubleArrayOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)

    // ── Gauges (read-on-demand suppliers) ───────────────────────────────────────
    // Replaces the monolith's hardwired Hikari binding. Persistence (or any module) can
    // publish a live numeric gauge — e.g. Mongo pool size, active rate-table version count —
    // without this module depending on the Mongo driver.
    private data class GaugeDef(val help: String, val supplier: () -> Number)
    private val gauges = ConcurrentHashMap<String, GaugeDef>()

    /** Register (or replace) a named gauge whose value is read each [render]. */
    fun registerGauge(name: String, help: String, supplier: () -> Number) {
        gauges[name] = GaugeDef(help, supplier)
    }

    fun recordHttp(method: String, path: String, status: Int, durationSeconds: Double) {
        val normPath = normalisePath(path)
        httpRequestsTotal
            .computeIfAbsent(listOf(method, normPath, status.toString())) { LongAdder() }
            .increment()
        httpDurationBuckets.computeIfAbsent(listOf(method, normPath)) { HistogramState(BUCKETS) }
            .observe(durationSeconds)
    }

    fun recordQuoteCalculation(planId: String, isValid: Boolean) {
        quoteCalculationTotal
            .computeIfAbsent(listOf(planId, isValid.toString())) { LongAdder() }
            .increment()
    }

    fun recordOtpSent(purpose: String) {
        otpSentTotal.computeIfAbsent(listOf(purpose)) { LongAdder() }.increment()
        otpSentCounter.incrementAndGet()
    }

    fun recordOtpVerified(purpose: String, outcome: String) {
        otpVerifiedTotal.computeIfAbsent(listOf(purpose, outcome)) { LongAdder() }.increment()
        when (outcome) {
            "ok" -> otpVerifySuccessCounter.incrementAndGet()
            else -> otpVerifyFailureCounter.incrementAndGet()
        }
    }

    fun recordOtpRateLimited() { otpRateLimitedCounter.incrementAndGet() }

    fun recordIdempotentNew()      { idemNewCounter.incrementAndGet() }
    fun recordIdempotentReplay()   { idemReplayCounter.incrementAndGet() }
    fun recordIdempotentConflict() { idemConflictCounter.incrementAndGet() }

    /** Clears all accumulators. Tests only — production counters stay monotonic. */
    fun resetForTest() {
        httpRequestsTotal.clear()
        quoteCalculationTotal.clear()
        otpSentTotal.clear()
        otpVerifiedTotal.clear()
        httpDurationBuckets.clear()
        gauges.clear()
        otpSentCounter.set(0)
        otpVerifySuccessCounter.set(0)
        otpVerifyFailureCounter.set(0)
        otpRateLimitedCounter.set(0)
        idemNewCounter.set(0)
        idemReplayCounter.set(0)
        idemConflictCounter.set(0)
    }

    /** Collapses high-cardinality path segments so the Prom label-set stays bounded. */
    fun normalisePath(path: String): String {
        if (path.isEmpty() || path == "/") return path
        val parts = path.split('/')
        val out = StringBuilder()
        for (p in parts) {
            if (out.isNotEmpty() || path.startsWith('/')) out.append('/')
            if (p.isEmpty()) continue
            out.append(if (looksLikeId(p)) "{id}" else p)
        }
        val s = out.toString()
        return if (s.startsWith("//")) s.substring(1) else s
    }

    private fun looksLikeId(seg: String): Boolean {
        if (seg.isEmpty()) return false
        if (seg.all { it.isDigit() }) return true
        if (seg.length >= 12 && seg.all { it.isLetterOrDigit() || it == '-' }) {
            if (seg.count { it.isDigit() } >= 4) return true
        }
        if (seg.startsWith("Q-") || seg.startsWith("PHI-") || seg.startsWith("req-")) return true
        return false
    }

    // ── Prometheus text exposition format ─────────────────────────────────────
    fun render(): String {
        val sb = StringBuilder()
        renderCounter(sb, "rate_http_requests_total",
            "Total HTTP requests by method, path, status.",
            httpRequestsTotal, listOf("method", "path", "status"))
        renderHistogram(sb, "rate_http_request_duration_seconds",
            "HTTP request duration in seconds.",
            httpDurationBuckets, listOf("method", "path"))
        renderCounter(sb, "rate_quote_calculations_total",
            "Total quote calculations executed.",
            quoteCalculationTotal, listOf("plan_id", "is_valid"))
        renderCounter(sb, "rate_otp_sent_total",
            "Total OTPs issued.",
            otpSentTotal, listOf("purpose"))
        renderCounter(sb, "rate_otp_verified_total",
            "Total OTP verification attempts by outcome.",
            otpVerifiedTotal, listOf("purpose", "outcome"))

        flatCounter(sb, "otp_sent_total", "Total OTP sends issued", otpSentCounter)
        flatCounter(sb, "otp_verify_success_total", "Successful OTP verifications", otpVerifySuccessCounter)
        flatCounter(sb, "otp_verify_failure_total", "Failed OTP verifications (wrong code or expired)", otpVerifyFailureCounter)
        flatCounter(sb, "otp_rate_limited_total", "OTP requests rejected by per-mobile rate limit", otpRateLimitedCounter)
        flatCounter(sb, "idempotent_new_total", "New requests handled (cache miss)", idemNewCounter)
        flatCounter(sb, "idempotent_replay_total", "Cache hits — handler skipped, cached response returned", idemReplayCounter)
        flatCounter(sb, "idempotent_conflict_total", "Idempotency-Key reused with a different request body", idemConflictCounter)

        // Registered gauges (e.g. Mongo pool stats published by server-persistence).
        for ((name, def) in gauges) {
            val value = runCatching { def.supplier() }.getOrNull() ?: continue
            sb.append("# HELP ").append(name).append(' ').append(def.help).append('\n')
            sb.append("# TYPE ").append(name).append(" gauge\n")
            sb.append(name).append(' ').append(value).append('\n')
        }
        return sb.toString()
    }

    private fun flatCounter(sb: StringBuilder, name: String, help: String, c: AtomicLong) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
        sb.append("# TYPE ").append(name).append(" counter\n")
        sb.append(name).append(' ').append(c.get()).append('\n')
    }

    private fun renderCounter(
        sb: StringBuilder,
        name: String,
        help: String,
        map: ConcurrentHashMap<List<String>, LongAdder>,
        labelNames: List<String>,
    ) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
        sb.append("# TYPE ").append(name).append(" counter\n")
        for ((labels, adder) in map) {
            sb.append(name).append('{')
            labelNames.forEachIndexed { i, n ->
                if (i > 0) sb.append(',')
                sb.append(n).append("=\"").append(escapeLabel(labels[i])).append('"')
            }
            sb.append("} ").append(adder.sum()).append('\n')
        }
    }

    private fun renderHistogram(
        sb: StringBuilder,
        name: String,
        help: String,
        map: ConcurrentHashMap<List<String>, HistogramState>,
        labelNames: List<String>,
    ) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
        sb.append("# TYPE ").append(name).append(" histogram\n")
        for ((labels, hist) in map) {
            val baseLabels = labelNames.mapIndexed { i, n -> "$n=\"${escapeLabel(labels[i])}\"" }
                .joinToString(",")
            val cum = hist.cumulative()
            BUCKETS.forEachIndexed { i, bound ->
                sb.append(name).append("_bucket{").append(baseLabels)
                    .append(",le=\"").append(bound).append("\"} ")
                    .append(cum[i]).append('\n')
            }
            sb.append(name).append("_bucket{").append(baseLabels)
                .append(",le=\"+Inf\"} ").append(hist.count.sum()).append('\n')
            sb.append(name).append("_sum{").append(baseLabels).append("} ")
                .append(java.lang.Double.longBitsToDouble(hist.sumBits.get())).append('\n')
            sb.append(name).append("_count{").append(baseLabels).append("} ")
                .append(hist.count.sum()).append('\n')
        }
    }

    private fun escapeLabel(v: String): String =
        v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private class HistogramState(private val bounds: DoubleArray) {
        val buckets = Array(bounds.size) { LongAdder() }
        val count = LongAdder()
        val sumBits = AtomicLong(java.lang.Double.doubleToLongBits(0.0))

        fun observe(v: Double) {
            count.increment()
            for (i in bounds.indices) {
                if (v <= bounds[i]) buckets[i].increment()
            }
            while (true) {
                val cur = sumBits.get()
                val next = java.lang.Double.doubleToLongBits(java.lang.Double.longBitsToDouble(cur) + v)
                if (sumBits.compareAndSet(cur, next)) break
            }
        }

        fun cumulative(): LongArray = LongArray(buckets.size) { buckets[it].sum() }
    }
}
