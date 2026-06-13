package com.rate.sdk.ui.kit.i18n

/**
 * Best-effort host-locale read.
 *
 *  - JVM returns the system locale's language tag (e.g. "en", "hi").
 *  - WASM returns `navigator.language` (or the first entry of `navigator.languages`).
 *
 * May return null if the platform doesn't expose one (or the call blew up under
 * us — both actuals wrap the underlying read in `runCatching`).
 *
 * Intended to be read ONCE at process start by the platform `main` to seed the
 * persisted locale on first launch (auto-pick Hindi when the host says Hindi).
 * NOT called on every composition — read once, persist-then-stop.
 */
expect fun detectHostLocale(): String?
