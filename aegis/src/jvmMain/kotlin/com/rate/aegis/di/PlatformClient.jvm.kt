package com.rate.aegis.di

import com.rate.core.network.client.NetworkLogLevel

/**
 * JVM actual — the desktop operator console.
 *
 * The CIO engine is pulled in by this target's `ktor-client-cio` dependency; the
 * core-network `httpClientEngineFactory()` jvm actual selects it. The desktop
 * binary is an internal operator tool, so HEADERS-level request logging is left
 * on to make field-debugging connectivity issues painless.
 */
actual val platformName: String = "jvm-desktop"

actual val platformLogLevel: NetworkLogLevel = NetworkLogLevel.HEADERS
