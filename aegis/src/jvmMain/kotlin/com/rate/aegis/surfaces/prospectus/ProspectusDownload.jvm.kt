package com.rate.aegis.surfaces.prospectus

import java.awt.Desktop
import java.io.File

/**
 * JVM actual — write the prospectus HTML to `~/Downloads/${planId}-prospectus.html`
 * and try to open it in the default browser. Save failures are logged via
 * `println` but never thrown — the surface stays usable even if the home
 * directory is read-only or the AWT Desktop integration is unavailable
 * (headless CI, restricted sandboxes, etc.).
 */
actual fun openOrSaveProspectus(planId: String, html: String) {
    try {
        val home = System.getProperty("user.home") ?: "."
        val downloads = File(home, "Downloads")
        if (!downloads.exists()) downloads.mkdirs()
        val out = File(downloads, "$planId-prospectus.html")
        out.writeText(html, Charsets.UTF_8)

        // Best-effort open in the default browser. Both checks needed —
        // `isDesktopSupported` can be true on a Mac while BROWSE is still
        // unavailable (e.g. a headless JDK with -Djava.awt.headless=true).
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                runCatching { desktop.browse(out.toURI()) }
                    .onFailure { println("aegis: prospectus browse failed: ${it.message}") }
            }
        }
    } catch (t: Throwable) {
        println("aegis: prospectus save failed: ${t.message}")
    }
}
