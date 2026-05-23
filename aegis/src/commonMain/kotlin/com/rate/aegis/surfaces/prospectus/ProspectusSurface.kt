@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.rate.aegis.surfaces.prospectus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisChip
import com.rate.aegis.components.AegisChipTone
import com.rate.aegis.components.AegisHDivider
import com.rate.aegis.components.AegisInput
import com.rate.aegis.components.AegisStatus
import com.rate.aegis.components.AegisStatusPill
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.data.PlanMeta
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanLifecycle
import com.rate.domain.money.formatRupees
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * IRDAI Prospectus — BUSINESS-shell surface (parallel iteration i).
 *
 * Renders a formal, printable disclosure document per plan, approximating the
 * structure mandated by IRDAI for retail health insurance products. Phase-1
 * scope: present the data the engine already knows (plan envelope, eligibility,
 * allowed covers, GST rate) plus a body of static IRDAI boilerplate (waiting
 * periods, free-look, grace, claims, portability, grievance).
 *
 * The document is intentionally dense and copy-pasteable — Phase-2 will add a
 * print-stylesheet wrapper and a real PDF export. For now, "printable" just
 * means the rendering is structured cleanly enough that an operator can paste
 * it into a Word doc for IRDAI filing.
 *
 * Data sources
 *  - `ApiClient.getPlans` — live plan envelope (fields, eligibility, covers).
 *  - [FakeAegisRepo.metaFor] — UIN + lifecycle metadata, until those fields
 *    move onto the domain `Plan`. Plans missing from the meta map show "UIN
 *    pending registration".
 *  - [CoverCatalog.findById] — display name lookup for `allowedCoverIds`.
 *
 * What this is NOT
 *  - A real PDF generator (no `window.print()`, no JVM-only PDF libs).
 *  - A regulatory-complete prospectus (claim repudiation grounds, portability
 *    fine print, exclusions list are all placeholder boilerplate — TODO Phase 2).
 *  - A round-trippable editor — read-only render only.
 */
