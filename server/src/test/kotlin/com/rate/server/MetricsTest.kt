package com.rate.server

import com.rate.server.metrics.Metrics
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MetricsTest {

    @AfterTest
    fun reset() = Metrics.resetForTest()

    @Test
    fun `http counter + histogram render in prometheus form`() {
        Metrics.recordHttp("GET", "/api/quotes/Q-01H", 200, 0.012)
        val out = Metrics.render()
        // The high-cardinality id is normalised away.
        assertTrue("rate_http_requests_total{method=\"GET\",path=\"/api/quotes/{id}\",status=\"200\"} 1" in out, out)
        assertTrue("rate_http_request_duration_seconds_count" in out, out)
    }

    @Test
    fun `registered gauge renders`() {
        Metrics.registerGauge("rate_test_gauge", "test", { 42 })
        val out = Metrics.render()
        assertTrue("rate_test_gauge 42" in out, out)
    }

    @Test
    fun `normalisePath collapses ids and prefixes`() {
        assertTrue(Metrics.normalisePath("/api/quotes/Q-abc123") == "/api/quotes/{id}")
        assertTrue(Metrics.normalisePath("/api/buy-online/proposal/PHI-XYZ") == "/api/buy-online/proposal/{id}")
        assertTrue(Metrics.normalisePath("/health") == "/health")
    }
}
