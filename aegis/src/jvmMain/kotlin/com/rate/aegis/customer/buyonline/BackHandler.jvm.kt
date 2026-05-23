package com.rate.aegis.customer.buyonline

/**
 * JVM actual — no-op. Desktop windows don't fire a browser-style
 * `beforeunload` event, so there's nothing to install or tear down. The
 * customer can still close the window via OS chrome, but the recent
 * 1.5s-debounced server save already persists in-flight state, so there's no
 * journey to guard here.
 */
actual fun setBeforeLeaveHandler(active: Boolean) {
    // no-op — desktop windows don't trigger before-unload
}
