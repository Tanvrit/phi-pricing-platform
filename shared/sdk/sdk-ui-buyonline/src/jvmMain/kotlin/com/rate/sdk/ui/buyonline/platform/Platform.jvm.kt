package com.rate.sdk.ui.buyonline.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * JVM actuals — the operator desktop console embeds the buy-online preview.
 * Desktop has no live URL or browser back button, so the URL-shaped seams
 * synthesise/no-op; the launch ids mirror the monolith's `-Daegis.session` /
 * `-Daegis.quote` system properties.
 */

actual fun resumeUrl(sessionId: String): String =
    "https://phi-buyonline.pages.dev/?session=$sessionId"

actual fun setBeforeLeaveHandler(active: Boolean) {
    // no-op — desktop windows don't fire a browser before-unload event.
}

actual fun clearQuoteParam() {
    // no-op — desktop has no browser query string to scrub.
}

actual fun copyToClipboard(text: String): Boolean = try {
    Toolkit.getDefaultToolkit().systemClipboard
        .setContents(StringSelection(text), null)
    true
} catch (t: Throwable) {
    println("buyonline: clipboard write failed: ${t.message}")
    false
}

actual fun launchSessionId(): String? =
    System.getProperty("aegis.session")?.trim()?.takeIf { it.isNotEmpty() }

actual fun launchQuoteId(): String? =
    System.getProperty("aegis.quote")?.trim()?.takeIf { it.isNotEmpty() }
