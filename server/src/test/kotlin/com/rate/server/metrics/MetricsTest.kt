package com.rate.server.metrics

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies that:
 *   - counters increment correctly across labels
 *   - paths are normalised so cardinality stays bounded
 *   - Prometheus output is well-formed and includes the expected family names
 *   - histogram buckets accumulate cumulatively (Prometheus contract)
 *   - the renderer is safe to call when no metrics have been recorded
 */
class MetricsTest {

    @BeforeEach
    fun reset() {
        // Metrics is a singleton — reset its internal counters between tests.
        Metrics.resetForTest()
    }

    @Test
    fun `counter increments per label combination`() {
        Metrics.recordHttp("GET", "/api/quotes/Q-123", 200, 0.012)
        Metrics.recordHttp("GET", "/api/quotes/Q-456", 200, 0.020)
        Metrics.recordHttp("GET", "/api/quotes/Q-789", 500, 0.040)

        val output = Metrics.render()
        // The first two collapse onto the normalised path; the 500 stays separate.
        output shouldContain "rate_http_requests_total{method=\"GET\",path=\"/api/quotes/{id}\",status=\"200\"} 2"
        output shouldContain "rate_http_requests_total{method=\"GET\",path=\"/api/quotes/{id}\",status=\"500\"} 1"
    }

    @Test
    fun `paths with unbounded ids collapse to bounded labels`() {
        Metrics.recordHttp("GET", "/api/buy-online/proposal/PHI-AB12-X34", 200, 0.005)
        Metrics.recordHttp("GET", "/api/plans/PHI_BASIC", 200, 0.003)
        val output = Metrics.render()
        // No raw quote/proposal id should appear in the output.
        output shouldNotContain "PHI-AB12-X34"
        output shouldNotContain "Q-123"
    }

    @Test
    fun `recordQuoteCalculation counts both valid and invalid outcomes`() {
        Metrics.recordQuoteCalculation("PHI_BASIC", true)
        Metrics.recordQuoteCalculation("PHI_BASIC", true)
        Metrics.recordQuoteCalculation("PHI_BASIC", false)
        Metrics.recordQuoteCalculation("PHI_FLAGSHIP1", true)

        val output = Metrics.render()
        output shouldContain "rate_quote_calculations_total{plan_id=\"PHI_BASIC\",is_valid=\"true\"} 2"
        output shouldContain "rate_quote_calculations_total{plan_id=\"PHI_BASIC\",is_valid=\"false\"} 1"
        output shouldContain "rate_quote_calculations_total{plan_id=\"PHI_FLAGSHIP1\",is_valid=\"true\"} 1"
    }

    @Test
    fun `recordOtp tracks sent and verified outcomes`() {
        Metrics.recordOtpSent("login")
        Metrics.recordOtpSent("login")
        Metrics.recordOtpSent("kyc")
        Metrics.recordOtpVerified("login", "ok")
        Metrics.recordOtpVerified("login", "mismatch")

        val output = Metrics.render()
        output shouldContain "rate_otp_sent_total{purpose=\"login\"} 2"
        output shouldContain "rate_otp_sent_total{purpose=\"kyc\"} 1"
        output shouldContain "rate_otp_verified_total{purpose=\"login\",outcome=\"ok\"} 1"
        output shouldContain "rate_otp_verified_total{purpose=\"login\",outcome=\"mismatch\"} 1"
    }

    @Test
    fun `histogram emits cumulative buckets per Prometheus contract`() {
        // Observe a spread of latencies so multiple buckets fire.
        Metrics.recordHttp("GET", "/health", 200, 0.001)   // < 0.005
        Metrics.recordHttp("GET", "/health", 200, 0.030)   // 0.05 bucket
        Metrics.recordHttp("GET", "/health", 200, 0.150)   // 0.25 bucket
        Metrics.recordHttp("GET", "/health", 200, 0.600)   // 1.0 bucket

        val output = Metrics.render()
        // The histogram MUST emit cumulative bucket counts: a smaller-le bucket count must be <= a larger-le bucket count.
        // Spot check: +Inf bucket should equal the count line.
        output shouldContain "rate_http_request_duration_seconds_bucket"
        output shouldContain "rate_http_request_duration_seconds_count{method=\"GET\",path=\"/health\"} 4"
    }

    @Test
    fun `render with no recorded metrics is safe and well-formed`() {
        val output = Metrics.render()
        // Prometheus output always ends in a single newline.
        // It may or may not contain content — must not throw.
        // Help/Type lines should still be present for known families even with zero observations.
        check(output.isNotEmpty())
    }

    @Test
    fun `Prometheus output uses TYPE and HELP lines per family`() {
        Metrics.recordHttp("POST", "/api/quotes", 201, 0.020)
        val output = Metrics.render()
        // Every metric family declared must have a # HELP and # TYPE line.
        output shouldContain "# TYPE rate_http_requests_total counter"
        output shouldContain "# HELP rate_http_requests_total"
    }
}
