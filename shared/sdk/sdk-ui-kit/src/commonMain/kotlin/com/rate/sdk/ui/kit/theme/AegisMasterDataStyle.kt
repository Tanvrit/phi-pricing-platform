package com.rate.sdk.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisStatus

/**
 * Master-data style-guide constants — the shared vocabulary for the admin / master-data hubs.
 *
 * The hubs (ConfigHubSurface) and the per-entity config screen render MANY heterogeneous entities,
 * so the visual grammar (which colour a status pill gets, how a count label is phrased, the
 * importance ordering of badge tones) MUST be defined once and consumed everywhere. This object is
 * that single source of truth; nothing here hardcodes a hex value (it composes the existing
 * [AegisColors] tokens) so it stays light/dark correct.
 *
 * Three concerns:
 *  - [statusPill] / [statusTone] — map a raw config-entity status string (+ soft-delete flag) to the
 *    canonical [AegisStatus] pill and a matching [AegisBadgeTone], so every surface paints the same
 *    state the same colour.
 *  - [countLabel] / [showingRange] — grammar-correct, locale-neutral count/range labels
 *    ("3 plans", "1 cover", "Showing 1-50 of 312") used by hub cards and list footers.
 *  - [badgeRank] — the importance hierarchy of badge tones, so a card showing several signals can
 *    surface the most urgent one first.
 */
object AegisMasterDataStyle {

    // ── Status → pill ───────────────────────────────────────────────────────
    /**
     * Canonical mapping of a [com.rate.core.base.model.EntityStatus] name (DRAFT/PUBLISHED/RETIRED)
     * plus the soft-delete flag onto the kit's [AegisStatus] pill. Soft-deleted always reads as
     * RETIRED regardless of lifecycle status. Unknown statuses fall back to a neutral DRAFT pill so
     * a forward-compatible server enum never crashes the grid.
     */
    fun statusPill(status: String, deleted: Boolean = false): AegisStatus = when {
        deleted -> AegisStatus.Retired
        status.equals("DRAFT", ignoreCase = true) -> AegisStatus.Draft
        status.equals("RETIRED", ignoreCase = true) -> AegisStatus.Retired
        status.equals("PUBLISHED", ignoreCase = true) -> AegisStatus.Live
        // Forward-compat lifecycle names sometimes surfaced by read-mostly entities.
        status.equals("IN_REVIEW", ignoreCase = true) -> AegisStatus.InReview
        status.equals("SCHEDULED", ignoreCase = true) -> AegisStatus.Scheduled
        status.equals("APPROVED", ignoreCase = true) -> AegisStatus.Approved
        status.equals("REJECTED", ignoreCase = true) ||
            status.equals("BLOCKED", ignoreCase = true) ||
            status.equals("SUSPENDED", ignoreCase = true) -> AegisStatus.Rejected
        status.equals("ACTIVE", ignoreCase = true) ||
            status.equals("LIVE", ignoreCase = true) -> AegisStatus.Live
        else -> AegisStatus.Draft
    }

    /** The [AegisBadgeTone] that matches a given lifecycle [AegisStatus] (for non-pill badges). */
    fun statusTone(status: AegisStatus): AegisBadgeTone = when (status) {
        AegisStatus.Draft -> AegisBadgeTone.Neutral
        AegisStatus.InReview -> AegisBadgeTone.Warn
        AegisStatus.Scheduled -> AegisBadgeTone.Info
        AegisStatus.Approved -> AegisBadgeTone.Success
        AegisStatus.Live -> AegisBadgeTone.Success
        AegisStatus.Retired -> AegisBadgeTone.Neutral
        AegisStatus.Rejected -> AegisBadgeTone.Danger
    }

    /** Convenience: raw status string + delete flag → badge tone in one hop. */
    fun statusTone(status: String, deleted: Boolean = false): AegisBadgeTone =
        statusTone(statusPill(status, deleted))

