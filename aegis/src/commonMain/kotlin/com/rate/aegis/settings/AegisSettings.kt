package com.rate.aegis.settings

import com.rate.sdk.ui.kit.i18n.AegisLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Device-local settings for the Aegis app shell.
 *
 * Persisted per-device (a flat JSON file on JVM, a single `localStorage` key on
 * WASM). NOT roamed between machines. After the re-architecture this record is
 * intentionally tiny — the shell owns no surfaces and therefore no per-surface
 * preferences; those live inside the sdk-ui-* modules / the server.
 *
 *   serverBaseUrl — base URL every SDK network client (the one shared
 *                   [com.rate.core.network.client.TanvritClient]) resolves
 *                   request paths against. Defaults to the local-dev port (9090).
 *   defaultRole   — role the desktop binary boots into when launched without
 *                   `-Daegis.role=...`. Stored as the [com.rate.aegis.AegisRole]
 *                   enum name. The WASM entry deliberately does NOT seed the
 *                   implicit role from here (the bare public URL is always
 *                   CUSTOMER) — see the WASM `Main.kt`.
 *   locale        — customer-journey language ([AegisLocale] code, "en"/"hi").
 *                   Anything else parses back to EN.
 *   operatorIdentity — free-form operator self-identity forwarded as the
 *                   `X-Aegis-Actor` request header on operator/admin calls so the
 *                   server can attribute audit rows. Empty = no identity set.
 *   dark          — operator console theme toggle (host owns the preference; the
 *                   customer journey is always the PRU light brand).
 */
@Serializable
data class AegisSettings(
    val serverBaseUrl: String = "http://localhost:9090",
    val defaultRole: String = "BUSINESS",
    val locale: String = "en",
    val operatorIdentity: String = "",
    val dark: Boolean = false,
) {
    /** [locale] parsed into the kit enum (falls back to EN for unknown codes). */
    val aegisLocale: AegisLocale get() = AegisLocale.fromCode(locale)
}

/**
 * Shared [Json] used by every actual. `ignoreUnknownKeys = true` so older saved
 * files (from the monolith's wider settings shape) don't blow up; `prettyPrint`
 * keeps the JVM file grep-friendly for ops who want to hand-edit it.
 */
internal val AegisSettingsJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

/**
 * Platform-backed storage for [AegisSettings].
 *
 *   JVM   — `${user.home}/.aegis/settings.json`
 *   WASM  — `window.localStorage["aegis.settings"]`
 *
 * Both implementations are best-effort: any IO / parse failure is swallowed and
 * the call returns/keeps the defaults so the launcher never breaks because of a
 * corrupt persisted file. Failures are logged via `println` (Aegis doesn't pull
 * in a logging facade at this layer).
 */
expect object AegisSettingsStore {
    fun load(): AegisSettings
    fun save(settings: AegisSettings)
}
