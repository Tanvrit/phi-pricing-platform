package com.rate.server.metrics

import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Tiny, dependency-free Prometheus-compatible metrics accumulator. Mirrors the
 * subset of the Prometheus client model we actually need (counter + histogram +
 * gauge) without dragging in a real client library. If the surface area outgrows
 * a few hundred LOC, we'll swap this for Micrometer.
 *
 * Concurrency model:
 *   - Counters use `LongAdder` per label combination (lock-free, contention-tolerant).
 *   - Histograms use bucketed counters + sum atomic. Cumulative buckets are emitted
 *     in Prometheus's `_bucket{le=...}` form.
 *   - Gauges read live values via a registered supplier each `render()`.
 *
 * Cardinality safety: paths are normalised via [Metrics.normalisePath] before being
 * recorded, so `/api/quotes/Q-abc123` becomes `/api/quotes/{id}` — keeps label
 * cardinality bounded (a quote-id explosion would kill any TSDB).
 */
object Metrics {

    // ── Counters ──────────────────────────────────────────────────────────────
    private val httpRequestsTotal     = ConcurrentHashMap<List<String>, LongAdder>()
    private val quoteCalculationTotal = ConcurrentHashMap<List<String>, LongAdder>()
    private val otpSentTotal          = ConcurrentHashMap<List<String>, LongAdder>()
    private val otpVerifiedTotal      = ConcurrentHashMap<List<String>, LongAdder>()

    // ── Aegis-surface no-label OTP counters ───────────────────────────────────
    // These mirror the labelled counters above as flat totals so the Aegis
    // Server Health surface can pin them prominently without parsing label sets.
    // OtpService increments these directly; routes keep using the labelled ones.
    private val otpSentCounter           = AtomicLong(0)
    private val otpVerifySuccessCounter  = AtomicLong(0)
    private val otpVerifyFailureCounter  = AtomicLong(0)
    private val otpRateLimitedCounter    = AtomicLong(0)

    // ── Aegis-surface no-label idempotency counters ───────────────────────────
    // Mirror the OTP pattern above — flat totals the Aegis Server Health surface
    // can pin without parsing label sets. Incremented from the Idempotency
    // plugin wrapper (the request-handling boundary where each outcome maps
    // 1:1 to "new request handled" / "cached response replayed" / "409 sent").
    private val idemNewCounter      = AtomicLong(0)
    private val idemReplayCounter   = AtomicLong(0)
    private val idemConflictCounter = AtomicLong(0)

