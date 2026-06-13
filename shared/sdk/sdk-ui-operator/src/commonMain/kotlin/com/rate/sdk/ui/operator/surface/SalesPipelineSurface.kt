package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.rfq.RfqStatus
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.ui.kit.components.AegisBadge
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisChipTone
import com.rate.sdk.ui.kit.components.AegisEmptyState
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.viewmodel.DemographySummary
import com.rate.sdk.ui.operator.viewmodel.SalesPipelineViewModel

/**
 * RFQ SALES PIPELINE (Dorian slides 16-20: upload -> demography -> compare -> manage).
 *
 * A kanban board of Requests-For-Quote grouped into [RfqStatus] columns. Each card shows the client,
 * lives, requested SI and effective date; clicking it opens a detail panel below the board with:
 *  - the full RFQ field list + status-transition buttons (advance / regress, audit-attributed);
 *  - a MEMBER CENSUS paste box ("age,count" or "age,gender,count") that previews an age-banded
 *    demography summary (display/preview only — no server persistence yet);
 *  - a QUOTE COMPARISON strip across plan tiers (premium per tier; "pending rates" until rates land).
 *
 * The board top bar surfaces two KPIs: total RFQs and total pipeline SI.
 *
 * Layout note (Compose rule): the surface root is a Column with verticalScroll. The board is a
 * horizontalScroll Row of fixed-width status columns, each a bounded-height (heightIn max) inner
 * verticalScroll of plain cards — bounded, so it never measures with infinite height.
 */
@Composable
fun SalesPipelineSurface(
    api: ConfigAdminApi,
    quotes: QuoteApi,
    actor: String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(api, actor) { SalesPipelineViewModel(api, scope, actor) }
    LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5),
    ) {
        Text("Sales Pipeline", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "RFQ funnel — upload, profile the demography, compare plan tiers and manage each account "
                + "across the sales stages.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        if (vm.loading) {
            AegisCallout(kind = CalloutKind.INFO, title = "Loading pipeline…", body = "Pulling the RFQ book and plan catalog.")
        }
        vm.error?.let {
            AegisCallout(kind = CalloutKind.DANGER, title = "Could not load the pipeline", body = it)
        }

        // ── Board KPIs ────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            KpiTile("Total RFQs", vm.totalRfqs.toString(), Modifier.weight(1f))
            KpiTile("Pipeline SI", vm.totalPipelineSi.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
            KpiTile("Plan tiers", vm.plans.size.toString(), Modifier.weight(1f))
        }

        // ── Kanban board ──────────────────────────────────────────────────────
        if (!vm.loading && vm.totalRfqs == 0 && vm.error == null) {
            AegisCard {
                AegisEmptyState(
                    title = "No RFQs yet",
                    helper = "New enquiries land in the Submitted column. Create RFQs from the Sales hub to populate the board.",
                )
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
            ) {
                RfqStatus.entries.forEach { status ->
                    StatusColumn(
                        status = status,
                        rfqs = vm.column(status),
                        selectedId = vm.selectedId,
                        onSelect = { vm.select(it) },
                    )
                }
            }
        }

        // ── Detail panel for the selected RFQ ───────────────────────────────────
        vm.selected?.let { rfq ->
            RfqDetailPanel(vm = vm, rfq = rfq, plans = vm.plans)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Board
// ────────────────────────────────────────────────────────────────────────────

private val COLUMN_WIDTH = 280.dp
private val COLUMN_MAX_HEIGHT = 460.dp

@Composable
private fun StatusColumn(
    status: RfqStatus,
    rfqs: List<Rfq>,
    selectedId: String?,
    onSelect: (Rfq) -> Unit,
) {
    Column(
        Modifier
            .width(COLUMN_WIDTH)
            .background(AegisColors.surfaceMuted, AegisRadii.shapeLg)
            .border(1.dp, AegisColors.border, AegisRadii.shapeLg)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                statusLabel(status),
                style = AegisTypography.small.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold),
            )
            AegisBadge(count = rfqs.size, tone = statusBadgeTone(status))
        }

        if (rfqs.isEmpty()) {
            Text(
                "No RFQs",
                style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                modifier = Modifier.padding(vertical = AegisSpacing.s3),
            )
        } else {
            // Bounded-height inner scroller — never infinite inside the outer verticalScroll.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = COLUMN_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                rfqs.forEach { rfq ->
                    RfqCard(rfq = rfq, selected = rfq.id == selectedId, onClick = { onSelect(rfq) })
                }
            }
        }
    }
}

