package com.rate.aegis.di

import com.rate.core.network.client.NetworkLogLevel

/**
 * The single platform seam for the Aegis transport.
 *
 * The Ktor *engine* itself is already a core-network concern (its
 * `httpClientEngineFactory()` expect/actual picks CIO on JVM and Js on WASM), so
 * this seam carries only the per-platform tuning the app shell wants on top:
 *
 *  - [platformName]   a short tag stamped into the `User-Agent` so server logs can
 *                     tell the desktop console apart from the browser journey.
 *  - [platformLogLevel] verbose request logging on the JVM desktop binary (an
 *                     internal operator tool), quiet on the public WASM build (the
 *                     customer journey ships over the wire to real users — no
 *                     request/response logging in the browser console).
 *
 * Keeping this as a seam (rather than a `when (target)` branch) keeps the
 * [AppClientFactory] platform-free and makes the desktop-vs-web posture explicit.
 *
 * The corresponding `ktor-client-cio` (jvmMain) / `ktor-client-js` (wasmJsMain)
 * dependencies are declared in this module's build so the core-network engine
 * actual resolves at link time on each target.
 */
expect val platformName: String

expect val platformLogLevel: NetworkLogLevel
