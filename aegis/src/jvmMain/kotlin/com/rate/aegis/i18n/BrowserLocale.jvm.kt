package com.rate.aegis.i18n

/**
 * JVM host-locale read. Returns `Locale.getDefault().language` — the ISO-639
 * language code (e.g. "en", "hi"), NOT the full BCP-47 tag (no region suffix).
 * That's deliberate: the auto-seed only cares about the language prefix.
 *
 * Wrapped in `runCatching` because the JDK contract on `Locale.getDefault()` is
 * "never null" but we'd rather degrade to "no detection" than blow up startup if
 * some bizarre security manager intercepts the call.
 */
actual fun detectHostLocale(): String? =
    runCatching { java.util.Locale.getDefault().language }.getOrNull()
