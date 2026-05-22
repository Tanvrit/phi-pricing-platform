package com.rate.aegis.settings

import kotlinx.browser.window

/**
 * WASM actual — single `window.localStorage` key.
 *
 * Storage key: `"aegis.settings"`. We use one key instead of one-per-field so the
 * read/write is atomic from the JS side: an operator hitting Save either replaces
 * the whole record or leaves the previous one intact.
 *
 * Parse-failure handling: if the stored value can't be deserialised (forward-
 * incompatible older shape, or someone hand-edited it badly via devtools) we
 * fall back to defaults rather than throwing. The Settings surface will then
 * show the defaults; saving from the surface overwrites the bad blob.
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
