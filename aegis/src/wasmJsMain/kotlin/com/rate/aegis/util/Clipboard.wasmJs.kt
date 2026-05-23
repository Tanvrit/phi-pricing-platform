package com.rate.aegis.util

/**
 * WASM actual — invoke the browser `navigator.clipboard.writeText` API. The
 * call returns a Promise that we intentionally drop on the floor: success
 * requires either a user gesture or a granted clipboard permission, both of
 * which the framework can't observe synchronously. We optimistically report
 * `true` so the UI can flip to the "Copied" state immediately; if the Promise
 * later rejects (no permission, insecure context), the worst case is a
 * confirmation toast for a copy that didn't land.
 *
 * Kotlin/Wasm requires every `js(...)` call to live at the top-level of its
 * own function body, so the actual call is factored into [writeClipboard].
 * The `&&` guard inside that JS short-circuits to `undefined` on ancient
 * browsers without `navigator.clipboard`, avoiding a TypeError.
 */
actual fun copyToClipboard(text: String): Boolean = try {
    writeClipboard(text)
    true
} catch (t: Throwable) {
    false
}

private fun writeClipboard(text: String): Unit =
    js("{ if (navigator.clipboard) { navigator.clipboard.writeText(text); } }")
