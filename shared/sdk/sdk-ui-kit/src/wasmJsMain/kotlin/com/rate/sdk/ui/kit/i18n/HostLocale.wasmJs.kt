package com.rate.sdk.ui.kit.i18n

/**
 * WASM host-locale read. Prefers `navigator.language`; falls back to
 * `navigator.languages[0]` if the primary is empty; final fallback is `"en"` so
 * the caller never sees a null from the underlying JS read.
 *
 * Wrapped in `runCatching` at the actual entry point because Compose's wasmJs
 * target can fail any interop call if the bridge isn't fully initialised — we
 * degrade to null in that case and the caller treats it as "no detection".
 */
actual fun detectHostLocale(): String? = runCatching { readNavigatorLanguage() }.getOrNull()

private fun readNavigatorLanguage(): String =
    js("navigator.language || (navigator.languages && navigator.languages[0]) || 'en'")