@Composable
fun ProspectusSurface() {
    val client = rememberApiClient()
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Server-rendered HTML download state. The Compose document below remains
    // the primary view; this is a secondary path for sharing/email/PDF-print.
    @Suppress("UNUSED_VARIABLE")
    var htmlState by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadSuccess by remember { mutableStateOf<String?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss the success/error toast after a few seconds so the document
    // chrome doesn't accumulate banners as the operator clicks around.
    LaunchedEffect(downloadSuccess, downloadError) {
        if (downloadSuccess != null || downloadError != null) {
            delay(3500)
            downloadSuccess = null
            downloadError = null
        }
    }

    LaunchedEffect(client) {
        runCatching { client.getPlans() }
            .onSuccess {
                plans = it
                loadError = null
                loaded = true
                if (selectedId == null && it.isNotEmpty()) selectedId = it.first().id
            }
            .onFailure { t ->
                loadError = t.message ?: t::class.simpleName ?: "unknown error"
                loaded = true
            }
    }

    val selectedPlan = plans.firstOrNull { it.id == selectedId }
    val docDate = remember { todayIsoDate() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Prospectus",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "IRDAI-mandated product disclosure document. Select a plan to render its prospectus.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        // ── LOADING / LIVE / ERROR banner ────────────────────────────────────
        when {
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Server unreachable",
                body = "Could not load plans: $loadError. The prospectus needs the live " +
                        "plan envelope from the server to render eligibility and cover " +
                        "scope sections.",
            )
            !loaded -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Fetching the plan catalogue from the server.",
            )
            else -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "${plans.size} plans available. Picking one below renders its full " +
                        "Phase-1 prospectus document.",
            )
        }

        // ── Plan picker ──────────────────────────────────────────────────────
        if (plans.isNotEmpty()) {
            PlanPicker(
                plans = plans,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
            )
        }

        // ── Prospectus body ──────────────────────────────────────────────────
        if (selectedPlan != null) {
            val meta = FakeAegisRepo.metaFor(selectedPlan.id)
            val uinMissing = meta.uin.contains("pending", ignoreCase = true)

            DocumentHeaderCard(plan = selectedPlan, meta = meta, uinMissing = uinMissing, docDate = docDate)

            // ── Server-rendered HTML download row ───────────────────────────
            // Sits next to the document chrome — same content, different
            // delivery channel (regulator filing, email attachment, browser
            // print-to-PDF). The in-Compose document above is still primary.
            DownloadRow(
                planId = selectedPlan.id,
                downloading = downloading,
                onDownload = {
                    val pid = selectedPlan.id
                    downloadSuccess = null
                    downloadError = null
                    scope.launch {
                        downloading = true
                        runCatching { client.getProspectusHtml(pid) }
                            .onSuccess { html ->
                                htmlState = html
                                openOrSaveProspectus(pid, html)
                                downloadSuccess = pid
                            }
                            .onFailure { t ->
                                downloadError = t.message ?: t::class.simpleName ?: "unknown error"
                            }
                        downloading = false
                    }
                },
            )
            downloadSuccess?.let { pid ->
                AegisCallout(
                    kind = CalloutKind.SUCCESS,
                    title = "Downloaded",
                    body = "Saved as $pid-prospectus.html.",
                )
            }
            downloadError?.let { msg ->
                AegisCallout(
                    kind = CalloutKind.DANGER,
                    title = "Download failed",
                    body = msg,
                )
            }

            if (selectedPlan.lifecycle == PlanLifecycle.DRAFT) {
                AegisCallout(
                    kind = CalloutKind.DANGER,
                    title = "Internal draft — do not distribute",
                    body = "This product is in pre-IRDAI-registration review. The prospectus " +
                            "below is the working draft, not the filed document.",
                )
            }

            ProspectusBody(plan = selectedPlan, meta = meta, uinMissing = uinMissing)

            DocumentFooter(plan = selectedPlan, meta = meta, docDate = docDate)
        } else if (loaded && plans.isNotEmpty()) {
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "No plan selected",
                body = "Pick a plan from the list above to render its prospectus.",
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan picker
// ─────────────────────────────────────────────────────────────────────────────

private const val CHIP_THRESHOLD = 8

@Composable
private fun PlanPicker(
    plans: List<Plan>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    AegisCard(
        title = "Select plan",
        subtitle = "${plans.size} plans — pick one to render its prospectus",
    ) {
        if (plans.size <= CHIP_THRESHOLD) {
            // Chip strip (compact catalogue)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
            ) {
                plans.forEach { p ->
                    AegisChip(
                        label = "${p.id} — ${p.name}",
                        selected = p.id == selectedId,
                        onClick = { onSelect(p.id) },
                    )
                }
            }
        } else {
            // Searchable picker (larger catalogue)
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = "Filter by plan ID or name",
                    helper = "Type any fragment — \"GLOBAL\", \"SENIOR\", \"FLAGSHIP\"…",
                )
                val q = searchQuery.trim()
                val filtered = if (q.isBlank()) plans
                else plans.filter {
                    it.id.contains(q, ignoreCase = true) ||
                            it.name.contains(q, ignoreCase = true)
                }
                Text(
                    "${filtered.size} of ${plans.size} plans match",
                    fontSize = 12.sp,
                    color = AegisColors.textSecondary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                    verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                ) {
                    filtered.forEach { p ->
                        AegisChip(
                            label = "${p.id} — ${p.name}",
                            selected = p.id == selectedId,
                            onClick = { onSelect(p.id) },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Document header — product name, UIN, version, lifecycle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DocumentHeaderCard(plan: Plan, meta: PlanMeta, uinMissing: Boolean, docDate: String) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PRUDENTIAL HEALTH INSURANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        plan.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AegisColors.textPrimary,
                    )
                    Text(
                        "Prospectus — Phase-1 disclosure document",
                        fontSize = 13.sp,
                        color = AegisColors.textSecondary,
                    )
                }
                AegisStatusPill(plan.lifecycle.toAegisStatus())
            }

            AegisHDivider()

            // Identification grid
            HeaderField("Product name", plan.name)
            HeaderField("Plan ID", plan.id, mono = true)
            if (uinMissing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("UIN", fontSize = 13.sp, color = AegisColors.textSecondary)
                    AegisChip(
                        label = "UIN pending registration",
                        tone = AegisChipTone.Warn,
                        selected = false,
                    )
                }
            } else {
                HeaderField("UIN", meta.uin.removePrefix("UIN: "), mono = true)
            }
            HeaderField("Insurer", "PRUHealth (Prudential Health Insurance Co. Ltd.)")
            HeaderField("Product family", plan.planType.displayName)
            HeaderField("Geography", plan.geographyScope.label)
            HeaderField("Document version", "v1.0 (Phase-1 internal)")
            HeaderField("Document date", docDate, mono = true)
            HeaderField("Last modified", "${meta.lastModifiedIso} by ${meta.lastModifiedBy}")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server-rendered HTML download row — secondary delivery channel for the
// prospectus. Sits between the header card and the body so it reads as part
// of the document chrome rather than a floating toolbar action.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DownloadRow(planId: String, downloading: Boolean, onDownload: () -> Unit) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Server-rendered HTML",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textPrimary,
                    )
                    Text(
                        "Browser-printable single-file document. Use File → Print → " +
                                "Save as PDF for an IRDAI-style print.",
                        fontSize = 12.sp,
                        color = AegisColors.textSecondary,
                    )
                }
                Box(Modifier.width(AegisSpacing.s3))
                AegisButton(
                    label = "Download server-rendered HTML",
                    onClick = onDownload,
                    variant = AegisButtonVariant.Secondary,
                    enabled = planId.isNotBlank(),
                    loading = downloading,
                )
            }
            Text(
                "Plan: $planId — file: $planId-prospectus.html",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AegisColors.textSecondary,
            )
        }
    }
}

