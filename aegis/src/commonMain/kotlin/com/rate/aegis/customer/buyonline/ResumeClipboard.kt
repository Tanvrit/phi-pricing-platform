package com.rate.aegis.customer.buyonline

/**
 * Best-effort host clipboard write. Returns true if the platform call did not
 * throw; the JVM AWT path is synchronous so the boolean is meaningful, but the
 * WASM path hands off to a Promise we deliberately do not await — there `true`
 * just means the API was reachable, not that the user granted the permission
 * prompt. Either way the caller flips the inline confirmation optimistically.
 */
expect fun copyToClipboard(text: String): Boolean
