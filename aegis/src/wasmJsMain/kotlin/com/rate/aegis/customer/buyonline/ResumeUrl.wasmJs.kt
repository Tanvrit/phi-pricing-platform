package com.rate.aegis.customer.buyonline

import kotlinx.browser.window

/**
 * WASM actual — read the current page's origin + path from `window.location`
 * so the resume URL matches whatever host the customer is on (localhost dev,
 * the staging `*.pages.dev`, prod custom domain, etc.). We deliberately drop
 * any existing query string — `?session=...` is the only param the resume
 * flow cares about and the launch bootstrap reads it directly.
 *
 * Wrapped in `runCatching` because `window.location` access can throw on
 * exotic sandboxed iframes; we fall back to a relative `?session=...` URL
 * which the browser will resolve against the current document.
 */
actual fun resumeUrl(sessionId: String): String = runCatching {
    val loc = window.location
    "${loc.origin}${loc.pathname}?session=$sessionId"
}.getOrElse { "?session=$sessionId" }