@Composable
private fun RfqCard(rfq: Rfq, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AegisColors.brand else AegisColors.border
    Column(
        Modifier
            .fillMaxWidth()
            .background(AegisColors.surface, AegisRadii.shapeMd)
            .border(if (selected) 2.dp else 1.dp, borderColor, AegisRadii.shapeMd)
            .clickable(onClick = onClick)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
    ) {
        Text(
            rfq.clientName.ifBlank { "(unnamed client)" },
            style = AegisTypography.body.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold),
        )
        Text(rfq.rfqNumber, style = AegisTypography.mono14.copy(color = AegisColors.textSecondary))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${rfq.lives} lives", style = AegisTypography.small.copy(color = AegisColors.textSecondary))
            Text(
                rfq.requestedSumInsured.formatIndian(showSymbol = true, showDecimals = false),
                style = AegisTypography.moneyS.copy(color = AegisColors.textBody),
            )
        }
        if (rfq.effectiveDate.isNotBlank()) {
            Text("Effective ${rfq.effectiveDate}", style = AegisTypography.small.copy(color = AegisColors.textTertiary))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Detail panel
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun RfqDetailPanel(vm: SalesPipelineViewModel, rfq: Rfq, plans: List<Plan>) {
    AegisCard(
        title = rfq.clientName.ifBlank { "(unnamed client)" },
        subtitle = "${rfq.rfqNumber} · ${statusLabel(rfq.rfqStatus)}",
        action = { AegisButton("Close", onClick = { vm.closeDetail() }, variant = AegisButtonVariant.Ghost, size = AegisButtonSize.Sm) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            // ── Field grid ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                DetailRow("RFQ number", rfq.rfqNumber)
                DetailRow("Lives", rfq.lives.toString())
                DetailRow("Requested SI", rfq.requestedSumInsured.formatIndian(showSymbol = true, showDecimals = false))
                DetailRow("Effective date", rfq.effectiveDate.ifBlank { "—" })
                DetailRow("Group type", rfq.groupTypeRef.ifBlank { "—" })
                DetailRow("Industry", rfq.industryRef.ifBlank { "—" })
                DetailRow("Channel", rfq.channelRef.ifBlank { "—" })
                DetailRow("Intermediary", rfq.intermediaryRef.ifBlank { "—" })
                DetailRow("Notes", rfq.notes.ifBlank { "—" })
                DetailRow("Version", "v${rfq.v}")
            }

            // ── Status transitions ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                Text("Stage", style = AegisTypography.small.copy(color = AegisColors.textSecondary, fontWeight = FontWeight.Medium))
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    val prev = prevStatus(rfq.rfqStatus)
                    val next = nextStatus(rfq.rfqStatus)
                    AegisButton(
                        label = if (prev != null) "← ${statusLabel(prev)}" else "← Regress",
                        onClick = { prev?.let { vm.transitionTo(rfq, it) } },
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                        enabled = prev != null && !vm.transitioning,
                    )
                    AegisButton(
                        label = if (next != null) "${statusLabel(next)} →" else "Advance →",
                        onClick = { next?.let { vm.transitionTo(rfq, it) } },
                        variant = AegisButtonVariant.Primary,
                        size = AegisButtonSize.Sm,
                        enabled = next != null && !vm.transitioning,
                        loading = vm.transitioning,
                    )
                }
                // Jump-to chips for the terminal stages (LOST / CLOSED) — common from any stage.
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    AegisChip(
                        label = "Mark Closed",
                        selected = rfq.rfqStatus == RfqStatus.CLOSED,
                        onClick = { if (!vm.transitioning) vm.transitionTo(rfq, RfqStatus.CLOSED) },
                        tone = AegisChipTone.Success,
                    )
                    AegisChip(
                        label = "Mark Lost",
                        selected = rfq.rfqStatus == RfqStatus.LOST,
                        onClick = { if (!vm.transitioning) vm.transitionTo(rfq, RfqStatus.LOST) },
                        tone = AegisChipTone.Danger,
                    )
                }
            }

            // ── Member census paste + demography preview ───────────────────────
            CensusPasteSection(vm = vm)

            // ── Quote comparison strip ─────────────────────────────────────────
            QuoteComparisonStrip(rfq = rfq, plans = plans)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
        Text(
            label,
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
            modifier = Modifier.width(140.dp),
        )
        Text(value, style = AegisTypography.body.copy(color = AegisColors.textBody), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CensusPasteSection(vm: SalesPipelineViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
        Text("Member census", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
        AegisInput(
            value = vm.censusText,
            onValueChange = { vm.setCensus(it) },
            label = "Paste census (one row per line)",
            helper = "Format: age,count  or  age,gender,count  — preview only, not yet persisted.",
            placeholder = "34,12\n41,M,5\n28,F,8",
        )

        val demo = vm.demography
        if (demo != null) {
            DemographyPreview(demo)
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "${demo.totalLives} lives across ${demo.bands.count { it.lives > 0 }} bands",
                body = "This demography can seed a group quote." +
                    (if (demo.rowsSkipped > 0) " ${demo.rowsSkipped} row(s) skipped (unparseable)." else ""),
            )
        }
    }
}

