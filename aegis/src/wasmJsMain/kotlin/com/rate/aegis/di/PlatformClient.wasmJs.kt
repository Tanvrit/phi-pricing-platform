package com.rate.aegis.di

import com.rate.core.network.client.NetworkLogLevel

/**
 * WASM actual — the public browser journey.
 *
 * The Js engine is pulled in by this target's `ktor-client-js` dependency; the
 * core-network `httpClientEngineFactory()` wasmJs actual selects it. This is a
 * public build serving real customers, so request/response logging is OFF — we
 * don't spray PII (mobile, PAN, Aadhaar fragments) into the browser console.
 */
actual val platformName: String = "wasm-web"

actual val platformLogLevel: NetworkLogLevel = NetworkLogLevel.NONE
