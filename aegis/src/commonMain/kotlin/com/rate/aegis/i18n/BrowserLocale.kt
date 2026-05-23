package com.rate.aegis.i18n

/**
 * Best-effort host-locale read.
 *
 *  - JVM returns the system locale's language tag (e.g. "en", "hi").
 *  - WASM returns `navigator.language` (or the first entry of `navigator.languages`).
 *
 * May return null if the platform doesn't expose one (or the call blew up under
 * us — both actuals wrap the underlying read in `runCatching`).
 *
 * Used once at process start by the platform `main` to seed
 * [com.rate.aegis.AegisLaunchContext.hostLocale]; the AegisRoot then performs a
 * one-time auto-seed of [com.rate.aegis.settings.AegisSettings.locale] when the
 * persisted settings have never been touched by an operator and the host says
 * Hindi.
 *
 * NOT called on every composition — read once, persist-then-stop.
 */
expect fun detectHostLocale(): String?
