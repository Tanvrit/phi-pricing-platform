package com.rate.core.network.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/** iOS engine: Darwin — backed by NSURLSession. Supports native cert pinning. */
actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
