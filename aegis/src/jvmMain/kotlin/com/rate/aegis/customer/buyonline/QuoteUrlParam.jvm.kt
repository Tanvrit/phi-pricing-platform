package com.rate.aegis.customer.buyonline

/**
 * JVM actual — no-op. Desktop windows have no URL bar to clear; the equivalent
 * JVM launch path uses `-Daegis.quote=…` and that property isn't re-read after
 * startup, so there's nothing to scrub.
 */
actual fun clearQuoteParam() {
    // no-op — desktop has no browser query string
}
