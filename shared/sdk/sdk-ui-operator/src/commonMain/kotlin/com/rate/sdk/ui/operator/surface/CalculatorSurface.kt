package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rate.core.money.formatRupees
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisHDivider
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.components.showToast
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.viewmodel.CalculatorViewModel

/**
 * Operator rate calculator — relocated from aegis CalculatorSurface. Pick a plan, enter member /
 * SI / zone / tenure / payment mode and price against the server engine via [QuoteApi]; show the
 * full result ledger; optionally persist the quote.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorSurface(
    configAdmin: ConfigAdminApi,
    quotes: QuoteApi,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vm = remember { CalculatorViewModel(configAdmin, quotes, scope) }
    LaunchedEffect(Unit) { vm.loadPlans() }

    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text("Rate Calculator", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
        Text(
            "Price any plan against the live server engine.",
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
        )

        AegisCard(title = "Inputs") {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Text("Plan", style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    vm.plans.forEach { p ->
                        AegisChip(label = p.name, selected = p.id == vm.selectedPlanId, onClick = { vm.selectPlan(p.id) })
                    }
                    if (vm.plans.isEmpty()) {
                        Text("No plans loaded — check the server.", style = AegisTypography.small.copy(color = AegisColors.textTertiary))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisInput(vm.primaryAge, { vm.setAge(it) }, "Primary age", modifier = Modifier.weight(1f))
                    AegisInput(vm.sumInsured, { vm.setSumInsured(it) }, "Sum insured", prefix = "₹", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisInput(vm.familyType, { vm.setFamilyType(it) }, "Family type", modifier = Modifier.weight(1f))
                    AegisInput(vm.zone, { vm.setZone(it) }, "Zone", modifier = Modifier.weight(1f))
                }
                Text("Tenure", style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    Tenure.entries.forEach { t ->
                        AegisChip(label = t.label, selected = vm.tenure == t, onClick = { vm.setTenure(t) })
                    }
                }
                Text("Payment mode", style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    PaymentMode.entries.forEach { m ->
                        AegisChip(label = m.label, selected = vm.paymentMode == m, onClick = { vm.setPaymentMode(m) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                    AegisButton(
                        label = "Calculate",
                        onClick = { vm.calculate() },
                        loading = vm.calculating,
                    )
                    AegisButton(
                        label = "Calculate & Save",
                        onClick = {
                            vm.saveQuote { id ->
                                if (id != null) showToast("Saved quote $id", CalloutKind.SUCCESS)
                            }
                        },
                        variant = AegisButtonVariant.Secondary,
                    )
                }
            }
        }

        vm.error?.let { AegisCallout(kind = CalloutKind.DANGER, title = "Calculation error", body = it) }

        vm.result?.let { r ->
            AegisCard(title = "Result", subtitle = "Engine ${r.engineVersion} · rate table ${r.rateTableVersion}") {
                Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    ResultRow("Base premium", formatRupees(r.basePremiumTotal))
                    ResultRow("Add-on covers", formatRupees(r.totalAddons))
                    ResultRow("UW loading", formatRupees(r.uwLoadingAmount))
                    ResultRow("Total before discount", formatRupees(r.totalBeforeDiscount))
                    ResultRow("Discounts", "− " + formatRupees(r.totalDiscountAmount))
                    ResultRow("Total after discount", formatRupees(r.totalAfterDiscount))
                    AegisHDivider()
                    ResultRow("GST (${(r.gstRate * 100).toInt()}%)", formatRupees(r.gstAmount))
                    ResultRow("Total incl. GST", formatRupees(r.totalIncludingGst), bold = true)
                    if (r.instalmentCount > 1) {
                        ResultRow("Instalment (${r.instalmentCount}×)", formatRupees(r.instalmentPremium))
                    }
                    if (r.coverBreakdown.isNotEmpty()) {
                        AegisHDivider()
                        Text("Cover breakdown", style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
                        r.coverBreakdown.forEach { c ->
                            ResultRow(c.coverName, (if (c.isDiscount) "− " else "") + formatRupees(c.totalPremium))
                        }
                    }
                    if (!r.isValid) {
                        AegisCallout(
                            kind = CalloutKind.WARN,
                            title = "Quote is invalid",
                            body = r.validationErrors.joinToString("\n").ifBlank { "Engine returned isValid=false." },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AegisTypography.body.copy(color = AegisColors.textSecondary))
        Text(
            value,
            style = if (bold) AegisTypography.money.copy(color = AegisColors.textPrimary, fontWeight = AegisTypography.h3.fontWeight)
            else AegisTypography.money.copy(color = AegisColors.textBody),
        )
    }
}
