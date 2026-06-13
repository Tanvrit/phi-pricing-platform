package com.rate.core.network.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

/** JVM engine: CIO (coroutine IO) — pure-Kotlin, no extra native deps. */
actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = CIO
