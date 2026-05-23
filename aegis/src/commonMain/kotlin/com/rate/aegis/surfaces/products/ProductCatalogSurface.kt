@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.rate.aegis.surfaces.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.DeepLink
import com.rate.aegis.LocalAegisDeepLink
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.LocalSurfaceRouter
import com.rate.aegis.components.*
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.*
import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanLifecycle
import com.rate.domain.model.PlanType
import com.rate.domain.money.formatRupees

/**
 * Product Catalog — BUSINESS-shell surface (parallel iteration h).
 *
 * Read-only, executive-level overview of every plan the pricing engine knows
 * about, grouped by product family ([PlanType]). For editing, operators jump to
 * the Plan Configurator. Single fetch on mount via `client.getPlans()` — the
 * catalogue doesn't churn, so we skip the auto-refresh loop the dashboard uses.
 */
@Composable
fun ProductCatalogSurface() {
    val client = rememberApiClient()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Plan?>(null) }

    val refreshTick by LocalRefreshTicker.current
    LaunchedEffect(client, refreshTick) {
        runCatching { client.getPlans() }
            .onSuccess {
                plans = it
                loadError = null
                loaded = true
            }
            .onFailure { t ->
                loadError = t.message ?: t::class.simpleName ?: "unknown error"
                loaded = true
            }
    }

    val liveCount = plans.count { it.lifecycle == PlanLifecycle.LIVE }
    val draftCount = plans.count { it.lifecycle == PlanLifecycle.DRAFT }
    val retiredCount = plans.count { it.lifecycle == PlanLifecycle.RETIRED }

    // Single-select lifecycle filter. `null` means "All"; otherwise the chosen
    // lifecycle scopes the family sections (and hides any whose count drops
    // to 0 under the filter).
    var lifecycleFilter by remember { mutableStateOf<PlanLifecycle?>(null) }
    val filteredPlans = remember(plans, lifecycleFilter) {
        val f = lifecycleFilter ?: return@remember plans
        plans.filter { it.lifecycle == f }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Product Catalog",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "Top-down view of all plans grouped by product family. " +
                    "For edits, use Plan Configurator.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        when {
            loadError != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Server unreachable",
                body = "Could not load plans: $loadError. The catalogue requires a " +
                        "running server at the configured base URL.",
            )
            !loaded -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading…",
                body = "Fetching the plan catalogue from the server.",
            )
            else -> AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Live data",
                body = "${plans.size} plans loaded across ${PRODUCT_FAMILY_ORDER.count { fam -> plans.any { it.planType == fam } }} product families.",
            )
        }

        // KPI strip ─────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            KpiTile(
                label = "Total plans",
                value = plans.size.toString(),
                helper = "All families combined",
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Live",
                value = liveCount.toString(),
                helper = "Sellable today",
                accent = AegisColors.success700,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Draft",
                value = draftCount.toString(),
                helper = "Pending sign-off",
                accent = AegisColors.warn700,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Retired",
                value = retiredCount.toString(),
                helper = "No new business",
                accent = AegisColors.slate8,
                modifier = Modifier.weight(1f),
            )
        }

        // Lifecycle filter chips ────────────────────────────────────────────
        // Matches the Aegis chrome convention used elsewhere in this surface
        // (drawer, family table cells) — AegisChip with single-select semantics.
        // Counts are over the *unfiltered* plan list so the operator sees the
        // full distribution while drilling.
        if (loaded && plans.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
            ) {
                AegisChip(
                    label = "All (${plans.size})",
                    selected = lifecycleFilter == null,
                    onClick = { lifecycleFilter = null },
                )
                AegisChip(
                    label = "LIVE ($liveCount)",
                    selected = lifecycleFilter == PlanLifecycle.LIVE,
                    onClick = { lifecycleFilter = PlanLifecycle.LIVE },
                )
                AegisChip(
                    label = "DRAFT ($draftCount)",
                    selected = lifecycleFilter == PlanLifecycle.DRAFT,
                    onClick = { lifecycleFilter = PlanLifecycle.DRAFT },
                )
                AegisChip(
                    label = "RETIRED ($retiredCount)",
                    selected = lifecycleFilter == PlanLifecycle.RETIRED,
                    onClick = { lifecycleFilter = PlanLifecycle.RETIRED },
                )
            }
        }

        // Family sections ───────────────────────────────────────────────────
        if (loaded && plans.isNotEmpty()) {
            // Sections render against `filteredPlans` so an empty bucket (e.g.
            // no DRAFT plans in a family) collapses entirely — preserves the
            // existing "hide empty family" UX.
            PRODUCT_FAMILY_ORDER.forEach { family ->
                val familyPlans = filteredPlans.filter { it.planType == family }
                if (familyPlans.isNotEmpty()) {
                    FamilySection(
                        family = family,
                        plans = familyPlans,
                        onRowClick = { selected = it },
                    )
                }
            }

            // Catch any plans whose `planType` isn't in the declared ordering —
            // makes new enum values visible instead of silently dropped.
            val orderedSet = PRODUCT_FAMILY_ORDER.toSet()
            val orphanFamilies = filteredPlans.map { it.planType }.distinct().filter { it !in orderedSet }
            orphanFamilies.forEach { family ->
                val orphanPlans = filteredPlans.filter { it.planType == family }
                if (orphanPlans.isNotEmpty()) {
                    FamilySection(
                        family = family,
                        plans = orphanPlans,
                        onRowClick = { selected = it },
                    )
                }
            }

            // If the filter zeroed every family, render an explicit empty card
            // instead of a blank surface so the operator knows the filter (not
            // the data) is at fault.
            if (lifecycleFilter != null && filteredPlans.isEmpty()) {
                AegisCard {
                    AegisEmptyState(
                        title = "No ${lifecycleFilter!!.name} plans",
                        helper = "No plans match the current lifecycle filter. " +
                                "Clear the filter to see the full catalogue.",
                    )
                }
            }
        } else if (loaded && plans.isEmpty()) {
            AegisCard {
                AegisEmptyState(
                    title = "No plans in the catalogue",
                    helper = "The server returned an empty list. Use the Import surface to seed plans, " +
                            "or create one in Plan Configurator.",
                )
            }
        }
    }

    AegisDrawer(
        open = selected != null,
        onClose = { selected = null },
        title = selected?.name ?: "Plan details",
        subtitle = selected?.let { "${it.id} · ${it.planType.displayName}" },
    ) {
        selected?.let { plan ->
            PlanDetailDrawerBody(plan, onClose = { selected = null })
        }
    }
}