@Composable
private fun HeaderField(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Body — sections 1..12
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProspectusBody(plan: Plan, meta: PlanMeta, uinMissing: Boolean) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5)) {

            // 1. Cover page is the header card above. Section 2 onwards.

            Section(number = "1", title = "Product overview") {
                Paragraph(
                    plan.description.ifBlank {
                        "No product description on file. Operators should populate `Plan.description` " +
                                "in the Plan Configurator before this prospectus is filed with IRDAI."
                    },
                )
            }
            AegisHDivider()

            Section(number = "2", title = "Scope of cover") {
                val coverIds = plan.allowedCoverIds
                if (coverIds.isEmpty()) {
                    Paragraph(
                        "This plan permits the full Prudential Health cover catalogue " +
                                "(${CoverCatalog.ALL.size} optional benefits, ${CoverCatalog.DISCOUNTS.size} " +
                                "discount lines). Optional benefits may be added at proposal stage subject " +
                                "to underwriting and applicable additional premium.",
                    )
                } else {
                    Paragraph(
                        "The following optional benefits are available under this product. " +
                                "Each may be added at proposal stage subject to underwriting and " +
                                "applicable additional premium. Full benefit-wise terms and conditions " +
                                "are set out in the Policy Schedule.",
                    )
                    Box(Modifier.padding(top = AegisSpacing.s2)) {
                        CoverListBlock(coverIds)
                    }
                }
            }
            AegisHDivider()

            Section(number = "3", title = "Eligibility") {
                LedgerRow("Minimum entry age", "${plan.minAge} years")
                LedgerRow("Maximum entry age", "${plan.maxAge} years")
                LedgerRow(
                    "Family types accepted",
                    plan.availableFamilyTypes.joinToString(", "),
                )
                LedgerRow(
                    "Geographic zones",
                    plan.availableZones.joinToString(", "),
                )
                Box(Modifier.padding(top = AegisSpacing.s2)) {
                    Text(
                        "Sum-insured options (₹)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AegisColors.textSecondary,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = AegisSpacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                    verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                ) {
                    plan.availableSumInsureds.forEach { si ->
                        AegisChip(
                            label = formatRupees(si.toDouble()),
                            selected = false,
                        )
                    }
                }
            }
            AegisHDivider()

            Section(number = "4", title = "Premium") {
                Paragraph(
                    "Premium for this product is computed by the Prudential Health pricing " +
                            "engine using the actuarially-filed rate cards keyed on the rate table " +
                            "\"${plan.rateTableId.ifBlank { plan.id }}\". Final premium varies by " +
                            "the insured member's age band, chosen sum insured, geographic zone, " +
                            "family composition, selected optional benefits, and any applicable " +
                            "discounts.",
                )
                Box(Modifier.padding(top = AegisSpacing.s2))
                LedgerRow(
                    "Goods & Services Tax (GST)",
                    "${(plan.gstRate * 100).roundedTenth()}% on premium (HSN 9971)",
                )
                LedgerRow(
                    "Maximum discount cap",
                    "${(plan.maxDiscountCap * 100).roundedTenth()}% of base premium",
                )
                LedgerRow("Co-payment table", plan.coPaymentTable.name)
                Paragraph(
                    "Premium tenure discounts apply per the Prudential Health master discount " +
                            "schedule — 7.5% (2-year), 10% (3-year), 12.5% (4-year), 15% (5-year) " +
                            "on single-premium policies — subject to the maximum discount cap above. " +
                            "Detailed rate cards are reproduced in Annexure A of the filed product " +
                            "document and may be obtained from the insurer on request.",
                )
            }
            AegisHDivider()

            Section(number = "5", title = "Waiting periods") {
                Paragraph(
                    "The following waiting periods apply from the policy inception date. " +
                            "Continuous renewal credit is granted on portability subject to IRDAI " +
                            "(Health Insurance) Regulations, 2016.",
                )
                Box(Modifier.padding(top = AegisSpacing.s2))
                LedgerRow("Initial waiting period", "30 days from policy inception")
                LedgerRow("Specified diseases waiting period", "24 months")
                LedgerRow("Pre-existing disease (PED) waiting period", "36 months")
                Box(Modifier.padding(top = AegisSpacing.s2))
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Default values — per-plan overrides pending",
                    body = "These are the Prudential Health default waiting periods. Per-plan " +
                            "overrides will be lifted from the audit ledger in a future phase " +
                            "(see DASHBOARD_REDESIGN_PLAN.md §Phase 2 — Per-Plan Waiting Periods).",
                )
            }
            AegisHDivider()

            Section(number = "6", title = "Free-look period") {
                Paragraph(
                    "In accordance with IRDAI (Health Insurance) Regulations, 2016, the " +
                            "policyholder has a free-look period of 15 days from the date of " +
                            "receipt of the policy document to review the terms and conditions. " +
                            "If the policyholder is not agreeable to any of the terms, they may " +
                            "return the policy to the insurer for cancellation and a refund of " +
                            "premium, after deduction of stamp duty, proportionate risk premium " +
                            "for the period of cover, and expenses incurred on medical examination, " +
                            "if any.",
                )
            }
            AegisHDivider()

            Section(number = "7", title = "Grace period") {
                Paragraph(
                    "A grace period of 30 days is allowed for renewal of the policy after the " +
                            "due date. Continuity of cover (including credit for waiting periods " +
                            "and no-claim bonus) is preserved if the policy is renewed within the " +
                            "grace period. Any claim arising during the grace period for an event " +
                            "occurring before the premium has been received by the insurer shall " +
                            "not be admissible.",
                )
            }
            AegisHDivider()

            Section(number = "8", title = "Claim process & repudiation") {
                Paragraph(
                    "Claim intimation must be made to the insurer within 24 hours of " +
                            "hospitalisation (for planned admissions, 48 hours in advance). The " +
                            "policyholder shall submit all required documents — discharge summary, " +
                            "bills, prescriptions, investigation reports — within 15 days of " +
                            "discharge. The insurer shall settle or repudiate the claim within 30 " +
                            "days of receipt of the last required document.",
                )
                Paragraph(
                    "Where a claim is repudiated, the insurer shall communicate the grounds " +
                            "for repudiation in writing with reference to the specific clauses of " +
                            "the policy. A policyholder aggrieved by repudiation may approach the " +
                            "insurer's grievance redressal cell (see §11 below) and thereafter the " +
                            "Insurance Ombudsman.",
                )
                Paragraph(
                    "Cashless facility is available at the insurer's empanelled network " +
                            "hospitals subject to pre-authorisation. For non-network hospitals, " +
                            "the claim shall be processed on a reimbursement basis.",
                )
            }
            AegisHDivider()

            Section(number = "9", title = "Cancellation & portability") {
                Paragraph(
                    "The policyholder may cancel the policy at any time during the policy " +
                            "term by giving the insurer 15 days' written notice. On cancellation, " +
                            "the insurer shall refund premium on a short-period basis as per the " +
                            "filed grid, provided no claim has been made under the policy.",
                )
                Paragraph(
                    "Portability: the policyholder may port the policy to another insurer, or " +
                            "to a different product offered by the insurer, in accordance with " +
                            "IRDAI (Portability of Health Insurance Policies) Regulations. A " +
                            "portability request must be made at least 45 days prior to the renewal " +
                            "date. Credit for waiting periods served under the previous policy " +
                            "shall be granted to the extent permitted by the said Regulations.",
                )
            }
            AegisHDivider()

            Section(number = "10", title = "Grievance redressal") {
                Paragraph(
                    "Any grievance arising out of or in connection with this policy may be " +
                            "addressed to the Grievance Redressal Officer of Prudential Health " +
                            "Insurance at the address below. If the grievance is not resolved to " +
                            "the policyholder's satisfaction within 30 days, the policyholder may " +
                            "approach the Insurance Ombudsman of the appropriate jurisdiction, or " +
                            "the IRDAI through the Integrated Grievance Management System (IGMS) " +
                            "at igms.irda.gov.in, or via the toll-free helpline 155255 / 1800 4254 732.",
                )
                Box(Modifier.padding(top = AegisSpacing.s2))
                LedgerRow("Grievance Redressal Officer", "grievance@pruhealth.example.in")
                LedgerRow("Toll-free helpline", "1800 200 7747")
                LedgerRow("Postal address", "PRUHealth, Mumbai — registered office on policy schedule")
            }
            AegisHDivider()

            Section(number = "11", title = "Lifecycle & filing status") {
                LedgerRow("Plan lifecycle", plan.lifecycle.label)
                LedgerRow(
                    "UIN registration",
                    if (uinMissing) "Pending — not yet filed with IRDAI" else "Filed",
                )
                LedgerRow("Last modified", meta.lastModifiedIso)
                LedgerRow("Modified by", meta.lastModifiedBy)
                LedgerRow("Active for new business", if (plan.isActive) "Yes" else "No (renewal-only)")
                if (plan.lifecycle == PlanLifecycle.RETIRED) {
                    Box(Modifier.padding(top = AegisSpacing.s2))
                    AegisCallout(
                        kind = CalloutKind.WARN,
                        title = "Product retired — renewal only",
                        body = "This product has been withdrawn for new business. Existing " +
                                "policyholders continue to be serviced on renewal at the filed " +
                                "rates until further notice.",
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(number: String, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "§$number",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.indigo700,
                fontFamily = FontFamily.Monospace,
            )
            Box(Modifier.width(AegisSpacing.s3))
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textPrimary,
            )
        }
        content()
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = AegisColors.textBody,
    )
}

