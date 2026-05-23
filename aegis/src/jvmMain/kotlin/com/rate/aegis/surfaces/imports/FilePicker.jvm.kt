package com.rate.aegis.surfaces.imports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * JVM actual — AWT FileDialog, matching the legacy ImportScreen's affordance so
 * operators get the same native picker (the macOS file panel, the GTK file
 * chooser on Linux, the Win32 open dialog on Windows). We hop to
 * `Dispatchers.IO` for the actual `isVisible = true` call because that blocks
 * the calling thread until the user dismisses the dialog; doing it on the
 * Swing/Compose-main dispatcher would freeze the UI.
 */
actual suspend fun pickFile(
    filterDescription: String,
    extensions: List<String>,
): PickedFile? = withContext(Dispatchers.IO) {
    val lowered = extensions.map { it.lowercase().removePrefix(".") }
    val dialog = FileDialog(null as Frame?, filterDescription, FileDialog.LOAD).apply {
        // FileDialog's filter is advisory — macOS honours it, GTK and Win32
        // ignore it. That's fine; the upstream import surface re-validates
        // the chosen path by extension before uploading.
        setFilenameFilter { _, name ->
            val n = name.lowercase()
            lowered.any { ext -> n.endsWith(".$ext") }
        }
        isVisible = true
    }
    val dir = dialog.directory ?: return@withContext null
    val name = dialog.file ?: return@withContext null
    val file = File(dir, name)
    PickedFile(name = file.name, bytes = file.readBytes())
}