    // ── Histogram (rate_http_request_duration_seconds) ────────────────────────
    private val httpDurationBuckets = ConcurrentHashMap<List<String>, HistogramState>()
    /** Buckets (in seconds) chosen for typical HTTP latency distributions. */
    private val BUCKETS = doubleArrayOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)

    // ── Gauges (read-on-demand) ───────────────────────────────────────────────
    @Volatile private var hikari: HikariDataSource? = null

    fun bindHikari(ds: HikariDataSource) { hikari = ds }

    fun recordHttp(method: String, path: String, status: Int, durationSeconds: Double) {
        val normPath = normalisePath(path)
        httpRequestsTotal
            .computeIfAbsent(listOf(method, normPath, status.toString())) { LongAdder() }
            .increment()
        val h = httpDurationBuckets.computeIfAbsent(listOf(method, normPath)) {
            HistogramState(BUCKETS)
        }
        h.observe(durationSeconds)
    }

    fun recordQuoteCalculation(planId: String, isValid: Boolean) {
        quoteCalculationTotal
            .computeIfAbsent(listOf(planId, isValid.toString())) { LongAdder() }
            .increment()
    }

    fun recordOtpSent(purpose: String) {
        otpSentTotal
            .computeIfAbsent(listOf(purpose)) { LongAdder() }
            .increment()
    }

    fun recordOtpVerified(purpose: String, outcome: String) {
        otpVerifiedTotal
            .computeIfAbsent(listOf(purpose, outcome)) { LongAdder() }
            .increment()
    }

    fun recordOtpSent() { otpSentCounter.incrementAndGet() }
    fun recordOtpVerifySuccess() { otpVerifySuccessCounter.incrementAndGet() }
    fun recordOtpVerifyFailure() { otpVerifyFailureCounter.incrementAndGet() }
    fun recordOtpRateLimited() { otpRateLimitedCounter.incrementAndGet() }

    fun recordIdempotentNew()      { idemNewCounter.incrementAndGet() }
    fun recordIdempotentReplay()   { idemReplayCounter.incrementAndGet() }
    fun recordIdempotentConflict() { idemConflictCounter.incrementAndGet() }

    /**
     * Clears all accumulators. Intended for unit tests only — production code never
     * resets metrics (Prometheus expects monotonic counters).
     */
    fun resetForTest() {
        httpRequestsTotal.clear()
        quoteCalculationTotal.clear()
        otpSentTotal.clear()
        otpVerifiedTotal.clear()
        httpDurationBuckets.clear()
        otpSentCounter.set(0)
        otpVerifySuccessCounter.set(0)
        otpVerifyFailureCounter.set(0)
        otpRateLimitedCounter.set(0)
        idemNewCounter.set(0)
        idemReplayCounter.set(0)
        idemConflictCounter.set(0)
    }

    /**
     * Collapses high-cardinality path segments (ids, ULIDs, numbers) so the Prom
     * label-set stays bounded. e.g. `/api/quotes/Q-01H...` -> `/api/quotes/{id}`.
     */
    fun normalisePath(path: String): String {
        if (path.isEmpty() || path == "/") return path
        val parts = path.split('/')
        val out = StringBuilder()
        for (p in parts) {
            if (out.isNotEmpty() || path.startsWith('/')) out.append('/')
            if (p.isEmpty()) continue
            out.append(if (looksLikeId(p)) "{id}" else p)
        }
        // strip duplicate leading slash
        val s = out.toString()
        return if (s.startsWith("//")) s.substring(1) else s
    }

    private fun looksLikeId(seg: String): Boolean {
        if (seg.isEmpty()) return false
        // pure-numeric, ULID-ish, UUID-ish, or our `Q-...` / `PHI-...` prefixes
        if (seg.all { it.isDigit() }) return true
        if (seg.length >= 12 && seg.all { it.isLetterOrDigit() || it == '-' }) {
            val digits = seg.count { it.isDigit() }
            if (digits >= 4) return true
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

        // Aegis-surface no-label OTP counters
        sb.append("# HELP otp_sent_total Total OTP sends issued\n")
        sb.append("# TYPE otp_sent_total counter\n")
        sb.append("otp_sent_total ").append(otpSentCounter.get()).append('\n')

        sb.append("# HELP otp_verify_success_total Successful OTP verifications\n")
        sb.append("# TYPE otp_verify_success_total counter\n")
        sb.append("otp_verify_success_total ").append(otpVerifySuccessCounter.get()).append('\n')

        sb.append("# HELP otp_verify_failure_total Failed OTP verifications (wrong code or expired)\n")
        sb.append("# TYPE otp_verify_failure_total counter\n")
        sb.append("otp_verify_failure_total ").append(otpVerifyFailureCounter.get()).append('\n')

        sb.append("# HELP otp_rate_limited_total OTP requests rejected by per-mobile rate limit\n")
        sb.append("# TYPE otp_rate_limited_total counter\n")
        sb.append("otp_rate_limited_total ").append(otpRateLimitedCounter.get()).append('\n')

        sb.append("# HELP idempotent_new_total New requests handled (cache miss)\n")
        sb.append("# TYPE idempotent_new_total counter\n")
        sb.append("idempotent_new_total ").append(idemNewCounter.get()).append('\n')

        sb.append("# HELP idempotent_replay_total Cache hits — handler skipped, cached response returned\n")
        sb.append("# TYPE idempotent_replay_total counter\n")
        sb.append("idempotent_replay_total ").append(idemReplayCounter.get()).append('\n')

        sb.append("# HELP idempotent_conflict_total Idempotency-Key reused with a different request body\n")
        sb.append("# TYPE idempotent_conflict_total counter\n")
        sb.append("idempotent_conflict_total ").append(idemConflictCounter.get()).append('\n')

        // HikariCP gauges
        hikari?.hikariPoolMXBean?.let { pool ->
            sb.append("# HELP rate_db_connections_active Active DB connections (HikariCP).\n")
            sb.append("# TYPE rate_db_connections_active gauge\n")
            sb.append("rate_db_connections_active ").append(pool.activeConnections).append('\n')
            sb.append("# HELP rate_db_connections_idle Idle DB connections (HikariCP).\n")
            sb.append("# TYPE rate_db_connections_idle gauge\n")
            sb.append("rate_db_connections_idle ").append(pool.idleConnections).append('\n')
            sb.append("# HELP rate_db_connections_total Total DB connections in pool.\n")
            sb.append("# TYPE rate_db_connections_total gauge\n")
            sb.append("rate_db_connections_total ").append(pool.totalConnections).append('\n')
            sb.append("# HELP rate_db_threads_awaiting_connection Threads waiting for a DB conn.\n")
            sb.append("# TYPE rate_db_threads_awaiting_connection gauge\n")
            sb.append("rate_db_threads_awaiting_connection ").append(pool.threadsAwaitingConnection).append('\n')
        }
        return sb.toString()
    }

    private fun renderCounter(
        sb: StringBuilder,
        name: String,
        help: String,
        map: ConcurrentHashMap<List<String>, LongAdder>,
        labelNames: List<String>
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
        labelNames: List<String>
    ) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
        sb.append("# TYPE ").append(name).append(" histogram\n")
        for ((labels, hist) in map) {
            val baseLabels = labelNames.mapIndexed { i, n ->
                "$n=\"${escapeLabel(labels[i])}\""
            }.joinToString(",")
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
            // CAS-add for doubles
            while (true) {
                val cur = sumBits.get()
                val next = java.lang.Double.doubleToLongBits(
                    java.lang.Double.longBitsToDouble(cur) + v
                )
                if (sumBits.compareAndSet(cur, next)) break
            }
        }

        fun cumulative(): LongArray = LongArray(buckets.size) { buckets[it].sum() }
    }
}
