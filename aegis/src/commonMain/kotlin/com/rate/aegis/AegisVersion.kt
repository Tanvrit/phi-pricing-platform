package com.rate.aegis

/**
 * Aegis build tag — hand-bumped string identifying the running shell build.
 *
 * The Home surface's "What's new" callout compares this against the operator's
 * persisted [com.rate.aegis.settings.AegisSettings.seenBuild]; bumping this
 * constant in source re-shows the callout to every operator the next time they
 * land on Home. The Settings "System info" card also surfaces this value
 * verbatim for bug reports.
 *
 * Format is YYYY.MM.dev for the dev stream; a real release pipeline (Phase 3)
 * will replace this with a generated `BuildConfig.kt` populated from the
 * git tag and CI build number.
 */
const val AEGIS_VERSION: String = "2026.05.dev"

/**
 * Highlights shown in the "What's new" callout on Home when
 * `AegisSettings.seenBuild != AEGIS_VERSION`. Kept short — one bullet per
 * shipped feature, in roughly the order they became visible to operators.
 *
 * Strictly an operator-facing changelog. Each string is rendered verbatim with
 * a leading "• " in the Home callout; do not add Markdown — the Text composable
 * doesn't parse it.
 */
val AEGIS_RELEASE_NOTES: List<String> = listOf(
    "⌘K command palette — jump to any surface, plan, quote, cover, or discount.",
    "Live audit stream — bell-icon NotificationCenter shows new events as they land.",
    "Customer save+resume via ?session= URL; shareable quotes via ?quote=.",
    "Reports surface — bucketed time series, distributions, customer-journey funnel, CSV export.",
    "RBAC operator allowlist (~/.aegis/operators.json) + X-Aegis-Actor header attribution.",
    "Hindi i18n on the buyonline journey; auto-detected from browser locale.",
    "Filesystem email outbox (~/.aegis/outbox/) — Phase-1 path for SMTP integration.",
    "Dark-mode theme + persistent operator identity + Settings system-info card.",
)
