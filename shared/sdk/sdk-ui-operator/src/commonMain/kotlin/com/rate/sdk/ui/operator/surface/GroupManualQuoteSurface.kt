package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.core.money.Money
import com.rate.sdk.rating.model.GradePremiumLine
import com.rate.sdk.rating.model.GroupQuoteResult
import com.rate.sdk.rating.model.ManualClaimYear
import com.rate.sdk.rating.model.ManualDemographyRow
import com.rate.sdk.rating.model.ManualGroupStrategyMode
import com.rate.sdk.rating.model.MemberPremiumLine
import com.rate.sdk.ui.kit.components.AegisBadge
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisColumn
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.AegisTable
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.components.TableDensity
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.viewmodel.GroupManualQuoteViewModel

/**
 * GROUP manual-quote surface (operator console). Pure client-side compute: the operator types
 * every number — group details, an editable demography grid of per-life book rates, the
 * size-discount / industry-loading overrides, and (for experience/hybrid) claim-year history —
 * and "Calculate Quote" runs [com.rate.sdk.rating.handler.ManualGroupRater] in-process to produce
 * a full [GroupQuoteResult] breakdown. No network, no persistence.
 *
 * Layout note: the root is a single page `verticalScroll`. The two editable grids are plain
 * `Column`s of rows (NOT nested LazyColumns) so they flow inside the page scroll; the read-only
 * result tables use [AegisTable] with a bounded `maxHeight` (the only legal place to virtualize).
 */
