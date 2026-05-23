package com.rate.aegis.util

/**
 * Best-effort host clipboard write. Returns true if the platform call did not
 * throw; the JVM AWT path is synchronous so the boolean is meaningful, but the
 * WASM path hands off to a Promise we deliberately do not await — there `true`
 * just means the API was reachable, not that the user granted the permission
 * prompt. Either way the caller flips the inline confirmation optimistically.
 *
 * Originally lived under `customer.buyonline` to back the resume-link copy
 * action. Promoted to `util` once a second caller (the Settings "System info"
 * card) needed the same affordance — keeping it in the buyonline package
 * would have created a non-obvious cross-feature dependency.
 */
expect fun copyToClipboard(text: String): Boolean
