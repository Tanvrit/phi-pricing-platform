package com.rate.aegis.surfaces.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.rate.aegis.DeepLink
import com.rate.aegis.LocalAegisDeepLink
import com.rate.aegis.components.*
import com.rate.aegis.data.DashboardSource
import com.rate.aegis.data.FakeAegisRepo
import com.rate.aegis.data.rememberDashboardData
import com.rate.aegis.theme.*
import com.rate.aegis.util.buildCsv
import com.rate.aegis.util.copyToClipboard
import com.rate.aegis.util.saveCsv
import com.rate.aegis.util.todayIsoDate
import com.rate.domain.money.formatRupees

/**
 * Quote Explorer — Ship-3 from DASHBOARD_REDESIGN_PLAN.md Pass 6 §6.1.
 *
 * Search + filter + sort + drill-down for the synthetic quote ledger.
 * Filter chips are functional (in-memory); the drawer is a placeholder until
 * we wire the real engine response into Aegis.
 */
@Composable
fun QuoteExplorerSurface() {
    var search by remember { mutableStateOf("") }
    var tierFilter by remember { mutableStateOf<TierFilter>(TierFilter.All) }
    var validityFilter by remember { mutableStateOf<ValidityFilter>(ValidityFilter.All) }
    var selected by remember { mutableStateOf<FakeAegisRepo.FakeQuote?>(null) }
    val dashboard by rememberDashboardData()
    val all = dashboard.quotes

    // Deep-link: command palette pre-selects a quote → open its drawer once data lands.
    // Keying on dashboard.quotes lets the effect re-run when the live cache fetches.
    val deepLink = LocalAegisDeepLink.current
    LaunchedEffect(deepLink.value.quoteId, dashboard.quotes) {
        val target = deepLink.value.quoteId
        if (target != null) {
            val match = dashboard.quotes.firstOrNull { it.id == target }
            if (match != null) {
                selected = match
                deepLink.value = DeepLink.NONE
            }
        }
    }

    val filtered = remember(search, tierFilter, validityFilter) {
        all.filter { q ->
            val matchesSearch = search.isBlank() ||
                    q.id.contains(search, true) ||
                    q.planName.contains(search, true) ||
                    q.planId.contains(search, true) ||
                    q.primaryAge.toString() == search.trim()
            val matchesTier = when (tierFilter) {
                TierFilter.All -> true
                TierFilter.Domestic -> q.planId in setOf("PHI_BASIC", "PHI_POSP")
                TierFilter.Flagship -> q.planId.startsWith("PHI_FLAGSHIP")
                TierFilter.Senior -> q.planId == "PHI_SENIOR" || q.planId == "PHI_SUBSTANDARD"
                TierFilter.Global -> q.planId.startsWith("PHI_GLOBAL")
            }
            val matchesValidity = when (validityFilter) {
                ValidityFilter.All -> true
                ValidityFilter.Valid -> q.isValid
                ValidityFilter.Invalid -> !q.isValid
            }
            matchesSearch && matchesTier && matchesValidity
        }
    }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text("Quote Explorer", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        val sourceLabel = when (dashboard.source) {
            DashboardSource.LOADING -> "loading…"
            DashboardSource.LIVE    -> "live from server"
            DashboardSource.DEMO    -> "demo data — ${dashboard.fallbackReason}"
        }
        Text("${filtered.size} of ${all.size} quotes — $sourceLabel.",
            fontSize = 13.sp, color = AegisColors.textSecondary)

        AegisCard {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(
                    value = search,
                    onValueChange = { search = it },
                    label = "Search by quote ID, plan, or age",
                    helper = "Try \"Q-202602\", \"Flagship\", \"35\""
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text("Tier:", fontSize = 13.sp, color = AegisColors.textSecondary,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                    TierFilter.entries.forEach { t ->
                        AegisChip(label = t.label, selected = tierFilter == t, onClick = { tierFilter = t })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    Text("Status:", fontSize = 13.sp, color = AegisColors.textSecondary,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                    ValidityFilter.entries.forEach { v ->
                        AegisChip(label = v.label, selected = validityFilter == v, onClick = { validityFilter = v })
                    }
                }
                // ── Export — dumps the *currently filtered* view to CSV. ─────────
                // Placed as the last filter-card child so it lives where the user
                // is already focused on filter state. Secondary variant so it
                // sits below the primary "click a row" affordance in priority.
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(
                        label = "Export CSV (${filtered.size})",
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                        enabled = filtered.isNotEmpty(),
                        onClick = {
                            val csv = buildCsv(
                                headers = listOf(
                                    "Quote ID", "Created", "Plan", "Age", "Family",
                                    "Zone", "Tenure", "SI", "Status", "Total (incl. GST)"
                                ),
                                rows = filtered.map { q ->
                                    listOf(
                                        q.id, q.createdAt, q.planName, q.primaryAge, q.familyType,
                                        q.zone, q.tenureLabel, q.sumInsured,
                                        if (q.isValid) "Valid" else "Invalid",
                                        if (q.isValid) q.totalIncludingGst else 0.0
                                    )
                                }
                            )
                            saveCsv("aegis-quotes-${todayIsoDate()}.csv", csv)
                        }
                    )
                }
            }
        }

        AegisCard {
            AegisTable(
                items = filtered,
                onRowClick = { selected = it },
                columns = listOf(
                    AegisColumn<FakeAegisRepo.FakeQuote>(
                        header = "Quote ID", weight = 1.7f,
                        cell = { Text(it.id, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Created", weight = 0.9f,
                        cell = { Text(it.createdAt, fontSize = 13.sp, color = AegisColors.textSecondary) }
                    ),
                    AegisColumn(
                        header = "Plan", weight = 1.6f,
                        cell = { Text(it.planName, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Age", weight = 0.4f, align = TextAlign.End,
                        cell = { Text(it.primaryAge.toString(), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Family", weight = 0.6f,
                        cell = { Text(it.familyType, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Zone", weight = 0.7f,
                        cell = { Text(it.zone, fontSize = 13.sp, color = AegisColors.textSecondary) }
                    ),
                    AegisColumn(
                        header = "Tenure", weight = 0.7f,
                        cell = { Text(it.tenureLabel, fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "SI", weight = 0.9f, align = TextAlign.End, mono = true,
                        cell = { Text(formatRupees(it.sumInsured / 1.0), fontSize = 13.sp) }
                    ),
                    AegisColumn(
                        header = "Status", weight = 0.7f,
                        cell = {
                            if (it.isValid) AegisStatusPill(AegisStatus.Approved)
                            else AegisStatusPill(AegisStatus.Rejected)
                        }
                    ),
                    AegisColumn(
                        header = "Total (incl. GST)", weight = 1.2f,
                        align = TextAlign.End, mono = true,
                        cell = {
                            if (it.isValid) Text(formatRupees(it.totalIncludingGst), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            else Text("—", fontSize = 13.sp, color = AegisColors.textSecondary)
                        }
                    ),
                ),
                emptyState = {
                    AegisEmptyState(
                        title = "No quotes match your filters",
                        helper = "Adjust the chips above or clear the search box."
                    )
                }
            )
        }
    }

    // Drawer for drill-down ─────────────────────────────────────────────────
    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.id ?: "Quote details",
        subtitle = selected?.planName,
    ) {
        selected?.let { q ->
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Text(q.planName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                AegisHDivider()
                LedgerRow("Quote ID", q.id)
                LedgerRow("Created", q.createdAt)
                LedgerRow("Plan ID", q.planId)
                LedgerRow("Primary age", q.primaryAge.toString())
                LedgerRow("Family type", q.familyType)
                LedgerRow("Zone", q.zone)
                LedgerRow("Tenure", q.tenureLabel)
                LedgerRow("Sum insured", formatRupees(q.sumInsured / 1.0))
                AegisHDivider()
                if (q.isValid) {
                    val preTax = q.totalIncludingGst / 1.18
                    val gst = q.totalIncludingGst - preTax
                    LedgerRow("Sub-total (pre-tax)", formatRupees(preTax, 2))
                    LedgerRow("GST (18%)", formatRupees(gst, 2))
                    LedgerRow("Total payable", formatRupees(q.totalIncludingGst), bold = true)
                } else {
                    AegisCallout(
                        kind = CalloutKind.WARN,
                        title = "Quote is invalid",
                        body = "Engine returned isValid=false. The Phase 5 follow-up is to " +
                                "surface which validation rule fired (right now only the boolean " +
                                "result is captured). Likely culprits: plan age range, " +
                                "SI not in plan grid, family-type mismatch, or a cover that the " +
                                "plan's allowedCoverIds doesn't permit."
                    )
                }
                Spacer(Modifier.height(AegisSpacing.s3))
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Full breakdown lands when Aegis talks to the engine directly",
                    body = "Ship-3 reads from the synthetic FakeAegisRepo. Once :aegis depends " +
                            "on the live PricingEngine (Phase 4b), this drawer will render " +
                            "per-cover lines + per-year breakdown + the rate-table version that " +
                            "produced the quote."
                )
                // ── Share-with-customer affordance ────────────────────────
                // Every saved quote is shareable — there's no "is_shared" flag;
                // the URL is just a deterministic function of the id. Operator
                // copies the link, drops it into chat/email, customer hits the
                // landing and sees SharedQuoteView (read-only) instead of the
                // buyonline journey. Cloudflare Pages is the canonical host;
                // local dev can rewrite the prefix in a future settings entry.
                Spacer(Modifier.height(AegisSpacing.s3))
                AegisHDivider()
                Spacer(Modifier.height(AegisSpacing.s2))
                Text("Share with customer", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = AegisColors.textBody)
                val shareUrl = "https://phi-buyonline.pages.dev/?quote=${q.id}"
                var copied by remember(q.id) { mutableStateOf(false) }
                Text(shareUrl, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = AegisColors.textSecondary)
                AegisButton(
                    label = if (copied) "Copied" else "Copy URL",
                    variant = AegisButtonVariant.Secondary,
                    size = AegisButtonSize.Sm,
                    onClick = {
                        copyToClipboard(shareUrl)
                        copied = true
                    }
                )
                Text(
                    "Customer sees a read-only summary at this URL. They start a fresh " +
                            "application from the same page after reviewing.",
                    fontSize = 11.sp, color = AegisColors.textSecondary
                )
            }
        }
    }
}

enum class TierFilter(val label: String) {
    All("All"),
    Domestic("Domestic"),
    Flagship("Flagship"),
    Senior("Senior / Sub-std"),
    Global("Global"),
}

enum class ValidityFilter(val label: String) {
    All("All"),
    Valid("Valid"),
    Invalid("Invalid"),
}

@Composable
private fun LedgerRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = AegisColors.textSecondary)
        Text(value, fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = AegisColors.textBody)
    }
}
