package com.rate.aegis.settings

import kotlinx.browser.window

/**
 * WASM actual — single `window.localStorage` key.
 *
 * Storage key: `"aegis.settings"`. One key (not one-per-field) so the read/write
 * is atomic from the JS side. A value that can't be deserialised (forward-
 * incompatible older shape, or a hand-edit via devtools) falls back to defaults
 * rather than throwing; the next save overwrites the bad blob.
 */
private const val STORAGE_KEY = "aegis.settings"

actual object AegisSettingsStore {

    actual fun load(): AegisSettings {
        val raw: String? = try {
            window.localStorage.getItem(STORAGE_KEY)
        } catch (t: Throwable) {
            println("[AegisSettingsStore] localStorage.getItem threw (${t.message}); using defaults")
            null
        }
        if (raw.isNullOrBlank()) return AegisSettings()
        return try {
            AegisSettingsJson.decodeFromString(AegisSettings.serializer(), raw)
        } catch (t: Throwable) {
            println("[AegisSettingsStore] parse failed (${t.message}); using defaults")
            AegisSettings()
        }
    }

    actual fun save(settings: AegisSettings) {
        try {
            val json = AegisSettingsJson.encodeToString(AegisSettings.serializer(), settings)
            window.localStorage.setItem(STORAGE_KEY, json)
        } catch (t: Throwable) {
            println("[AegisSettingsStore] save failed (${t.message}); settings not persisted")
        }
    }
}
