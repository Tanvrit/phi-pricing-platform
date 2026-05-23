package com.rate.aegis.surfaces.uw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisChip
import com.rate.aegis.components.AegisChipTone
import com.rate.aegis.components.AegisColumn
import com.rate.aegis.components.AegisEmptyState
import com.rate.aegis.components.AegisHDivider
import com.rate.aegis.components.AegisStatus
import com.rate.aegis.components.AegisStatusPill
import com.rate.aegis.components.AegisTable
import com.rate.aegis.components.AegisDrawer
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.DashboardSource
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.data.rememberDashboardData
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.domain.money.formatRupees

/**
 * UW Queue — Phase 1 synthetic underwriting review surface.
 *
 * Auto-derives an "underwriter would want to look at this" queue from the
 * existing quote stream rather than waiting on a real `uw_review` table. The
 * three signals we *can* compute from `FakeQuote` today:
 *
 *  - INVALID       — engine rejected the quote (`isValid == false`).
 *  - SENIOR        — primary age ≥ 60 (medical-review actuarial convention).
 *  - HIGH_SI       — sum insured ≥ ₹50L (financial-underwriting tier 2).
 *  - GLOBAL        — plan-type starts with `GLOBAL` (policy-form review for
 *                    cross-border medical providers).
 *
 * TODOs the projection doesn't yet expose (so we skip them this iteration):
 *  - **UW loading applied** lives on `QuoteRequest.uwLoadingFactor`, not on
 *    the `FakeQuote` projection — surfacing it needs a new column on the
 *    server's QuoteListItem (Phase 4 along with the real review table).
 *  - **Discount stacking** (rows that consumed >25% of `maxDiscountCap`) —
 *    same story; the projection doesn't carry per-discount lines.
 *
 * Disposition controls ("Approve / Counter / Decline") are rendered as
 * disabled buttons in the drawer with a Phase-4 callout — they're the
 * affordance shape so the wiring point is obvious, but they can't fire
 * because there's no UW workflow table yet.
 */
