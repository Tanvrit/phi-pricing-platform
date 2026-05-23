package com.rate.aegis.customer.buyonline

/**
 * Install (or remove) a confirm-before-leaving handler on the host platform.
 *
 * On WASM, hooks `window.onbeforeunload` so the browser shows its native
 * "Leave site?" dialog. The browser ignores custom messages for security
 * reasons; the only thing we can do is signal "there's unsaved work".
 *
 * On JVM, no-op — desktop windows don't have a back button.
 */
expect fun setBeforeLeaveHandler(active: Boolean)
