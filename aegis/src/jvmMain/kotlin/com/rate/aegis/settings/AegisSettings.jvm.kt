package com.rate.aegis.settings

import java.io.File

/**
 * JVM actual — persists settings as JSON under the user-home.
 *
 * File path: `${user.home}/.aegis/settings.json`. A single grep-friendly dotfile
 * (rather than the OS-specific config dir) keeps the operator-tooling support
 * story simple.
 *
 * Robustness: any IO/parse exception falls back to defaults and is logged. We
 * never throw out of [load] — a broken settings file MUST NOT prevent the app
 * from booting.
 */
actual object AegisSettingsStore {

    private val settingsFile: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".aegis/settings.json")
    }

    actual fun load(): AegisSettings {
        return try {
            if (!settingsFile.exists()) return AegisSettings()
            val raw = settingsFile.readText()
            if (raw.isBlank()) return AegisSettings()
            AegisSettingsJson.decodeFromString(AegisSettings.serializer(), raw)
        } catch (t: Throwable) {
            println("[AegisSettingsStore] load failed (${t.message}); using defaults")
            AegisSettings()
        }
    }

    actual fun save(settings: AegisSettings) {
        try {
            val parent = settingsFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val json = AegisSettingsJson.encodeToString(AegisSettings.serializer(), settings)
            settingsFile.writeText(json)
        } catch (t: Throwable) {
            println("[AegisSettingsStore] save failed (${t.message}); settings not persisted")
        }
    }
}
