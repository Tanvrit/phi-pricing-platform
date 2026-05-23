package com.rate.aegis.customer.buyonline

/**
 * WASM actual — toggle a `beforeunload` listener that signals "unsaved work"
 * to the browser. When active, hitting the back button (or closing the tab,
 * or following a link off-site) triggers the browser's native "Leave site?"
 * confirmation. Modern browsers ignore any custom message for security
 * reasons, so we only set `returnValue = ''` — the prompt copy is whatever
 * the user agent ships.
 *
 * Implementation note
 * -------------------
 * Kotlin/Wasm's `js(...)` body must be a compile-time string constant living
 * at top-level function scope, mirroring the convention in
 * [ResumeClipboard.wasmJs.kt] and [CsvExport.wasmJs.kt]. We stash the
 * listener reference on `window.__aegisBeforeUnload` so the remove path can
 * detach the *same* function — `removeEventListener` on an anonymous lambda
 * would be a no-op.
 */
actual fun setBeforeLeaveHandler(active: Boolean) {
    runCatching { if (active) installBeforeUnload() else removeBeforeUnload() }
}

private fun installBeforeUnload(): Unit =
    js(
        "{ " +
            "if (!window.__aegisBeforeUnload) { " +
                "window.__aegisBeforeUnload = function(e) { e.preventDefault(); e.returnValue = ''; return ''; }; " +
                "window.addEventListener('beforeunload', window.__aegisBeforeUnload); " +
            "} " +
        "}"
    )

private fun removeBeforeUnload(): Unit =
    js(
        "{ " +
            "if (window.__aegisBeforeUnload) { " +
                "window.removeEventListener('beforeunload', window.__aegisBeforeUnload); " +
                "window.__aegisBeforeUnload = null; " +
            "} " +
        "}"
    )
