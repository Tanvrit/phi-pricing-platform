package com.rate.server.plugins

import com.rate.server.metrics.Metrics
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory

/**
 * Single-line structured request log for every HTTP exchange plus metric updates.
 *
 *   `method=GET path=/api/quotes/Q-... status=200 durationMs=12 requestId=req-xxx actor=unknown`
 *
 * The output is intentionally keyed (k=v) so logback/Elastic can parse it without a
 * regex. Request paths are also normalised before being recorded as a metric label,
 * so high-cardinality ids don't blow up the TSDB.
 *
 * Install order matters: this runs *after* HTTP's request-id interceptor so
 * `REQUEST_ID_KEY` is already populated.
 */
fun Application.configureRequestLog() {
    val log = LoggerFactory.getLogger("com.rate.server.access")

    intercept(ApplicationCallPipeline.Monitoring) {
        val startNs = System.nanoTime()
        try {
            proceed()
        } finally {
            val durationNs = System.nanoTime() - startNs
            val durationMs = durationNs / 1_000_000
            val durationSec = durationNs / 1_000_000_000.0
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod.value
            val path   = call.request.path()
            val rid    = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor  = call.attributes.getOrNull(ACTOR_SUBJECT_KEY) ?: "unknown"

            // Skip noisy probes from access logs (still counted in metrics for SLO).
            val isProbe = path == "/health" || path == "/health/live" ||
                path == "/health/ready" || path == "/metrics"
            if (!isProbe) {
                log.info(
                    "method={} path={} status={} durationMs={} requestId={} actor={}",
                    method, path, status, durationMs, rid ?: "-", actor
                )
            }
            Metrics.recordHttp(method, path, status, durationSec)
        }
    }
}
