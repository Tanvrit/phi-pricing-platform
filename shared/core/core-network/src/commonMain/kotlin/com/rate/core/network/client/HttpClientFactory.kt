package com.rate.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The ONE platform seam in core-network: each target supplies its Ktor engine.
 *  - jvmMain    → CIO   (ktor-client-cio)
 *  - wasmJsMain → Js    (ktor-client-js)
 *  - iosMain    → Darwin(ktor-client-darwin)
 *
 * Everything else (ContentNegotiation, logging, timeouts, retry, auth) is wired
 * once in [TanvritClient] over the engine this returns, so behaviour is identical
 * on every platform.
 */
expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>

/**
 * Build a raw [HttpClient] on the platform engine with the given [block] applied.
 * [TanvritClient] uses this; direct callers normally shouldn't — go through
 * [TanvritClient] so you get the shared plugin stack.
 */
fun buildHttpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient(httpClientEngineFactory(), block)
