package com.rate.core.network.client

import com.rate.core.network.security.CertPin

/**
 * Immutable configuration for a [TanvritClient]. Everything the transport needs
 * is here so the client itself is just wiring — handy for tests (swap a config)
 * and for the app layer (one place to read secrets/URLs out of env at startup).
 *
 * @property baseUrl    Scheme + host (+ optional port/prefix), NO trailing slash —
 *                      e.g. `http://localhost:9090`. Relative request paths are
 *                      resolved against this.
 * @property timeouts   Connect / request / socket budgets in millis.
 * @property retry      Retry/backoff knobs handed to the [com.rate.core.network.retry.RetryPolicy].
 * @property certPins   Optional certificate-pinning descriptors. Empty = no pinning.
 *                      Pinning is enforced by the platform engine where supported;
 *                      the descriptor is carried here so config stays platform-free.
 * @property enableLogging  Install Ktor's [io.ktor.client.plugins.logging.Logging]
 *                          plugin. Off in production builds that ship PII over the wire.
 * @property logLevel       Verbosity when [enableLogging] is true.
 * @property defaultHeaders Static headers applied to every request (e.g. an API key,
 *                          a build/version tag). Per-request headers still override.
 * @property userAgent      Value for the `User-Agent` header. Null leaves the engine default.
 */
data class TanvritClientConfig(
    val baseUrl: String,
    val timeouts: Timeouts = Timeouts(),
    val retry: RetryConfig = RetryConfig(),
    val certPins: List<CertPin> = emptyList(),
    val enableLogging: Boolean = false,
    val logLevel: NetworkLogLevel = NetworkLogLevel.HEADERS,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val userAgent: String? = "tanvrit-client",
) {
    /** [baseUrl] with any trailing slash stripped, so path joins never double up. */
    val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')

    /** Join [normalizedBaseUrl] with a request [path] (absolute http(s) URLs pass through). */
    fun resolve(path: String): String = when {
        path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) -> path
        path.startsWith("/") -> normalizedBaseUrl + path
        else -> "$normalizedBaseUrl/$path"
    }
}

/**
 * Network timeout budgets (millis). Mirrors Ktor's HttpTimeout plugin knobs but
 * kept as a plain data class so config stays engine-agnostic.
 *
 * @property connectMillis TCP/TLS connect budget.
 * @property requestMillis End-to-end budget for a whole call (headers + body).
 * @property socketMillis  Max idle between two data packets once connected.
 */
data class Timeouts(
    val connectMillis: Long = 10_000,
    val requestMillis: Long = 30_000,
    val socketMillis: Long = 30_000,
)

/**
 * Knobs for the exponential-backoff [com.rate.core.network.retry.RetryPolicy].
 *
 * @property maxAttempts   Total tries INCLUDING the first (1 = no retry).
 * @property baseDelayMillis Delay before the first retry; doubles each attempt.
 * @property maxDelayMillis  Ceiling so backoff doesn't run away.
 * @property jitterRatio   0.0..1.0 fraction of the computed delay added as random
 *                         jitter to avoid thundering-herd retries.
 * @property retryOnServerErrors Retry on 5xx / 429 / network faults. 4xx (except 429)
 *                         is never retried — it won't succeed on a re-send.
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 200,
    val maxDelayMillis: Long = 5_000,
    val jitterRatio: Double = 0.25,
    val retryOnServerErrors: Boolean = true,
)

/** Logging verbosity, decoupled from Ktor's LogLevel so commonMain stays thin. */
enum class NetworkLogLevel { NONE, INFO, HEADERS, BODY, ALL }
