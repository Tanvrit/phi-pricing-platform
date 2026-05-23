package com.rate.aegis.util

/**
 * Two thin platform shims surfacing diagnostic context for the Settings
 * "System info" card. We deliberately keep the return type as plain [String]
 * (not a richer struct) so the rendering code stays target-agnostic and the
 * actuals can degrade to placeholders ("?", "unknown browser") when the host
 * doesn't expose the data — see the WASM actual for the `navigator.userAgent`
 * fallback.
 */

/** Coarse target tag: "JVM" or "WASM". Cheap, never throws. */
expect fun runtimeKind(): String

/**
 * Free-form host descriptor:
 *  - JVM: `${os.name} ${os.version}` (best-effort, `?` if a property is missing)
 *  - WASM: `navigator.userAgent` truncated to 80 chars
 *
 * Never throws — the actuals catch any host weirdness and return a placeholder.
 */
expect fun runtimeHostInfo(): String
