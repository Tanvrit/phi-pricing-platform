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
 *   operatorIdentity — free-form string the operator types into Settings to
 *                    self-identify (name, email, employee id). The Aegis
 *                    `ApiClient` forwards it as the `X-Aegis-Actor` request
 *                    header; the server stamps it on every `audit_event` row.
 *                    Empty string ("") means "no identity set" — the server
 *                    falls back to `AuditActor.unknown()`. This is a
 *                    trust-the-header pass-through; real auth (Phase 2) will
 *                    replace it with a verified JWT subject.
 *   locale         — language code for the customer-journey strings (buyonline).
 *                    Currently recognised: "en" (default) and "hi". Anything
 *                    else falls back to English. Operator surfaces stay
 *                    English-only — see `com.rate.aegis.i18n.Strings`.
 *   mutedNotificationActions — set of audit `action` strings the operator has
 *                    chosen to hide from the bell-icon NotificationCenter
 *                    dropdown. ONLY affects that dropdown — the audit log
 *                    surface, ActivityFeed, and the server-side audit chain
 *                    are unaffected. Read once on first composition of the
 *                    notification list (matches the theme/locale apply-on-
 *                    next-open contract). Default empty = nothing muted.
 *   localeAutoSeeded — one-shot guard for the browser-locale auto-seed. The
 *                    AegisRoot reads `navigator.language` (or JVM
 *                    `Locale.getDefault()`) on first launch and, if it starts
 *                    with "hi" AND this flag is still `false`, persists
 *                    `locale = "hi", localeAutoSeeded = true`. Once flipped,
 *                    we NEVER auto-seed again — even if the operator manually
 *                    switches back to "en". That decision sticks. The flag is
 *                    intentionally single-purpose; the cost is one boolean in
 *                    the persisted JSON, and the alternative (skip-on-later)
 *                    would silently regress any future Hindi-speaking customer.
 *   seenBuild      — the most recent `AEGIS_VERSION` the operator has
 *                    acknowledged via the "Got it" button on the Home surface's
 *                    "What's new" callout. Empty string ("") means "never seen
 *                    anything" — every fresh install shows the callout once.
 *                    When `seenBuild != AEGIS_VERSION` the callout reappears,
 *                    so bumping the constant in `AegisVersion.kt` re-notifies
 *                    every operator on next Home load. Operator-only surface;
 *                    the customer journey never reads this.
 *   recentCommandIds — most-recently-invoked command-palette ids (most recent
 *                    first, capped at 5 by the caller). The palette surfaces
 *                    these under a "Recent" section header when the search
 *                    query is empty, then shows the rest of the catalog
 *                    underneath. Strictly per-device — we deliberately don't
 *                    roam this to the server; recents are a UI affordance,
 *                    not part of the operator's identity.
 *   pinnedPlanIds  — set of `Plan.id`s the operator has starred via the
 *                    pin button on the Plan Configurator PlanCard / the
 *                    Product Catalog drawer. Surfaces as a "Pinned plans"
 *                    KPI tile on HomeSurface. Per-device, never roamed to
 *                    the server — pinning is a personal navigation
 *                    affordance, not part of the plan record. No cap on
 *                    how many plans can be pinned.
 *
 * The settings record is intentionally narrow.
 */
@Serializable
data class AegisSettings(
    val serverBaseUrl: String = "http://localhost:9090",
    val defaultRole: String = "BUSINESS",
    val theme: String = "light",
    val operatorIdentity: String = "",
    val locale: String = "en",
    val mutedNotificationActions: Set<String> = emptySet(),
    val localeAutoSeeded: Boolean = false,
    val seenBuild: String = "",
    val recentCommandIds: List<String> = emptyList(),
    val pinnedPlanIds: Set<String> = emptySet(),
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
