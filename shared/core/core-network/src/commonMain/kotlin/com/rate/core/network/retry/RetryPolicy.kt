package com.rate.core.network.retry

import com.rate.core.network.client.RetryConfig
import com.rate.core.network.error.NetworkError
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential-backoff-with-jitter retry policy. Pure (no IO except [retry]'s
 * [delay]) so [computeDelayMillis] is unit-testable in isolation.
 *
 * Backoff for retry attempt N (N starting at 1, the delay BEFORE the Nth attempt):
 *   raw   = baseDelayMillis * 2^(N-1)
 *   capped= min(raw, maxDelayMillis)
 *   delay = capped + random(0, capped * jitterRatio)
 *
 * Jitter is additive "full-jitter-lite" — spreads a retry storm without ever
 * delaying *less* than the deterministic backoff, which keeps tests bounded.
 */
class RetryPolicy(
    private val config: RetryConfig = RetryConfig(),
    private val random: Random = Random.Default,
) {

    /**
     * Delay in millis before [attempt] (1-based; attempt 1 = first retry, i.e. the
     * delay AFTER the initial try fails). Returns 0 for attempt <= 0.
     */
    fun computeDelayMillis(attempt: Int): Long {
        if (attempt <= 0) return 0
        val exponent = (attempt - 1).coerceAtMost(30) // guard pow overflow
        val raw = config.baseDelayMillis.toDouble() * 2.0.pow(exponent)
        val capped = min(raw, config.maxDelayMillis.toDouble())
        val jitter = if (config.jitterRatio > 0.0) {
            random.nextDouble(0.0, capped * config.jitterRatio)
        } else 0.0
        return (capped + jitter).toLong()
    }

    /** Whether another attempt should be made after [error] on [attemptsSoFar] tries. */
    fun shouldRetry(error: NetworkError, attemptsSoFar: Int): Boolean {
        if (attemptsSoFar >= config.maxAttempts) return false
        if (!config.retryOnServerErrors) return false
        return when (error) {
            is NetworkError.Connectivity,
            is NetworkError.Timeout -> true
            is NetworkError.Http -> error.status >= 500 || error.status == 429
            // 4xx (auth/validation/notfound/conflict), TLS, serialization, unknown:
            // a re-send won't change the outcome — don't retry.
            else -> false
        }
    }

    /**
     * Run [block] with retry/backoff. [block] returns a value on success and must
     * throw to signal failure (the client's safeCall maps that to a NetworkError
     * for [shouldRetry] via [classify]). The last failure is re-thrown.
     */
    suspend fun <T> retry(
        classify: (Throwable) -> NetworkError,
        block: suspend (attempt: Int) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (true) {
            attempt++
            try {
                return block(attempt)
            } catch (t: Throwable) {
                lastError = t
                val netError = classify(t)
                if (!shouldRetry(netError, attempt)) throw t
                delay(computeDelayMillis(attempt))
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw lastError ?: IllegalStateException("retry loop exited without result")
    }
}
