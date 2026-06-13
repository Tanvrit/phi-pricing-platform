package com.rate.core.network.client

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

/**
 * WASM/browser engine: Js — delegates to the browser's `fetch`. TLS and any
 * certificate pinning are owned by the browser; [com.rate.core.network.security.CertPin]
 * descriptors are advisory here.
 */
actual fun httpClientEngineFactory(): HttpClientEngineFactory<*> = Js
