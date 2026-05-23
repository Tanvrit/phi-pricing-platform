package com.rate.aegis.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * JVM actual — push the string to the AWT system clipboard. Wrapped in a
 * try/catch because headless JDKs (`-Djava.awt.headless=true`, CI sandboxes)
 * raise `HeadlessException` on `getDefaultToolkit()` and we'd rather degrade
 * gracefully than crash the calling UI.
 */
actual fun copyToClipboard(text: String): Boolean = try {
    Toolkit.getDefaultToolkit().systemClipboard
        .setContents(StringSelection(text), null)
    true
} catch (t: Throwable) {
    println("aegis: clipboard write failed: ${t.message}")
    false
}
