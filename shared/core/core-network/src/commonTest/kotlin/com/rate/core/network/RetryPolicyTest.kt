package com.rate.core.network

import com.rate.core.network.client.RetryConfig
import com.rate.core.network.error.NetworkError
import com.rate.core.network.retry.RetryPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPolicyTest {

    // jitterRatio 0 → deterministic backoff: base * 2^(attempt-1), capped.
    private val noJitter = RetryPolicy(
        RetryConfig(maxAttempts = 5, baseDelayMillis = 100, maxDelayMillis = 5_000, jitterRatio = 0.0),
        random = Random(0),
    )

    @Test
    fun exponential_backoff_doubles_each_attempt() {
        assertEquals(0L, noJitter.computeDelayMillis(0))
        assertEquals(100L, noJitter.computeDelayMillis(1))
        assertEquals(200L, noJitter.computeDelayMillis(2))
        assertEquals(400L, noJitter.computeDelayMillis(3))
        assertEquals(800L, noJitter.computeDelayMillis(4))
    }

    @Test
    fun backoff_is_capped_at_max() {
        // 100 * 2^9 = 51200 > 5000 cap.
        assertEquals(5_000L, noJitter.computeDelayMillis(10))
    }

    @Test
    fun jitter_never_drops_below_deterministic_backoff() {
        val withJitter = RetryPolicy(
            RetryConfig(baseDelayMillis = 100, jitterRatio = 0.5),
            random = Random(42),
        )
        repeat(20) {
            val d = withJitter.computeDelayMillis(2) // base 200, +0..100 jitter
            assertTrue(d in 200L..300L, "delay $d out of [200,300]")
        }
    }

    @Test
    fun retries_5xx_and_429_but_not_4xx() {
        val p = RetryPolicy(RetryConfig(maxAttempts = 3))
        assertTrue(p.shouldRetry(NetworkError.Http(500), 1))
        assertTrue(p.shouldRetry(NetworkError.Http(503), 1))
        assertTrue(p.shouldRetry(NetworkError.Http(429), 1))
        assertTrue(p.shouldRetry(NetworkError.Timeout(), 1))
        assertTrue(p.shouldRetry(NetworkError.Connectivity(), 1))
        assertFalse(p.shouldRetry(NetworkError.Http(404), 1))
        assertFalse(p.shouldRetry(NetworkError.Unauthorized(), 1))
        assertFalse(p.shouldRetry(NetworkError.Validation(), 1))
    }

    @Test
    fun stops_at_max_attempts() {
        val p = RetryPolicy(RetryConfig(maxAttempts = 3))
        assertTrue(p.shouldRetry(NetworkError.Http(500), 2))
        assertFalse(p.shouldRetry(NetworkError.Http(500), 3))
    }
}