@Composable
fun UwQueueSurface() {
    val dashboard by rememberDashboardData()
    val all = dashboard.quotes

    // Compute flags once per dataset; ChipFilter changes only re-filter the cache.
    val flagged = remember(all) {
        all.mapNotNull { q ->
            val flags = classifyFlags(q)
            if (flags.isEmpty()) null else FlaggedQuote(q, flags)
        }
    }

    var filter by remember { mutableStateOf<FlagFilter>(FlagFilter.All) }
    var selected by remember { mutableStateOf<FlaggedQuote?>(null) }

    val visible = remember(flagged, filter) {
        when (filter) {
            FlagFilter.All     -> flagged
            FlagFilter.Invalid -> flagged.filter { UwFlag.Invalid in it.flags }
            FlagFilter.Senior  -> flagged.filter { UwFlag.Senior  in it.flags }
            FlagFilter.HighSi  -> flagged.filter { UwFlag.HighSi  in it.flags }
            FlagFilter.Global  -> flagged.filter { UwFlag.Global  in it.flags }
        }
    }

    val countInvalid = flagged.count { UwFlag.Invalid in it.flags }
    val countSenior  = flagged.count { UwFlag.Senior  in it.flags }
    val countHighSi  = flagged.count { UwFlag.HighSi  in it.flags }
    val countGlobal  = flagged.count { UwFlag.Global  in it.flags }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text(
            "UW Queue",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody
        )
        Text(
            "Quotes flagged for underwriter review. Auto-derived from the engine " +
                    "output; explicit UW workflow lands in Phase 4.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary
        )

        UwSourceBanner(
            source = dashboard.source,
            reason = dashboard.fallbackReason,
            refreshedAt = dashboard.refreshedAt,
            totalQuotes = all.size,
            flaggedCount = flagged.size
        )

        // KPI strip ─────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            UwKpiTile(
                label = "Total flagged",
                value = flagged.size,
                accent = AegisColors.indigo500,
                modifier = Modifier.weight(1f)
            )
            UwKpiTile(
                label = "Invalid",
                value = countInvalid,
                accent = AegisColors.danger500,
                modifier = Modifier.weight(1f)
            )
            UwKpiTile(
                label = "Senior (≥ 60)",
                value = countSenior,
                accent = AegisColors.warn500,
                modifier = Modifier.weight(1f)
            )
            UwKpiTile(
                label = "High SI (≥ ₹50L)",
                value = countHighSi,
                accent = AegisColors.info500,
                modifier = Modifier.weight(1f)
            )
            UwKpiTile(
                label = "Global plans",
                value = countGlobal,
                accent = AegisColors.premium500,
                modifier = Modifier.weight(1f)
            )
        }

        // Filter chips ──────────────────────────────────────────────────────
        AegisCard {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Text(
                    "Filter:",
                    fontSize = 13.sp,
                    color = AegisColors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                FlagFilter.entries.forEach { f ->
                    AegisChip(
                        label = f.label,
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }
        }

        // Queue table ───────────────────────────────────────────────────────
        AegisCard {
            AegisTable(
                items = visible,
                onRowClick = { selected = it },
                rowKey = { it.quote.id },
                columns = listOf(
                    AegisColumn<FlaggedQuote>(
                        header = "Quote ID", weight = 1.7f, mono = true,
                        cell = { Text(it.quote.id, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Created", weight = 0.9f,
                        cell = {
                            Text(it.quote.createdAt, fontSize = 13.sp,
                                color = AegisColors.textSecondary)
                        }
                    ),
                    AegisColumn(
                        header = "Plan", weight = 1.5f,
                        cell = { Text(it.quote.planName, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Age", weight = 0.4f, align = TextAlign.End,
                        cell = { Text(it.quote.primaryAge.toString(), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "SI", weight = 0.9f, align = TextAlign.End, mono = true,
                        cell = {
                            Text(
                                formatRupees(it.quote.sumInsured.toDouble()),
                                fontSize = 13.sp
                            )
                        }
                    ),
                    AegisColumn(
                        header = "Flags", weight = 2.0f,
                        cell = { fq ->
                            // Render every flag as its own coloured chip. Wraps
                            // visually as a Row but stays on one line per row
                            // in practice because no quote has >4 flags.
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                fq.flags.forEach { flag ->
                                    AegisChip(
                                        label = flag.label,
                                        tone = flag.chipTone
                                    )
                                }
                            }
                        }
                    ),
                    AegisColumn(
                        header = "Status", weight = 0.8f,
                        cell = { fq ->
                            if (UwFlag.Invalid in fq.flags) AegisStatusPill(AegisStatus.Rejected)
                            else AegisStatusPill(AegisStatus.InReview)
                        }
                    ),
                    AegisColumn(
                        header = "Total", weight = 1.1f,
                        align = TextAlign.End, mono = true,
                        cell = { fq ->
                            if (fq.quote.isValid) {
                                Text(
                                    formatRupees(fq.quote.totalIncludingGst),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text("—", fontSize = 13.sp, color = AegisColors.textSecondary)
                            }
                        }
                    ),
                ),
                emptyState = {
                    AegisEmptyState(
                        title = "No quotes match this filter",
                        helper = "Switch the chip above to widen the queue."
                    )
                }
            )
        }
    }

    // Drawer ────────────────────────────────────────────────────────────────
    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.quote?.id ?: "UW review",
        subtitle = selected?.quote?.planName,
    ) {
        selected?.let { fq ->
            Column(
                Modifier.padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
            ) {
                Text(
                    "Flag breakdown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textPrimary
                )
                fq.flags.forEach { flag ->
                    FlagExplanationRow(flag, fq.quote)
                }

                AegisHDivider()

                Text(
                    "Quote summary",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textPrimary
                )
                DrawerLine("Quote ID", fq.quote.id)
                DrawerLine("Created", fq.quote.createdAt)
                DrawerLine("Plan", fq.quote.planName)
                DrawerLine("Primary age", fq.quote.primaryAge.toString())
                DrawerLine("Family type", fq.quote.familyType)
                DrawerLine("Zone", fq.quote.zone)
                DrawerLine("Tenure", fq.quote.tenureLabel)
                DrawerLine("Sum insured", formatRupees(fq.quote.sumInsured.toDouble()))
                if (fq.quote.isValid) {
                    DrawerLine(
                        "Total (incl. GST)",
                        formatRupees(fq.quote.totalIncludingGst),
                        bold = true
                    )
                } else {
                    DrawerLine("Total (incl. GST)", "—")
                }

                AegisHDivider()

                Text(
                    "Disposition",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textPrimary
                )
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Phase 4 — real UW workflow",
                    body = "These actions are placeholders. The real underwriter " +
                            "disposition table (with audit trail, SLA timer and " +
                            "counter-offer notes) lands in Phase 4 alongside the " +
                            "`uw_review` projection on the server."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(
                        label = "Approve",
                        onClick = {},
                        enabled = false,
                        variant = AegisButtonVariant.Primary
                    )
                    AegisButton(
                        label = "Counter-offer",
                        onClick = {},
                        enabled = false,
                        variant = AegisButtonVariant.Secondary
                    )
                    AegisButton(
                        label = "Decline",
                        onClick = {},
                        enabled = false,
                        variant = AegisButtonVariant.Danger
                    )
                }
                Spacer(Modifier.height(AegisSpacing.s2))
            }
        }
    }
}

// ── Flag model ──────────────────────────────────────────────────────────────

/** A single underwriting-review signal we can derive from a `FakeQuote`. */
private enum class UwFlag(val label: String, val chipTone: AegisChipTone) {
    Invalid("Invalid", AegisChipTone.Danger),
    Senior ("Senior",  AegisChipTone.Warn),
    HighSi ("High SI", AegisChipTone.Info),
    Global ("Global",  AegisChipTone.Neutral),
}

private data class FlaggedQuote(
    val quote: FakeAegisRepo.FakeQuote,
    val flags: List<UwFlag>,
)

/**
 * Apply every UW signal we can compute from the projection. Order matters
 * for display: Invalid first (most severe), then Senior / HighSi / Global.
 *
 * NOTE: we deliberately skip the "UW loading applied" signal — it lives on
 * `QuoteRequest.uwLoadingFactor`, not on `FakeQuote`, so wiring it needs a
 * new column on the server's QuoteListItem (Phase 4).
 */
private fun classifyFlags(q: FakeAegisRepo.FakeQuote): List<UwFlag> {
    val out = mutableListOf<UwFlag>()
    if (!q.isValid)                         out += UwFlag.Invalid
    if (q.primaryAge >= 60)                 out += UwFlag.Senior
    if (q.sumInsured >= 5_000_000L)         out += UwFlag.HighSi
    if (q.planId.startsWith("PHI_GLOBAL"))  out += UwFlag.Global
    return out
}

private enum class FlagFilter(val label: String) {
    All("All"),
    Invalid("Invalid"),
    Senior("Senior"),
    HighSi("High SI"),
    Global("Global"),
}

// ── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun UwKpiTile(
    label: String,
    value: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val dim = value == 0
    val valueColor = if (dim) AegisColors.textTertiary else AegisColors.textBody
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(width = 6.dp, height = 14.dp)
                        .background(
                            if (dim) AegisColors.slate4 else accent,
                            RoundedCornerShape(2.dp)
                        )
                )
                Text(
                    label,
                    fontSize = 12.sp,
                    color = AegisColors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                value.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun UwSourceBanner(
    source: DashboardSource,
    reason: String,
    refreshedAt: String,
    totalQuotes: Int,
    flaggedCount: Int,
) {
    val refreshLabel = refreshedAt.take(19).replace('T', ' ')
        .let { if (it.isNotBlank()) " · refreshed $it UTC" else "" }
    when (source) {
        DashboardSource.LOADING -> AegisCallout(
            kind = CalloutKind.INFO,
            title = "Loading…",
            body = "Pulling the quote stream from the server."
        )
        DashboardSource.LIVE -> AegisCallout(
            kind = CalloutKind.SUCCESS,
            title = "Live data",
            body = "$flaggedCount of $totalQuotes quotes need review." + refreshLabel
        )
        DashboardSource.DEMO -> AegisCallout(
            kind = CalloutKind.WARN,
            title = "Demo data",
            body = (reason.ifBlank { "Server unreachable — showing a synthetic dataset." }) +
                    " $flaggedCount of $totalQuotes quotes flagged." + refreshLabel
        )
    }
}

@Composable
private fun FlagExplanationRow(flag: UwFlag, q: FakeAegisRepo.FakeQuote) {
    val explanation = when (flag) {
        UwFlag.Invalid -> "Engine returned isValid=false — likely a plan-rule violation " +
                "(age band, SI grid, or family-type mismatch)."
        UwFlag.Senior  -> "Primary life is ${q.primaryAge} — medical underwriting " +
                "is mandatory for ages 60+."
        UwFlag.HighSi  -> "Sum insured of ${formatRupees(q.sumInsured.toDouble())} " +
                "exceeds the ₹50L financial-UW tier-1 ceiling."
        UwFlag.Global  -> "Plan ${q.planName} ships with cross-border providers — " +
                "policy form needs UW sign-off."
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)
    ) {
        AegisChip(label = flag.label, tone = flag.chipTone)
        Text(
            explanation,
            fontSize = 13.sp,
            color = AegisColors.textBody,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrawerLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = AegisColors.textBody
        )
    }
}
