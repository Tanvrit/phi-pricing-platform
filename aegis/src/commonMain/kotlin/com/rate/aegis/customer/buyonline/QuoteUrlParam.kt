package com.rate.aegis.customer.buyonline

/**
 * Strip the `?quote=<id>` query parameter (and any other query string) from
 * the browser address bar without reloading the page.
 *
 * Called when the customer taps "Continue to apply" on [SharedQuoteView] — we
 * want them to fall through into the buyonline journey, and a stale `?quote=`
 * param means a refresh would bounce them back to the read-only summary.
 *
 * WASM: rewrites the URL via `history.replaceState`, keeping the pathname
 * intact. JVM: no-op (desktop has no address bar).
 */
expect fun clearQuoteParam()
