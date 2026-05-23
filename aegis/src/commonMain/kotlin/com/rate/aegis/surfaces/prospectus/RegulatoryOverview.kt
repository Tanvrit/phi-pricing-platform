@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.rate.aegis.surfaces.prospectus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisHDivider
import com.rate.aegis.components.AegisStatus
import com.rate.aegis.components.AegisStatusPill
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanLifecycle

/**
 * Regulatory overview — shell-wide IRDAI dashboard.
 *
 * Read-only operator-side surface that consolidates the IRDAI-related
 * artefacts that don't fit neatly into a per-plan prospectus: roll-up of UIN
 * registration status across the catalogue, the audit retention policy,
 * PII handling summary, PRUHealth-wide free-look / grace / claim SLAs, and
 * regulator contact info.
 *
 * Phase-1 scope
 *  - No new server endpoint. Reuses the [Plan] list already loaded by the
 *    parent [ProspectusSurface] and looks UINs up via [FakeAegisRepo.metaFor]
 *    — same data source as the per-plan document.
 *  - Static boilerplate for SLAs / contacts (mirrors §§6-10 of the per-plan
 *    Prospectus, since these are product-family defaults).
 *  - Right-to-erasure mention is forward-looking, see §"PII handling" — Phase 2.
 *
 * Not in scope
 *  - Actual IRDAI filing automation
 *  - PII redaction enforcement (server-side only)
 *  - Opening external docs programmatically (link is printed — keeps the
 *    surface KMP-portable across JVM / wasmJs)
 */
@Composable
internal fun RegulatoryOverview(
    plans: List<Plan>,
    loaded: Boolean,
    loadError: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Regulatory overview",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textPrimary,
        )
        Text(
            "Shell-wide consolidation of IRDAI artefacts — UIN registration roll-up, " +
                    "audit retention, PII handling, PRUHealth-wide SLAs, and regulator contacts. " +
                    "Read-only.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        ComplianceStatusCard(plans = plans, loaded = loaded, loadError = loadError)
        AuditRetentionCard()
        PiiHandlingCard()
        SlaTableCard()
        RegulatorContactsCard()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §1 IRDAI compliance status — per-plan UIN roll-up
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComplianceStatusCard(plans: List<Plan>, loaded: Boolean, loadError: String?) {
    AegisCard(
        title = "IRDAI compliance status",
        subtitle = "UIN registration roll-up across the active plan catalogue",
    ) {
        when {
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Server unreachable",
                body = "Could not load plans: $loadError. Compliance roll-up needs the live " +
                        "plan envelope.",
            )
            !loaded -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Fetching the plan catalogue.",
            )
            plans.isEmpty() -> AegisCallout(
                kind = CalloutKind.WARN,
                title = "No plans on file",
                body = "No plans returned by the server — nothing to roll up.",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                // Roll-up counts at the top
                val rows = plans.map { p ->
                    val meta = FakeAegisRepo.metaFor(p.id)
                    val uinPending = meta.uin.contains("pending", ignoreCase = true)
                    Triple(p, meta.uin, uinPending)
                }
                val live = rows.count { !it.third }
                val pending = rows.size - live
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                ) {
                    SummaryStat("Plans", "${plans.size}")
                    SummaryStat("UIN filed", "$live")
                    SummaryStat("UIN pending", "$pending")
                }

                AegisHDivider()

                // Table-style header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Plan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                        modifier = Modifier.weight(1.4f),
                    )
                    Text(
                        "UIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                        modifier = Modifier.weight(1.6f),
                    )
                    Text(
                        "Status",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                        modifier = Modifier.weight(0.6f),
                    )
                }

                rows.forEach { (plan, uin, uinPending) ->
                    ComplianceRow(plan = plan, uin = uin, uinPending = uinPending)
                }
            }
        }
    }
}