@Composable
private fun LedgerRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(AegisSpacing.s4))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AegisColors.textBody,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun CoverListBlock(coverIds: Set<String>) {
    // Resolve display names via CoverCatalog; fall back to bare ID if unknown.
    val resolved = coverIds.map { id ->
        val meta = CoverCatalog.findById(id)
        Triple(id, meta?.name ?: id, meta?.isDiscount == true)
    }.sortedBy { it.second.lowercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AegisColors.slate2, AegisRadii.shapeMd)
            .border(1.dp, AegisColors.slate3, AegisRadii.shapeMd)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
    ) {
        resolved.forEach { (id, name, isDiscount) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "•",
                    fontSize = 14.sp,
                    color = AegisColors.indigo500,
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    name,
                    fontSize = 13.sp,
                    color = AegisColors.textBody,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(AegisSpacing.s2))
                if (isDiscount) {
                    AegisChip(label = "Discount", tone = AegisChipTone.Info, selected = false)
                    Box(Modifier.width(AegisSpacing.s2))
                }
                Text(
                    id,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.textSecondary,
                )
            }
        }
        Text(
            "${resolved.size} optional benefit${if (resolved.size == 1) "" else "s"} permitted under this plan.",
            fontSize = 12.sp,
            color = AegisColors.textSecondary,
            modifier = Modifier.padding(top = AegisSpacing.s1),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Document footer — IRDAI registration disclaimer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DocumentFooter(plan: Plan, meta: PlanMeta, docDate: String) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(
                "REGULATORY DISCLAIMER",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textSecondary,
                letterSpacing = 0.5.sp,
            )
            Text(
                "Prudential Health Insurance Co. Ltd. is regulated by the Insurance Regulatory " +
                        "and Development Authority of India (IRDAI). This prospectus is issued in " +
                        "compliance with IRDAI (Health Insurance) Regulations, 2016 and forms part " +
                        "of the policy documentation. The information contained herein is " +
                        "indicative; the final terms, conditions, exclusions and limitations are " +
                        "as set out in the Policy Schedule and Policy Wording. In case of any " +
                        "conflict between this prospectus and the policy document, the policy " +
                        "document shall prevail.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = AegisColors.textBody,
            )
            Text(
                "Insurance is the subject matter of solicitation. For more details on risk " +
                        "factors, terms and conditions, please read the sales brochure carefully " +
                        "before concluding a sale.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = AegisColors.textBody,
                fontWeight = FontWeight.Medium,
            )
            AegisHDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Document: Prospectus / ${plan.id} / v1.0",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.textSecondary,
                )
                Text(
                    "Generated $docDate",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AegisColors.textSecondary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Today's date as YYYY-MM-DD (UTC). KMP-safe — no JVM-only APIs. */
private fun todayIsoDate(): String {
    // `Instant.toString()` yields ISO-8601 like "2026-05-22T10:13:45.123Z" — take the
    // date prefix. Avoids dragging in `TimeZone.UTC` + `toLocalDateTime()` from
    // kotlinx-datetime (the time-zone variants on wasm need the zone database).
    val iso = Clock.System.now().toString()
    return iso.substringBefore('T').ifBlank { iso.take(10) }
}

/** Map a [PlanLifecycle] to the closest [AegisStatus] pill state. */
private fun PlanLifecycle.toAegisStatus(): AegisStatus = when (this) {
    PlanLifecycle.LIVE -> AegisStatus.Live
    PlanLifecycle.DRAFT -> AegisStatus.Draft
    PlanLifecycle.RETIRED -> AegisStatus.Retired
}

private val PlanLifecycle.label: String get() = when (this) {
    PlanLifecycle.LIVE -> "Live (in force)"
    PlanLifecycle.DRAFT -> "Draft (pre-registration)"
    PlanLifecycle.RETIRED -> "Retired (renewal-only)"
}

/** Round a percentage to one decimal place without dragging in JVM `String.format`. */
private fun Double.roundedTenth(): Double = (this * 10).toLong() / 10.0