/**
 * Display order for product families. Listed top-down from mass-market domestic
 * tiers to global / premium variants — matches how the actuarial team thinks
 * about the portfolio. Any [PlanType] not in this list still renders, just
 * appended at the end (see orphanFamilies handling above).
 */
private val PRODUCT_FAMILY_ORDER: List<PlanType> = listOf(
    PlanType.DOMESTIC,
    PlanType.DOMESTIC_POSP,
    PlanType.DOMESTIC_FLAGSHIP,
    PlanType.DOMESTIC_SENIOR,
    PlanType.DOMESTIC_SUBSTANDARD,
    PlanType.GLOBAL,
    PlanType.GLOBAL_PLUS,
)

@Composable
private fun KpiTile(
    label: String,
    value: String,
    helper: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = AegisColors.textBody,
) {
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(
                label,
                fontSize = 12.sp,
                color = AegisColors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Text(helper, fontSize = 11.sp, color = AegisColors.textSecondary)
        }
    }
}

@Composable
private fun FamilySection(
    family: PlanType,
    plans: List<Plan>,
    onRowClick: (Plan) -> Unit,
) {
    AegisCard(
        title = family.displayName,
        subtitle = "${plans.size} plan${if (plans.size == 1) "" else "s"}",
    ) {
        AegisTable(
            items = plans,
            onRowClick = onRowClick,
            rowKey = { it.id },
            density = TableDensity.Compact,
            columns = listOf(
                AegisColumn<Plan>(
                    header = "Plan ID", weight = 1.1f, mono = true,
                    cell = {
                        Text(
                            it.id,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AegisColors.textBody,
                        )
                    },
                ),
                AegisColumn(
                    header = "Name", weight = 1.8f,
                    cell = {
                        Text(
                            it.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AegisColors.textBody,
                        )
                    },
                ),
                AegisColumn(
                    header = "Lifecycle", weight = 0.9f,
                    cell = { AegisStatusPill(it.lifecycle.toAegisStatus()) },
                ),
                AegisColumn(
                    header = "Active", weight = 0.6f,
                    cell = {
                        Text(
                            if (it.isActive) "Yes" else "No",
                            fontSize = 13.sp,
                            color = if (it.isActive) AegisColors.textBody else AegisColors.textSecondary,
                            fontWeight = if (it.isActive) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                ),
                AegisColumn(
                    header = "SI grid", weight = 1.5f,
                    cell = {
                        Text(
                            formatSiGrid(it.availableSumInsureds),
                            fontSize = 13.sp,
                            color = AegisColors.textBody,
                        )
                    },
                ),
                AegisColumn(
                    header = "Age range", weight = 0.9f,
                    cell = {
                        Text(
                            "${it.minAge}–${it.maxAge} yrs",
                            fontSize = 13.sp,
                            color = AegisColors.textBody,
                        )
                    },
                ),
                AegisColumn(
                    header = "Zones", weight = 1.4f,
                    cell = {
                        Text(
                            formatZones(it.availableZones),
                            fontSize = 13.sp,
                            color = AegisColors.textBody,
                        )
                    },
                ),
                AegisColumn(
                    header = "GST", weight = 0.5f,
                    cell = {
                        Text(
                            formatPercent(it.gstRate),
                            fontSize = 13.sp,
                            color = AegisColors.textBody,
                        )
                    },
                ),
            ),
            emptyState = {
                AegisEmptyState(
                    title = "No plans in ${family.displayName}",
                    helper = "Empty families are usually hidden — this is a fallback.",
                )
            },
        )
    }
}

@Composable
private fun PlanDetailDrawerBody(plan: Plan, onClose: () -> Unit) {
    val deepLink = LocalAegisDeepLink.current
    val router = LocalSurfaceRouter.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s5),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            AegisStatusPill(plan.lifecycle.toAegisStatus())
            AegisChip(
                label = if (plan.isActive) "Active" else "Inactive",
                tone = if (plan.isActive) AegisChipTone.Success else AegisChipTone.Neutral,
                selected = false,
            )
        }
        AegisHDivider()

        DetailRow("Plan ID", plan.id, mono = true)
        DetailRow("Name", plan.name)
        DetailRow("Product family", plan.planType.displayName)
        DetailRow("Lifecycle", plan.lifecycle.name)
        DetailRow("Underwriting category", plan.underwritingCategory.name)
        DetailRow("Geography scope", plan.geographyScope.label)
        DetailRow("Co-payment table", plan.coPaymentTable.name)
        DetailRow("Age range", "${plan.minAge}–${plan.maxAge} yrs")
        DetailRow("GST rate", formatPercent(plan.gstRate))
        DetailRow("Max discount cap", formatPercent(plan.maxDiscountCap))
        DetailRow("Active", if (plan.isActive) "Yes" else "No")

        if (plan.description.isNotBlank()) {
            AegisHDivider()
            Text(
                "Description",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textSecondary,
            )
            Text(plan.description, fontSize = 14.sp, color = AegisColors.textBody)
        }

        AegisHDivider()
        ChipSection(
            label = "Sum insured options",
            count = plan.availableSumInsureds.size,
        ) {
            plan.availableSumInsureds.forEach { si ->
                AegisChip(label = formatRupees(si.toDouble()), selected = false)
            }
        }

        ChipSection(
            label = "Zones",
            count = plan.availableZones.size,
        ) {
            plan.availableZones.forEach { z ->
                AegisChip(label = z, selected = false)
            }
        }

        ChipSection(
            label = "Family types",
            count = plan.availableFamilyTypes.size,
        ) {
            plan.availableFamilyTypes.forEach { ft ->
                AegisChip(label = ft, selected = false)
            }
        }

        // Allowed covers — clickable: each chip closes the drawer, sets the
        // deep-link to the cover id, and routes to the Cover Catalog surface
        // (which already has a deep-link consumer that opens the cover drawer).
        ChipSection(
            label = "Allowed covers",
            count = plan.allowedCoverIds.size,
        ) {
            plan.allowedCoverIds.sorted().forEach { coverId ->
                val label = CoverCatalog.findById(coverId)?.name ?: coverId
                AegisChip(
                    label = label,
                    selected = false,
                    onClick = {
                        deepLink.value = DeepLink(coverId = coverId)
                        onClose()
                        router(AegisSurface.COVER_CATALOG)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChipSection(
    label: String,
    count: Int,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AegisColors.textSecondary,
            )
            Text(
                "$count option${if (count == 1) "" else "s"}",
                fontSize = 12.sp,
                color = AegisColors.textSecondary,
            )
        }
        if (count == 0) {
            Text("—", fontSize = 13.sp, color = AegisColors.textSecondary)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                content = content,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
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
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

// ── Formatting helpers (commonMain — no java.util / String.format) ──────────

private fun PlanLifecycle.toAegisStatus(): AegisStatus = when (this) {
    PlanLifecycle.LIVE -> AegisStatus.Live
    PlanLifecycle.DRAFT -> AegisStatus.Draft
    PlanLifecycle.RETIRED -> AegisStatus.Retired
}

/**
 * Compact "lowest … highest" sum-insured rendering for the table cell. Single
 * value renders as just that value; empty list as "—".
 *
 *   [1_000_000, 2_500_000, 5_000_000, 10_000_000] → "₹10,00,000 … ₹1,00,00,000"
 */
private fun formatSiGrid(sis: List<Long>): String {
    if (sis.isEmpty()) return "—"
    val sorted = sis.sorted()
    val lo = formatRupees(sorted.first().toDouble())
    val hi = formatRupees(sorted.last().toDouble())
    if (sorted.size == 1) return lo
    return "$lo … $hi"
}

/**
 * Zone column rendering. Avoids listing all zones if there's a clear
 * Pan-India entry (which subsumes the others in actuarial terms), and prefixes
 * with a count when there are multiple discrete zones.
 */
private fun formatZones(zones: List<String>): String {
    if (zones.isEmpty()) return "—"
    if (zones.any { it.equals("Pan India", ignoreCase = true) }) return "Pan India"
    if (zones.size == 1) return zones.first()
    return "${zones.size} · ${zones.joinToString(", ")}"
}

/**
 * Percent with one decimal, trimmed when the decimal is .0 — e.g.
 * 0.18 → "18%", 0.075 → "7.5%". No `String.format` because that's JVM-only.
 */
private fun formatPercent(fraction: Double): String {
    val tenths = kotlin.math.round(fraction * 1000).toInt()  // e.g. 0.075 → 75
    val whole = tenths / 10
    val rem = tenths % 10
    return if (rem == 0) "$whole%" else "$whole.$rem%"
}
