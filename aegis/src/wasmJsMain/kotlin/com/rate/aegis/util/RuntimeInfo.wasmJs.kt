package com.rate.aegis.util

/**
 * WASM actuals — diagnostic surface delegates to the browser.
 *
 * `navigator.userAgent` returns a string in every modern browser; we still
 * wrap the read in [runCatching] because exotic embedded contexts (service
 * workers, sandboxed iframes with privacy-mode polyfills) occasionally throw
 * a SecurityError on access. The 80-char cap is for layout sanity — UA
 * strings frequently exceed 200 chars and would otherwise dominate the row.
 *
 * Implementation note
 * -------------------
 * Kotlin/Wasm requires every `js(...)` body to live at top-level in its own
 * private helper, mirroring the convention used elsewhere in the module
 * (see ResumeClipboard.wasmJs.kt, CsvExport.wasmJs.kt). The helper returns
 * a [String] which Kotlin/Wasm marshals automatically from the JS string.
 */
actual fun runtimeKind(): String = "WASM"

actual fun runtimeHostInfo(): String =
    runCatching { hostUserAgent() }
        .getOrDefault("unknown browser")
        .take(80)

private fun hostUserAgent(): String =
    js("navigator.userAgent")