@Composable
private fun DemographyPreview(demo: DemographySummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AegisColors.surface, AegisRadii.shapeMd)
            .border(1.dp, AegisColors.border, AegisRadii.shapeMd),
    ) {
        Row(
            Modifier.fillMaxWidth().background(AegisColors.slate2).padding(horizontal = AegisSpacing.s4, vertical = 10.dp),
        ) {
            Text("AGE BAND", style = AegisTypography.small.copy(color = AegisColors.textSecondary, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("LIVES", style = AegisTypography.small.copy(color = AegisColors.textSecondary, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        demo.bands.forEach { band ->
            Row(Modifier.fillMaxWidth().padding(horizontal = AegisSpacing.s4, vertical = 8.dp)) {
                Text(band.label, style = AegisTypography.body.copy(color = AegisColors.textBody), modifier = Modifier.weight(1f))
                Text(
                    band.lives.toString(),
                    style = AegisTypography.money.copy(color = if (band.lives > 0) AegisColors.textBody else AegisColors.textTertiary),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(AegisColors.slate2).padding(horizontal = AegisSpacing.s4, vertical = 10.dp),
        ) {
            Text("Total lives", style = AegisTypography.body.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(
                demo.totalLives.toString(),
                style = AegisTypography.money.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuoteComparisonStrip(rfq: Rfq, plans: List<Plan>) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
        Text("Quote comparison", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
        if (plans.isEmpty()) {
            AegisCallout(
                kind = CalloutKind.WARN,
                title = "No plan tiers configured",
                body = "Add plans in the actuarial config to compare premiums per tier for this RFQ.",
            )
            return@Column
        }
        // Up to three tiers side-by-side; bounded width so the strip scrolls horizontally if needed.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
        ) {
            plans.take(3).forEach { plan ->
                TierColumn(plan = plan, rfq = rfq)
            }
        }
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Pending rates",
            body = "Per-tier premiums show once the rate set lands for these plans. The structure below is wired and ready.",
        )
    }
}

@Composable
private fun TierColumn(plan: Plan, rfq: Rfq) {
    // Premium is 0 until rates land; render the structure cleanly with the placeholder dash.
    val premium = Money.ZERO
    Column(
        Modifier
            .width(220.dp)
            .background(AegisColors.surface, AegisRadii.shapeMd)
            .border(1.dp, AegisColors.border, AegisRadii.shapeMd)
            .padding(AegisSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
    ) {
        Text(plan.name, style = AegisTypography.body.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold))
        Text(plan.planType.displayName, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
        Box(Modifier.padding(top = AegisSpacing.s2)) {
            Text(
                if (premium.isZero()) "—" else premium.formatIndian(showSymbol = true, showDecimals = false),
                style = AegisTypography.moneyL.copy(color = if (premium.isZero()) AegisColors.textTertiary else AegisColors.textBody),
            )
        }
        Text("est. annual premium", style = AegisTypography.small.copy(color = AegisColors.textTertiary))
        Text(
            "${rfq.lives} lives · ${plan.geographyScope.label}",
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Shared bits
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KpiTile(label: String, value: String, modifier: Modifier = Modifier) {
    AegisCard(modifier = modifier, padding = PaddingValues(AegisSpacing.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
            Text(value, style = AegisTypography.moneyL.copy(color = AegisColors.textBody))
        }
    }
}

/** Human label for a pipeline stage (the enum tokens are SCREAMING_SNAKE). */
private fun statusLabel(status: RfqStatus): String = when (status) {
    RfqStatus.SUBMITTED -> "Submitted"
    RfqStatus.ESTIMATED_PRICE -> "Estimated Price"
    RfqStatus.CONSOLIDATED_PRICE -> "Consolidated Price"
    RfqStatus.MANAGED -> "Managed"
    RfqStatus.CLOSED -> "Closed"
    RfqStatus.LOST -> "Lost"
}

private fun statusBadgeTone(status: RfqStatus): AegisBadgeTone = when (status) {
    RfqStatus.SUBMITTED -> AegisBadgeTone.Info
    RfqStatus.ESTIMATED_PRICE -> AegisBadgeTone.Brand
    RfqStatus.CONSOLIDATED_PRICE -> AegisBadgeTone.Warn
    RfqStatus.MANAGED -> AegisBadgeTone.Success
    RfqStatus.CLOSED -> AegisBadgeTone.Neutral
    RfqStatus.LOST -> AegisBadgeTone.Danger
}

/** The forward stage in the funnel (null at the end of the happy path / terminal stages). */
private fun nextStatus(status: RfqStatus): RfqStatus? = when (status) {
    RfqStatus.SUBMITTED -> RfqStatus.ESTIMATED_PRICE
    RfqStatus.ESTIMATED_PRICE -> RfqStatus.CONSOLIDATED_PRICE
    RfqStatus.CONSOLIDATED_PRICE -> RfqStatus.MANAGED
    RfqStatus.MANAGED -> RfqStatus.CLOSED
    RfqStatus.CLOSED -> null
    RfqStatus.LOST -> null
}

/** The previous stage in the funnel (null at the start; terminal stages regress to MANAGED). */
private fun prevStatus(status: RfqStatus): RfqStatus? = when (status) {
    RfqStatus.SUBMITTED -> null
    RfqStatus.ESTIMATED_PRICE -> RfqStatus.SUBMITTED
    RfqStatus.CONSOLIDATED_PRICE -> RfqStatus.ESTIMATED_PRICE
    RfqStatus.MANAGED -> RfqStatus.CONSOLIDATED_PRICE
    RfqStatus.CLOSED -> RfqStatus.MANAGED
    RfqStatus.LOST -> RfqStatus.MANAGED
}