@Composable
private fun ComplianceRow(plan: Plan, uin: String, uinPending: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.4f)) {
            Text(
                plan.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AegisColors.textBody,
            )
            Text(
                plan.id,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AegisColors.textSecondary,
            )
        }
        Text(
            if (uinPending) "Pending registration" else uin.removePrefix("UIN: "),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (uinPending) AegisColors.textSecondary else AegisColors.textBody,
            modifier = Modifier.weight(1.6f),
        )
        Box(Modifier.weight(0.6f)) {
            // Map lifecycle into a Live/Draft/Retired pill. Plans without a UIN
            // get a "Pending" indication by virtue of being DRAFT in the meta
            // (see FakeAegisRepo.metaFor fallback), so the lifecycle pill is
            // the right signal here.
            AegisStatusPill(plan.lifecycle.toAegisStatus())
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(AegisColors.slate2, AegisRadii.shapeMd)
            .border(1.dp, AegisColors.slate3, AegisRadii.shapeMd)
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = AegisColors.textSecondary,
        )
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textPrimary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §2 Audit retention
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuditRetentionCard() {
    AegisCard(
        title = "Audit retention",
        subtitle = "Operator action log + quote lineage retention policy",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(
                "PRUHealth retains the full operator audit ledger (rate-card edits, plan " +
                        "lifecycle transitions, configurator changes) and the per-quote pricing " +
                        "lineage for a minimum of 10 financial years from the date of the " +
                        "underlying policy's expiry, in accordance with the IRDAI (Maintenance of " +
                        "Insurance Records) Regulations, 2015 and the Companies Act, 2013 §128(5). " +
                        "Ledger entries are append-only and reconciled nightly against the " +
                        "server-side audit stream. PII fields in the ledger payloads are redacted " +
                        "at write time — see PII handling below.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AegisColors.textBody,
            )
            // Print the link rather than open it — keeps the surface portable
            // across JVM (Compose Desktop) and wasmJs without an expect/actual
            // browser-open shim.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "View full policy:",
                    fontSize = 12.sp,
                    color = AegisColors.textSecondary,
                )
                Box(Modifier.width(AegisSpacing.s2))
                Text(
                    "docs/audit-retention.md",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.indigo700,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §3 PII handling
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PiiHandlingCard() {
    AegisCard(
        title = "PII handling",
        subtitle = "Server-side redaction + DPDPA / GDPR posture",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(
                "PRUHealth redacts personally-identifiable fields server-side before they " +
                        "are persisted to the audit ledger or surfaced to operator dashboards. " +
                        "Redacted attributes include: full name (truncated to initials), PAN " +
                        "(masked to last four), Aadhaar (replaced with hash digest), mobile " +
                        "(masked to last four), date of birth (truncated to year), email " +
                        "(local-part masked), and bank account numbers (replaced with last-four " +
                        "tokens). Quote payloads referenced from this shell carry only the " +
                        "tokenised view — operators never see raw PII.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AegisColors.textBody,
            )
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "Right to erasure — Phase 2",
                body = "Customer-initiated DPDPA (Digital Personal Data Protection Act, " +
                        "2023) §12 / GDPR Article 17 right-to-erasure flows are scheduled for " +
                        "Phase 2: the request inbox, identity-verification step, and the " +
                        "cascading scrub across audit stream + quote ledger + KYC vault are " +
                        "tracked under DASHBOARD_REDESIGN_PLAN.md §Phase 2 — Erasure.",
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §4 Free-look + grace + claim SLAs — PRUHealth-wide defaults
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlaTableCard() {
    AegisCard(
        title = "Free-look + grace + claim SLAs",
        subtitle = "PRUHealth-wide defaults — mirrored per-plan in the Prospectus document",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            SlaRow(
                clause = "Free-look period",
                value = "15 days",
                ref = "IRDAI (Health Insurance) Regulations, 2016",
            )
            SlaRow(
                clause = "Grace period (renewal)",
                value = "30 days",
                ref = "Continuity preserved if renewed within grace",
            )
            SlaRow(
                clause = "Claim intimation",
                value = "≤ 24 hours (emergency) / ≥ 48 hours advance (planned)",
                ref = "From admission / before admission respectively",
            )
            SlaRow(
                clause = "Document submission window",
                value = "15 days post-discharge",
                ref = "Discharge summary, bills, prescriptions, reports",
            )
            SlaRow(
                clause = "Claim settlement / repudiation",
                value = "30 days from last document",
                ref = "IRDAI mandated turnaround",
            )
            SlaRow(
                clause = "Initial waiting period",
                value = "30 days from inception",
                ref = "Excludes accident-induced claims",
            )
            SlaRow(
                clause = "Specified diseases waiting",
                value = "24 months",
                ref = "Listed in policy schedule",
            )
            SlaRow(
                clause = "Pre-existing disease waiting",
                value = "36 months",
                ref = "Portability credit applies per IRDAI rules",
            )
            SlaRow(
                clause = "Portability request",
                value = "≥ 45 days before renewal",
                ref = "IRDAI (Portability of Health Insurance Policies)",
            )
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "Per-plan overrides pending",
                body = "These are the PRUHealth defaults reproduced in §§5-9 of every " +
                        "Prospectus document. Per-plan overrides (e.g. shorter PED waiting " +
                        "on the Flagship tier) will be lifted from the audit ledger in Phase 2.",
            )
        }
    }
}

@Composable
private fun SlaRow(clause: String, value: String, ref: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1.2f)) {
            Text(
                clause,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AegisColors.textBody,
            )
            Text(
                ref,
                fontSize = 11.sp,
                color = AegisColors.textSecondary,
            )
        }
        Box(Modifier.width(AegisSpacing.s3))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// §5 Regulator contacts — placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegulatorContactsCard() {
    AegisCard(
        title = "Regulator contacts",
        subtitle = "IRDAI helpline + Integrated Grievance Management System",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            ContactRow("IRDAI toll-free helpline", "155255 / 1800 4254 732")
            ContactRow("IRDAI IGMS portal", "igms.irda.gov.in")
            ContactRow("IRDAI head office", "Hyderabad — Survey No. 115/1, Financial District")
            ContactRow("IRDAI website", "irdai.gov.in")
            AegisHDivider()
            ContactRow("PRUHealth grievance redressal", "grievance@pruhealth.example.in")
            ContactRow("PRUHealth toll-free", "1800 200 7747")
            Text(
                "Placeholder data — production contact details will be sourced from the " +
                        "insurer configuration sheet once the multi-insurer plumbing lands.",
                fontSize = 11.sp,
                color = AegisColors.textSecondary,
                modifier = Modifier.padding(top = AegisSpacing.s1),
            )
        }
    }
}

@Composable
private fun ContactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = AegisColors.textBody,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun PlanLifecycle.toAegisStatus(): AegisStatus = when (this) {
    PlanLifecycle.LIVE -> AegisStatus.Live
    PlanLifecycle.DRAFT -> AegisStatus.Draft
    PlanLifecycle.RETIRED -> AegisStatus.Retired
}
