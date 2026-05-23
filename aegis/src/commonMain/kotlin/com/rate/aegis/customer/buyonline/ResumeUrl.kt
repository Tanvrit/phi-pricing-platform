package com.rate.aegis.customer.buyonline

/**
 * Build the full URL a customer can use to resume their journey. WASM derives
 * the origin/path from `window.location` so dev (`localhost:8081`), preview
 * (`*.pages.dev`), and prod all return the right host; JVM has no real
 * deployment surface so it falls back to the canonical Cloudflare Pages URL
 * — operator desktop previews are the only JVM consumer.
 */
expect fun resumeUrl(sessionId: String): String
