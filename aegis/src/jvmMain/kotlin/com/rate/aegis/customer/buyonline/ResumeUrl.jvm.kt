package com.rate.aegis.customer.buyonline

/**
 * JVM actual — there is no live URL when the buy-online journey runs in the
 * Compose Desktop preview, so we synthesise the canonical Cloudflare Pages
 * URL. Operators copying this URL still get something a real customer browser
 * could open.
 */
actual fun resumeUrl(sessionId: String): String =
    "https://phi-buyonline.pages.dev/?session=$sessionId"
