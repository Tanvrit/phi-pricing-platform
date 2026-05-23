package com.rate.aegis.util

import java.awt.Desktop
import java.io.File

/**
 * JVM actual — write the CSV to `~/Downloads/<filename>`, then best-effort
 * open the Downloads folder so the operator can see the file appear.
 * All failures are swallowed (logged via println) so a borked home dir or
 * a headless JVM never crashes the surface.
 */
actual fun saveCsv(filename: String, csv: String) {
    val home = System.getProperty("user.home") ?: "."
    val dir = File(home, "Downloads")
    dir.mkdirs()
    val file = File(dir, filename)
    runCatching { file.writeText(csv) }
        .onFailure { println("aegis: CSV save failed: ${it.message}") }
        .onSuccess {
            runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                    Desktop.getDesktop().open(dir)
            }.onFailure { println("aegis: CSV reveal failed: ${it.message}") }
        }
}
