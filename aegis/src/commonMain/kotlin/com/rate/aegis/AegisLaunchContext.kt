package com.rate.aegis

/**
 * Mutable singleton for one-shot launch parameters that can't be plumbed via
 * composables (URL query, JVM system properties). Set once at process start by
 * the platform `main` (wasmJs reads `window.location.search`, jvm reads
 * `System.getProperty`), read once on first composition by [com.rate.aegis.customer.buyonline.BuyOnlineApp].
 *
 * Kept in `commonMain` so both platforms can write/read without expect/actual
 * scaffolding — there's no actual platform difference, just a different
 * extraction point.
 */
object AegisLaunchContext {
    /** Session id from `?session=...` URL query or `-Daegis.session=...`. Null = fresh journey. */
    var sessionId: String? = null

    /**
     * Saved-quote id from `?quote=...` URL query or `-Daegis.quote=...`. When set,
     * the customer entry point short-circuits the buyonline journey and renders a
     * read-only summary of the shared quote — the affordance the operator hands a
     * customer ("here's the price we discussed: <link>").
     *
     * Null = no shared quote, fall through to the normal journey.
     */
    var quoteId: String? = null
}