@Composable
fun GroupManualQuoteSurface() {
    val scope = rememberCoroutineScope()
    val vm = remember { GroupManualQuoteViewModel(scope) }
    val result = vm.result

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Group Quote (Manual)", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
                Text(
                    "Type every number — per-life book rates, the size-discount / loading overrides, and " +
                        "claim history — then compute the full group premium breakdown client-side.",
                    style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                )
            }
            AegisButton(
                label = "Reset to sample",
                onClick = { vm.resetToSample() },
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                leadingIcon = { Icon(Icons.Filled.RestartAlt, null, Modifier.size(16.dp)) },
            )
        }

        vm.error?.let { AegisCallout(kind = CalloutKind.DANGER, title = "Cannot calculate", body = it) }

        GroupDetailsSection(vm)
        DemographySection(vm)
        StrategySection(vm)

        AegisButton(
            label = "Calculate Quote",
            onClick = { vm.calculate() },
            loading = vm.calculating,
            size = AegisButtonSize.Lg,
            leadingIcon = { Icon(Icons.Filled.Calculate, null, Modifier.size(18.dp)) },
        )

        if (result != null) ResultPanel(result)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Section 1 — Group details
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupDetailsSection(vm: GroupManualQuoteViewModel) {
    val input = vm.input
    AegisCard(title = "Group details", subtitle = "Scheme, zone and the manual group factors") {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(input.clientName, vm::setClientName, "Client name", Modifier.weight(1.4f), placeholder = "Acme Technologies Pvt Ltd")
                AegisInput(input.groupConfigId, vm::setGroupConfigId, "Group config id", Modifier.weight(1f), placeholder = "GHI_STD")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(input.industryCode, vm::setIndustryCode, "Industry code", Modifier.weight(1f), placeholder = "IT")
                AegisInput(input.zone, vm::setZone, "Zone", Modifier.weight(1f), placeholder = "Pan India")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                Text("Industry:", style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                INDUSTRY_PRESETS.forEach { code ->
                    AegisChip(
                        label = code,
                        selected = input.industryCode.equals(code, ignoreCase = true),
                        onClick = { vm.setIndustryCode(code) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(fmtPct(input.gstRate * 100.0), vm::setGstPct, "GST %", Modifier.weight(1f), suffix = "%")
                AegisInput(fmtPct(input.employerSharePct), vm::setEmployerSharePct, "Employer share %", Modifier.weight(1f), suffix = "%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                AegisInput(fmtPct(input.groupSizeDiscountPct), vm::setGroupSizeDiscountPct, "Group-size discount %", Modifier.weight(1f), suffix = "%")
                AegisInput(fmtPct(input.industryLoadingPct), vm::setIndustryLoadingPct, "Industry loading %", Modifier.weight(1f), suffix = "%")
            }
            Text(
                "Tenure: 1 year. Discount and loading are manual overrides — no head-count or industry table is consulted.",
                style = AegisTypography.small.copy(color = AegisColors.textTertiary),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Section 2 — Demography & rates (editable grid)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun DemographySection(vm: GroupManualQuoteViewModel) {
    val rows = vm.input.demography
    AegisCard(
        title = "Demography & rates",
        subtitle = "One row per grade × age band — type the per-life book rate",
        action = {
            AegisButton(
                label = "Add row",
                onClick = { vm.addDemographyRow() },
                variant = AegisButtonVariant.Secondary,
                size = AegisButtonSize.Sm,
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            DemographyHeaderRow()
            if (rows.isEmpty()) {
                Text(
                    "No rows — add at least one grade × age-band bucket.",
                    style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                    modifier = Modifier.padding(vertical = AegisSpacing.s3),
                )
            }
            rows.forEachIndexed { i, row ->
                DemographyEditRow(
                    row = row,
                    onChange = { transform -> vm.updateDemographyRow(i) { transform(it) } },
                    onRemove = { vm.removeDemographyRow(i) },
                )
            }

            // Running totals footer.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AegisColors.surfaceMuted)
                    .border(1.dp, AegisColors.border)
                    .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Totals", style = AegisTypography.small.copy(color = AegisColors.textSecondary, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${vm.totalLives} lives", style = AegisTypography.body.copy(color = AegisColors.textPrimary, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Box(Modifier.weight(1.2f)) {
                    Text(
                        "Book ${vm.totalBookPremium.formatIndian(showSymbol = true, showDecimals = false)}",
                        style = AegisTypography.money.copy(color = AegisColors.textPrimary),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemographyHeaderRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AegisColors.slate2)
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
    ) {
        GridHeaderCell("Grade", 1.0f)
        GridHeaderCell("Age band", 1.0f)
        GridHeaderCell("Min age", 0.7f, TextAlign.End)
        GridHeaderCell("Lives", 0.7f, TextAlign.End)
        GridHeaderCell("Sum insured (₹)", 1.1f, TextAlign.End)
        GridHeaderCell("Rate/life (₹)", 1.1f, TextAlign.End)
        Box(Modifier.width(40.dp))
    }
}

@Composable
private fun DemographyEditRow(
    row: ManualDemographyRow,
    onChange: ((ManualDemographyRow) -> ManualDemographyRow) -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        GridInput(row.grade, { v -> onChange { it.copy(grade = v) } }, 1.0f)
        GridInput(row.ageBandLabel, { v -> onChange { it.copy(ageBandLabel = v) } }, 1.0f)
        GridInput(row.ageBandMinAge.toString(), { v -> onChange { it.copy(ageBandMinAge = v.toIntOr(it.ageBandMinAge)) } }, 0.7f, TextAlign.End)
        GridInput(row.lives.toString(), { v -> onChange { it.copy(lives = v.toIntOr(it.lives)) } }, 0.7f, TextAlign.End)
        GridInput(row.sumInsured.toString(), { v -> onChange { it.copy(sumInsured = v.toLongOr(it.sumInsured)) } }, 1.1f, TextAlign.End)
        GridInput(row.ratePerLifeRupees.toString(), { v -> onChange { it.copy(ratePerLifeRupees = v.toLongOr(it.ratePerLifeRupees)) } }, 1.1f, TextAlign.End)
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            AegisButton(
                label = "",
                onClick = onRemove,
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                contentDescription = "Remove row",
                leadingIcon = { Icon(Icons.Filled.Delete, "Remove row", Modifier.size(16.dp), tint = AegisColors.danger500) },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Section 3 — Strategy
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun StrategySection(vm: GroupManualQuoteViewModel) {
    val input = vm.input
    AegisCard(title = "Rating strategy", subtitle = "Manual book rate, or blend with claim experience") {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                ManualGroupStrategyMode.entries.forEach { mode ->
                    AegisChip(
                        label = mode.name,
                        selected = input.mode == mode,
                        onClick = { vm.setMode(mode) },
                    )
                }
            }

            if (input.mode != ManualGroupStrategyMode.MANUAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisInput(fmtPct(input.expenseRatioPct), vm::setExpenseRatioPct, "Expense ratio %", Modifier.weight(1f), suffix = "%")
                    AegisInput(
                        value = input.credibilityOverridePct?.let { fmtPct(it) } ?: "",
                        onValueChange = vm::setCredibilityOverridePct,
                        label = "Credibility override %",
                        modifier = Modifier.weight(1f),
                        suffix = "%",
                        placeholder = "auto (√-rule)",
                    )
                    if (input.mode == ManualGroupStrategyMode.HYBRID) {
                        AegisInput(fmtPct(input.manualWeightPct), vm::setManualWeightPct, "Manual weight %", Modifier.weight(1f), suffix = "%")
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }

                ClaimYearsGrid(vm)
            }
        }
    }
}

@Composable
private fun ClaimYearsGrid(vm: GroupManualQuoteViewModel) {
    val years = vm.input.claimYears
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Claim experience", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
            AegisButton(
                label = "Add year",
                onClick = { vm.addClaimYear() },
                variant = AegisButtonVariant.Secondary,
                size = AegisButtonSize.Sm,
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
            )
        }
        Row(
            Modifier.fillMaxWidth().background(AegisColors.slate2).padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
        ) {
            GridHeaderCell("Year", 1.0f)
            GridHeaderCell("Avg lives", 0.8f, TextAlign.End)
            GridHeaderCell("Earned prem (₹)", 1.2f, TextAlign.End)
            GridHeaderCell("Incurred claims (₹)", 1.2f, TextAlign.End)
            GridHeaderCell("Claims", 0.8f, TextAlign.End)
            Box(Modifier.width(40.dp))
        }
        if (years.isEmpty()) {
            Text(
                "No claim years yet — add at least one for experience / hybrid rating.",
                style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                modifier = Modifier.padding(vertical = AegisSpacing.s2),
            )
        }
        years.forEachIndexed { i, y ->
            ClaimYearEditRow(
                year = y,
                onChange = { transform -> vm.updateClaimYear(i) { transform(it) } },
                onRemove = { vm.removeClaimYear(i) },
            )
        }
    }
}

@Composable
private fun ClaimYearEditRow(
    year: ManualClaimYear,
    onChange: ((ManualClaimYear) -> ManualClaimYear) -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        GridInput(year.label, { v -> onChange { it.copy(label = v) } }, 1.0f)
        GridInput(year.averageLives.toString(), { v -> onChange { it.copy(averageLives = v.toIntOr(it.averageLives)) } }, 0.8f, TextAlign.End)
        GridInput(year.earnedPremium.rupeesText(), { v -> onChange { it.copy(earnedPremium = v.toMoneyOr(it.earnedPremium)) } }, 1.2f, TextAlign.End)
        GridInput(year.incurredClaims.rupeesText(), { v -> onChange { it.copy(incurredClaims = v.toMoneyOr(it.incurredClaims)) } }, 1.2f, TextAlign.End)
        GridInput(year.claimCount.toString(), { v -> onChange { it.copy(claimCount = v.toIntOr(it.claimCount)) } }, 0.8f, TextAlign.End)
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            AegisButton(
                label = "",
                onClick = onRemove,
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                contentDescription = "Remove claim year",
                leadingIcon = { Icon(Icons.Filled.Delete, "Remove claim year", Modifier.size(16.dp), tint = AegisColors.danger500) },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Result panel
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultPanel(result: GroupQuoteResult) {
    if (!result.isValid) {
        AegisCallout(
            kind = CalloutKind.DANGER,
            title = "Quote is not valid",
            body = result.validationErrors.joinToString("\n") { "• $it" },
        )
        return
    }

    AegisCard(
        title = "Group quote",
        subtitle = "${result.totalLives} lives · strategy ${result.strategy} · request ${result.requestId}",
        action = { AegisBadge(label = result.strategy.uppercase(), tone = AegisBadgeTone.Brand) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
            // KPI grid (two rows of equal-weight tiles).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                KpiTile("Manual premium", result.manualPremium.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
                KpiTile("Experience premium", result.experiencePremium.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
                KpiTile("Credibility", "${(result.credibility * 100).fmt1()}%", Modifier.weight(1f))
                KpiTile("Blended premium", result.blendedPremium.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                KpiTile("GST (${(result.gstRate * 100).fmt1()}%)", result.gst.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
                KpiTile("TOTAL", result.total.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f), emphasis = true)
                KpiTile("Employer share", result.allocation.employerAmount.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
                KpiTile("Employee share", result.allocation.employeeAmount.formatIndian(showSymbol = true, showDecimals = false), Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                AegisBadge(label = "Size discount ${(result.groupSizeDiscount * 100).fmt1()}%", tone = AegisBadgeTone.Success)
                AegisBadge(label = "Industry loading ${(result.industryLoading * 100).fmt1()}%", tone = AegisBadgeTone.Warn)
                AegisBadge(
                    label = "Employer ${(result.allocation.employerShare * 100).fmt1()}%",
                    tone = AegisBadgeTone.Info,
                )
            }

            // Per-grade table.
            Text("Per grade", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
            AegisTable(
                items = result.perGrade,
                density = TableDensity.Compact,
                maxHeight = 280.dp,
                rowKey = { it.grade },
                columns = listOf(
                    AegisColumn<GradePremiumLine>("Grade", 1.0f) { Text(it.grade, style = AegisTypography.body.copy(color = AegisColors.textBody)) },
                    AegisColumn("Lives", 0.7f, TextAlign.End) { Text(it.totalLives.toString(), style = AegisTypography.body.copy(color = AegisColors.textBody)) },
                    AegisColumn("Sum insured", 1.1f, TextAlign.End, mono = true) { MoneyCell(Money.fromRupees(it.sumInsured)) },
                    AegisColumn("Book", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.bookPremium) },
                    AegisColumn("Allocated", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.allocatedPremium) },
                    AegisColumn("Employer", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.allocation.employerAmount) },
                    AegisColumn("Employee", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.allocation.employeeAmount) },
                ),
            )

            // Per-member / bucket table.
            Text("Per bucket (grade × age band)", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
            AegisTable(
                items = result.perMember,
                density = TableDensity.Compact,
                maxHeight = 360.dp,
                rowKey = { it.grade + it.ageBandMinAge },
                columns = listOf(
                    AegisColumn<MemberPremiumLine>("Grade", 0.9f) { Text(it.grade, style = AegisTypography.body.copy(color = AegisColors.textBody)) },
                    AegisColumn("Age band", 0.9f) { Text(it.ageBandLabel, style = AegisTypography.body.copy(color = AegisColors.textBody)) },
                    AegisColumn("Lives", 0.6f, TextAlign.End) { Text(it.lives.toString(), style = AegisTypography.body.copy(color = AegisColors.textBody)) },
                    AegisColumn("Sum insured", 1.1f, TextAlign.End, mono = true) { MoneyCell(Money.fromRupees(it.sumInsured)) },
                    AegisColumn("Rate/life", 1.0f, TextAlign.End, mono = true) { MoneyCell(it.ratePerLife) },
                    AegisColumn("Book", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.bookPremium) },
                    AegisColumn("Allocated", 1.1f, TextAlign.End, mono = true) { MoneyCell(it.allocatedPremium) },
                ),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Small shared pieces
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KpiTile(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(
        modifier
            .background(if (emphasis) AegisColors.brandTint else AegisColors.surfaceMuted, com.rate.sdk.ui.kit.theme.AegisRadii.shapeMd)
            .border(1.dp, if (emphasis) AegisColors.brand else AegisColors.border, com.rate.sdk.ui.kit.theme.AegisRadii.shapeMd)
            .padding(AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = AegisTypography.small.copy(color = AegisColors.textSecondary))
        Text(
            value,
            style = AegisTypography.moneyS.copy(
                color = if (emphasis) AegisColors.brand else AegisColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun MoneyCell(money: Money) {
    Text(money.formatIndian(showSymbol = true, showDecimals = false), style = AegisTypography.money.copy(color = AegisColors.textBody))
}

@Composable
private fun RowScope.GridHeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(),
        style = AegisTypography.small.copy(color = AegisColors.textSecondary, fontWeight = FontWeight.SemiBold),
        textAlign = align,
        modifier = Modifier.weight(weight).padding(horizontal = 4.dp),
    )
}

@Composable
private fun RowScope.GridInput(value: String, onValueChange: (String) -> Unit, weight: Float, @Suppress("UNUSED_PARAMETER") align: TextAlign = TextAlign.Start) {
    Box(Modifier.weight(weight)) {
        AegisInput(value = value, onValueChange = onValueChange, label = "")
    }
}

private val INDUSTRY_PRESETS = listOf("IT", "BFSI", "SERVICES", "MANUFACTURING", "CONSTRUCTION", "MINING", "CHEMICALS")

// ── Formatting / parsing helpers ───────────────────────────────────────────

/** Render a percent for a text field: whole numbers without a decimal, else one decimal place. */
private fun fmtPct(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.fmt1()

/** One-decimal-place rendering without java.lang String.format (JVM-only) — KMP-safe. */
private fun Double.fmt1(): String {
    val rounded = kotlin.math.round(this * 10).toLong()
    val sign = if (rounded < 0) "-" else ""
    val abs = if (rounded < 0) -rounded else rounded
    return "$sign${abs / 10}.${abs % 10}"
}

private fun String.toIntOr(previous: Int): Int = trim().filter { it.isDigit() }.toIntOrNull() ?: previous
private fun String.toLongOr(previous: Long): Long = trim().filter { it.isDigit() }.toLongOrNull() ?: previous
private fun String.toMoneyOr(previous: Money): Money =
    trim().filter { it.isDigit() }.toLongOrNull()?.let { Money.fromRupees(it) } ?: previous

/** Whole-rupee text for a [Money] (paise dropped; the operator types rupees). */
private fun Money.rupeesText(): String = if (paise == 0L) "" else (paise / 100L).toString()
