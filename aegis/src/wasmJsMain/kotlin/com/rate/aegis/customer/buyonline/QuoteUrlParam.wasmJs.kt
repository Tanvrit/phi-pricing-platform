package com.rate.aegis.customer.buyonline

/**
 * WASM actual — drop the entire query string (and fragment) from the address
 * bar by rewriting the current history entry. We deliberately use
 * `replaceState` rather than `pushState` so the back button doesn't return the
 * customer to the `?quote=…` view they just dismissed.
 *
 * Wrapped in `runCatching` so the journey continues even when the browser
 * unexpectedly rejects the call (e.g. some sandboxed embeds disable history
 * mutation). The user-visible cost of a no-op here is just a slightly stale
 * URL — not worth aborting the Continue flow over.
 */
actual fun clearQuoteParam() {
    runCatching { replaceStateNoQuery() }
}

private fun replaceStateNoQuery(): Unit =
    js("history.replaceState(null, '', window.location.pathname)")
