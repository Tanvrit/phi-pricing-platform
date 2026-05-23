package com.rate.aegis.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Operator-tunable settings for the Aegis shell.
 *
 * Persisted locally per-device (a flat JSON file on JVM, a single `localStorage`
 * key on WASM). NOT roamed between machines — the settings file/key lives on
 * whichever desktop binary or browser the operator opens.
 *
 *   serverBaseUrl  — host the server-driven surfaces should call (HomeSurface's
 *                    `LiveDashboardRepo`, the operator `ApiClient`, the audit
 *                    feed, etc.). Defaults to the local-dev port (9090).
 *   defaultRole    — role the desktop binary boots into when launched without
 *                    `-Daegis.role=...`, and the seed for the URL query param
 *                    on the web entry. Stored as the [com.rate.aegis.AegisRole]
 *                    enum name ("CUSTOMER" / "BUSINESS" / "ADMIN").
 *   theme          — palette identifier the AegisTheme should provide. Free-form
 *                    string so we can extend to "dim" / "high-contrast" later
 *                    without a schema migration. Currently recognised values:
 *                    "light" (default) and "dark". Anything else falls back to
 *                    light at theme resolution time.
 *
 * The settings record is intentionally narrow.
 */
@Serializable
data class AegisSettings(
    val serverBaseUrl: String = "http://localhost:9090",
    val defaultRole: String = "BUSINESS",
    val theme: String = "light",
)

/**
 * Shared [Json] used by every actual. `ignoreUnknownKeys = true` so older saved
 * files don't blow up after we add a field; `prettyPrint` keeps the JVM file
 * grep-friendly for ops who want to hand-edit it.
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
 * the call returns/keeps the defaults so the UI never breaks because of a
 * corrupt persisted file. Failures are logged via `println` (Aegis doesn't
 * pull in SLF4J at this layer).
 */
expect object AegisSettingsStore {
    fun load(): AegisSettings
    fun save(settings: AegisSettings)
}
