package com.rate.server.plugins

import com.rate.server.logging.PiiMaskingConverter
import com.rate.server.metrics.Metrics
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory

/**
 * Single-line structured request log for every HTTP exchange plus metric updates:
 *
 *   `method=GET path=/api/quotes/Q-... status=200 durationMs=12 requestId=req-xxx actor=...`
 *
 * RELOCATED from the monolith. PII protection is defence-in-depth: the on-disk/console pattern
 * runs through [PiiMaskingConverter] (via the `%piimsg` logback rule), AND this logger additionally
 * masks the path itself before emitting so an accidental PII path segment (e.g. a mobile-number
 * route param) never lands in the access log even if the appender pattern is misconfigured.
 *
 * Install order matters: runs AFTER HTTP's request-id interceptor so [REQUEST_ID_KEY] is populated.
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
            val path = call.request.path()
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY) ?: "-"

            val isProbe = path == "/health" || path == "/health/live" ||
                path == "/health/ready" || path == "/metrics"
            if (!isProbe) {
                log.info(
                    "method={} path={} status={} durationMs={} requestId={} actor={}",
                    method, PiiMaskingConverter.mask(path), status, durationMs, rid ?: "-",
                    PiiMaskingConverter.mask(actor),
                )
            }
            Metrics.recordHttp(method, path, status, durationSec)
        }
    }
}
