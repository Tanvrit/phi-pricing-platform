package com.rate.sdk.ui.buyonline.platform

/**
 * Platform seams for the buy-online journey — the only places the pure-KMP UI
 * has to reach into the host (browser DOM / desktop window). Each is `expect`
 * here and implemented per target (`wasmJsMain` = real browser behaviour,
 * `jvmMain` = a sensible desktop no-op / fallback).
 *
 * RELOCATED from the monolith's `com.rate.aegis.customer.buyonline.{ResumeUrl,
 * BackHandler,QuoteUrlParam}` + `com.rate.aegis.util.Clipboard`, consolidated
 * here so the journey carries its own host seams rather than depending on the
 * operator app shell.
 */

/**
 * Build the full URL a customer can use to resume their journey. WASM derives
 * the origin/path from `window.location` so dev, preview (`*.pages.dev`) and
 * prod all return the right host; JVM falls back to the canonical Cloudflare
 * Pages URL (operator desktop previews are the only JVM consumer).
 */
expect fun resumeUrl(sessionId: String): String

/**
 * Install (or remove) a confirm-before-leaving handler on the host platform.
 * WASM hooks `window.onbeforeunload` so the browser shows its native "Leave
 * site?" dialog when there is unsaved in-flight work; JVM is a no-op.
 */
expect fun setBeforeLeaveHandler(active: Boolean)

/**
 * Strip the `?quote=<id>` query string from the browser address bar without
 * reloading the page (so a refresh after "Continue to apply" lands in the
 * journey, not the shared-quote summary). WASM rewrites via
 * `history.replaceState`; JVM is a no-op.
 */
expect fun clearQuoteParam()

/**
 * Best-effort host clipboard write. Returns true if the platform call did not
 * throw; the WASM path hands off to a Promise it does not await, so `true`
 * there means "the API was reachable", not "the user granted permission".
 */
expect fun copyToClipboard(text: String): Boolean

/**
 * One-shot launch context read at process start: the `?session=<id>` resume
 * bearer and the `?quote=<id>` shared-quote id. WASM parses
 * `window.location.search`; JVM reads the `-Daegis.session` / `-Daegis.quote`
 * system properties. Both null when absent (brand-new customer).
 */
expect fun launchSessionId(): String?

expect fun launchQuoteId(): String?