    /** Resolved fill colour for a status (composes [AegisColors]; use for dots/accents, not pills). */
    @Composable
    fun statusColor(status: AegisStatus): Color = when (status) {
        AegisStatus.Draft -> AegisColors.slate6
        AegisStatus.InReview -> AegisColors.warn500
        AegisStatus.Scheduled -> AegisColors.info500
        AegisStatus.Approved -> AegisColors.success500
        AegisStatus.Live -> AegisColors.success500
        AegisStatus.Retired -> AegisColors.slate6
        AegisStatus.Rejected -> AegisColors.danger500
    }

    // ── Badge importance hierarchy ──────────────────────────────────────────
    /**
     * Importance rank of a badge tone (0 = most urgent). When a card carries several signals
     * (e.g. a count badge + a "3 drafts" warning + an error), sort by this rank and surface the
     * most urgent first so the operator's eye lands on what matters.
     */
    fun badgeRank(tone: AegisBadgeTone): Int = when (tone) {
        AegisBadgeTone.Danger -> 0
        AegisBadgeTone.Warn -> 1
        AegisBadgeTone.Brand -> 2
        AegisBadgeTone.Info -> 3
        AegisBadgeTone.Success -> 4
        AegisBadgeTone.Neutral -> 5
    }

    /** The badge tones ordered most-urgent → least, for callers that iterate the hierarchy. */
    val badgeHierarchy: List<AegisBadgeTone> = AegisBadgeTone.entries.sortedBy { badgeRank(it) }

    // ── Count / range labels (grammar-correct) ──────────────────────────────
    /**
     * Pluralise [noun] for [count] using a naive English rule, honouring an explicit [plural]
     * override for irregulars ("entry" → "entries", "annexure" → "annexures"). Pure + allocation-light.
     */
    fun pluralize(count: Int, noun: String, plural: String? = null): String =
        if (count == 1) noun else (plural ?: defaultPlural(noun))

    /** "0 plans", "1 plan", "12 plans" — count + correctly-pluralised noun. */
    fun countLabel(count: Int, singular: String, plural: String? = null): String =
        "$count ${pluralize(count, singular, plural)}"

    /**
     * "Showing 1-50 of 312" / "Showing 1 of 1" / "No covers" — the canonical list/grid footer.
     *
     * [page] is zero-based; [pageSize] the page window; [total] the full (possibly filtered) count.
     * Computes the inclusive 1-based [from]-[to] window, clamps to [total], and falls back to a
     * grammar-correct "No <plural>" when empty. When everything fits one page it omits the range
     * and reads "Showing N <noun>".
     */
    fun showingRange(
        page: Int,
        pageSize: Int,
        total: Long,
        singular: String,
        plural: String? = null,
    ): String {
        if (total <= 0L) return "No ${plural ?: defaultPlural(singular)}"
        val pluralNoun = plural ?: defaultPlural(singular)
        val from = page.toLong() * pageSize + 1
        val to = minOf((page + 1).toLong() * pageSize, total)
        // Single short page: don't bother with a 1-N range.
        if (from == 1L && to == total) {
            return if (total == 1L) "Showing 1 $singular" else "Showing $total $pluralNoun"
        }
        return "Showing $from-$to of $total $pluralNoun"
    }

    /**
     * Naive English pluralisation covering the suffixes our entity nouns actually use. Not a
     * linguistics engine — descriptors that need an irregular form pass an explicit `plural`.
     */
    private fun defaultPlural(noun: String): String {
        if (noun.isEmpty()) return noun
        val lower = noun.lowercase()
        return when {
            lower.endsWith("y") && noun.length > 1 && noun[noun.length - 2].lowercaseChar() !in "aeiou" ->
                noun.dropLast(1) + "ies"
            lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") ||
                lower.endsWith("ch") || lower.endsWith("sh") -> noun + "es"
            else -> noun + "s"
        }
    }
}
