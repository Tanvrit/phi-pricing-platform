package com.rate.sdk.ui.buyonline.platform

import kotlinx.browser.window

/**
 * WASM actuals — the public customer journey runs in the browser (Cloudflare
 * Pages). These reach into `window.location` / `navigator` / `history`.
 *
 * Kotlin/Wasm requires every `js(...)` call to live at the top-level of its own
 * function body, so the raw JS calls are factored into private helpers.
 */

actual fun resumeUrl(sessionId: String): String = runCatching {
    val loc = window.location
    "${loc.origin}${loc.pathname}?session=$sessionId"
}.getOrElse { "?session=$sessionId" }

actual fun setBeforeLeaveHandler(active: Boolean) {
    runCatching { if (active) installBeforeUnload() else removeBeforeUnload() }
}

actual fun clearQuoteParam() {
    runCatching { replaceStateNoQuery() }
}

actual fun copyToClipboard(text: String): Boolean = try {
    writeClipboard(text)
    true
} catch (t: Throwable) {
    false
}

actual fun launchSessionId(): String? = paramFromSearch("session")

actual fun launchQuoteId(): String? = paramFromSearch("quote")

// ── private JS seams ──────────────────────────────────────────────────────────

private fun paramFromSearch(key: String): String? {
    val search = runCatching { window.location.search }.getOrElse { "" }
    if (search.isEmpty() || search == "?") return null
    val pair = search.removePrefix("?").split("&")
        .firstOrNull { it.startsWith("$key=", ignoreCase = true) } ?: return null
    return pair.substringAfter('=').trim().ifBlank { null }
}

private fun writeClipboard(text: String): Unit =
    js("{ if (navigator.clipboard) { navigator.clipboard.writeText(text); } }")

private fun replaceStateNoQuery(): Unit =
    js("history.replaceState(null, '', window.location.pathname)")

private fun installBeforeUnload(): Unit =
    js(
        "{ " +
            "if (!window.__buyonlineBeforeUnload) { " +
                "window.__buyonlineBeforeUnload = function(e) { e.preventDefault(); e.returnValue = ''; return ''; }; " +
                "window.addEventListener('beforeunload', window.__buyonlineBeforeUnload); " +
            "} " +
        "}"
    )

private fun removeBeforeUnload(): Unit =
    js(
        "{ " +
            "if (window.__buyonlineBeforeUnload) { " +
                "window.removeEventListener('beforeunload', window.__buyonlineBeforeUnload); " +
                "window.__buyonlineBeforeUnload = null; " +
            "} " +
        "}"
    )
