package com.rate.desktop.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.desktop.api.ApiClient
import com.rate.desktop.navigation.Screen
import com.rate.desktop.ui.components.*
import com.rate.desktop.ui.theme.DiscountColor
import com.rate.desktop.ui.theme.LoadingColor
import com.rate.domain.data.CoverMeta
import com.rate.domain.model.*

// ── Cover grouping ─────────────────────────────────────────────────────────
// Each group: display name → set of cover IDs that belong to it
private val COVER_GROUPS = listOf(
    "Hospitalization" to setOf(
        "day1_instant", "chronic_instant", "consumables_list1", "ped_waiting",
        "specific_illness_waiting", "room_rent_mod", "disease_sublimit",
        "pre_post_hosp", "consumable_plus", "home_care", "air_ambulance", "post_discharge_care"
    ),
    "Claim & Restoration" to setOf(
        "loyalty_bonus", "double_cover_7yr", "infinite_claim", "restoration_plus",
        "tenure_wise", "donor_plus", "modern_treatment_plus", "durable_medical"
    ),
    "Family & Maternity" to setOf(
        "spouse_protect", "child_protect", "maternity_newborn", "maternity_fixed",
        "post_delivery_care", "infertility", "surrogate_mother", "oocyte_donor"
    ),
    "Health & Wellness" to setOf(
        "daily_hospital_cash", "convalescence", "compassionate", "personal_accident",
        "adventure_sports", "chronic_management", "fitness_plus", "wellness_package",
        "second_opinion", "pru_health_specialist", "female_vaccination",
        "advance_health_checkup", "cashless_opd"
    ),
    "Critical & Cancer" to setOf(
        "critical_illness", "cancer_booster", "cancer_screening", "enhanced_geo"
    ),
    "Special Benefits" to setOf(
        "prudential_healthy", "premium_return"
    ),
)

// ── SI formatting (Indian system) ─────────────────────────────────────────
private fun Long.toSILabel(): String {
    val cr = this / 10_000_000.0
    val l  = this / 100_000.0
    return when {
        this >= 10_000_000L -> "₹ ${if (cr % 1 == 0.0) cr.toInt() else cr} Cr"
        this >= 100_000L    -> "₹ ${if (l  % 1 == 0.0) l.toInt()  else l } L"
        else                -> "₹ %,d".format(this)
    }
}

// ── Plan type chip colours ─────────────────────────────────────────────────
private fun planChipColor(pt: PlanType): Color = when (pt) {
    PlanType.DOMESTIC             -> Color(0xFFE8F5E9)
    PlanType.DOMESTIC_FLAGSHIP    -> Color(0xFFEDE7F6)
    PlanType.DOMESTIC_SENIOR      -> Color(0xFFFBE9E7)
    PlanType.DOMESTIC_SUBSTANDARD -> Color(0xFFFCE4EC)
    PlanType.DOMESTIC_POSP        -> Color(0xFFE0F2F1)
    PlanType.GLOBAL               -> Color(0xFFE3F2FD)
    PlanType.GLOBAL_PLUS          -> Color(0xFFCE93D8).copy(alpha = 0.4f)
}
private fun planChipTextColor(pt: PlanType): Color = when (pt) {
    PlanType.DOMESTIC             -> Color(0xFF1B5E20)
    PlanType.DOMESTIC_FLAGSHIP    -> Color(0xFF4A148C)
    PlanType.DOMESTIC_SENIOR      -> Color(0xFFBF360C)
    PlanType.DOMESTIC_SUBSTANDARD -> Color(0xFF880E4F)
    PlanType.DOMESTIC_POSP        -> Color(0xFF004D40)
    PlanType.GLOBAL               -> Color(0xFF0D47A1)
    PlanType.GLOBAL_PLUS          -> Color(0xFF4A148C)
}

// ── Screen ────────────────────────────────────────────────────────────────

@Composable
fun CalculatorScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    val vm = remember { CalculatorViewModel(client) }
    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    Scaffold(
        bottomBar = { AppNavBar(current = Screen.Calculator, onNavigate = onNavigate) }
    ) { padding ->
        if (vm.loading && vm.plans.isEmpty()) { LoadingOverlay(); return@Scaffold }

        Row(Modifier.fillMaxSize().padding(padding)) {

            // ── LEFT: inputs ───────────────────────────────────────────────
            Column(
                Modifier
                    .weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Quote Calculator", style = MaterialTheme.typography.headlineSmall)
                vm.error?.let { ErrorBanner(it) }

                // ── Policy details ─────────────────────────────────────────
                SectionCard("Policy Details") {
                    // Plan dropdown
                    LabelledDropdown(
                        label    = "Plan",
                        options  = vm.plans,
                        selected = vm.selectedPlan,
                        onSelect = { vm.onPlanSelected(it) },
                        display  = { it.name }
                    )
                    // Plan context chips
                    vm.selectedPlan?.let { plan ->
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            InfoChip(
                                plan.planType.displayName,
                                planChipColor(plan.planType),
                                planChipTextColor(plan.planType)
                            )
                            if (plan.geographyScope != GeographyScope.DOMESTIC) {
                                InfoChip(plan.geographyScope.label, Color(0xFFB3E5FC), Color(0xFF01579B))
                            }
                            InfoChip("${plan.availableZones.size} zone(s)")
                            InfoChip("Max disc ${(plan.maxDiscountCap * 100).toInt()}%")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Member ages
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Primary Age", vm.primaryAge,
                            { vm.primaryAge = it }, Modifier.weight(1f))
                        NumberField("Spouse Age (opt)", vm.spouseAge,
                            { vm.spouseAge = it }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value         = vm.childAges,
                        onValueChange = { vm.childAges = it },
                        label         = { Text("Child Ages (comma-separated)") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    // Multi-individual toggle + derived family type
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked         = vm.isMultiIndividual,
                            onCheckedChange = { vm.toggleMultiIndividual(it) }
                        )
                        Text(
                            "Multi-Individual Policy",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    vm.selectedFT?.let { ft ->
                        val desc = if (ft.code == "multi") "Multi-Individual"
                        else "${ft.adultCount} adult${if (ft.adultCount > 1) "s" else ""}" +
                             if (ft.childCount > 0) " + ${ft.childCount} child${if (ft.childCount > 1) "ren" else ""}" else ""
                        Text(
                            "Family Type: ${ft.code}  ($desc)",
                            style  = MaterialTheme.typography.bodySmall,
                            color  = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Sum Insured — Indian formatting
                    LabelledDropdown(
                        label    = "Sum Insured",
                        options  = vm.selectedPlan?.availableSumInsureds ?: vm.domesticSIs,
                        selected = vm.selectedSI,
                        onSelect = { vm.selectedSI = it },
                        display  = { it.toSILabel() }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Pincode → zone auto-detection
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value         = vm.pincode,
                            onValueChange = { if (it.length <= 6) vm.pincode = it },
                            label         = { Text("Pincode (auto-detect zone)") },
                            singleLine    = true,
                            modifier      = Modifier.weight(1f)
                        )
                        val cityHint = vm.detectedCity
                        if (cityHint != null) {
                            Text(
                                cityHint,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LabelledDropdown(
                        label    = "Zone",
                        options  = Zone.entries,
                        selected = vm.selectedZone,
                        onSelect = { vm.selectedZone = it },
                        display  = { it.label }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Tenure row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelledDropdown(
                            label    = "Policy Tenure",
                            options  = Tenure.entries,
                            selected = vm.selectedTenure,
                            onSelect = { t ->
                                vm.selectedTenure = t
                                // Auto-sync payment tenure for non-instalment modes
                                if (vm.selectedPayMode == PaymentMode.SINGLE_PREMIUM ||
                                    vm.selectedPayMode == PaymentMode.ANNUAL) {
                                    vm.selectedPayTenure = t
                                }
                            },
                            display  = { it.label },
                            modifier = Modifier.weight(1f)
                        )
                        LabelledDropdown(
                            label    = "Payment Mode",
                            options  = PaymentMode.entries,
                            selected = vm.selectedPayMode,
                            onSelect = { mode ->
                                vm.selectedPayMode = mode
                                // Auto-sync payment tenure for non-instalment modes
                                if (mode == PaymentMode.SINGLE_PREMIUM ||
                                    mode == PaymentMode.ANNUAL) {
                                    vm.selectedPayTenure = vm.selectedTenure
                                }
                            },
                            display  = { it.label },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Payment Tenure — only relevant for Monthly/Quarterly/Half-Yearly
                    val showPayTenure = vm.selectedPayMode != PaymentMode.SINGLE_PREMIUM &&
                                       vm.selectedPayMode != PaymentMode.ANNUAL
                    if (showPayTenure) {
                        Spacer(Modifier.height(8.dp))
                        LabelledDropdown(
                            label    = "Payment Tenure",
                            options  = Tenure.entries,
                            selected = vm.selectedPayTenure,
                            onSelect = { vm.selectedPayTenure = it },
                            display  = { it.label }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // UW loading
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumberField(
                            label    = "UW Loading %",
                            value    = vm.uwLoading,
                            onChange = { vm.uwLoading = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%  (Underwriting loading applied on top of base)",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Grouped optional covers ────────────────────────────────
                COVER_GROUPS.forEach { (groupName, groupIds) ->
                    val groupCovers = vm.covers.filter { it.id in groupIds }
                    if (groupCovers.isEmpty()) return@forEach

                    val selectedCount = groupCovers.count { it.id in vm.selectedCoverIds }
                    val badge = if (selectedCount > 0) "$selectedCount / ${groupCovers.size}" else null
                    val startOpen = selectedCount > 0 || groupName == "Hospitalization"

                    CollapsibleSectionCard(
                        title         = groupName,
                        badge         = badge,
                        startExpanded = startOpen
                    ) {
                        groupCovers.forEach { cover ->
                            CoverToggleRow(cover, vm, isDiscount = false)
                        }
                    }
                }

                // ── Deductibles & Adjustments ──────────────────────────────
                if (vm.deductibleCovers.isNotEmpty()) {
                    val selectedDeds = vm.deductibleCovers.count { it.id in vm.selectedCoverIds }
                    CollapsibleSectionCard(
                        title         = "Deductibles & Adjustments",
                        badge         = if (selectedDeds > 0) "$selectedDeds / ${vm.deductibleCovers.size}" else null,
                        startExpanded = selectedDeds > 0
                    ) {
                        Text(
                            "Select a deductible to reduce your premium",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        vm.deductibleCovers.forEach { cover ->
                            CoverToggleRow(cover, vm, isDiscount = false)
                        }
                    }
                }

                // ── Discounts ─────────────────────────────────────────────
                if (vm.discounts.isNotEmpty()) {
                    val selectedDiscs = vm.discounts.count { it.id in vm.selectedDiscountIds }
                    CollapsibleSectionCard(
                        title         = "Discounts",
                        badge         = if (selectedDiscs > 0) "$selectedDiscs / ${vm.discounts.size}" else null,
                        startExpanded = selectedDiscs > 0
                    ) {
                        vm.discounts.forEach { disc ->
                            CoverToggleRow(disc, vm, isDiscount = true)
                        }
                    }
                }

                // ── Calculate button ──────────────────────────────────────
                Button(
                    onClick  = { vm.calculate() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = !vm.loading
                ) {
                    if (vm.loading)
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    else
                        Text("Calculate Premium", style = MaterialTheme.typography.titleSmall)
                }

                Spacer(Modifier.height(16.dp))
            }

            VerticalDivider(Modifier.fillMaxHeight().width(1.dp))

            // ── RIGHT: results ─────────────────────────────────────────────
            Column(
                Modifier
                    .weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Premium Breakdown", style = MaterialTheme.typography.headlineSmall)

                val res = vm.result
                if (res == null) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Fill in the details on the left\nand click Calculate Premium",
                            color     = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Validation errors always shown at top
                    if (res.validationErrors.isNotEmpty()) {
                        WarningBanner(
                            "⚠ Business rule violations:\n" +
                            res.validationErrors.joinToString("\n") { "• $it" }
                        )
                    }

                    if (!res.isValid) return@Column

                    // Tenure comparison table
                    if (vm.tenureResults.isNotEmpty()) {
                        TenureComparisonTable(vm.tenureResults)
                    }
                    // Detailed breakdown
                    PremiumResultPanel(res)
                }
            }
        }
    }
}

// ── Cover toggle row ──────────────────────────────────────────────────────

@Composable
private fun CoverToggleRow(cover: CoverMeta, vm: CalculatorViewModel, isDiscount: Boolean) {
    val coverId = cover.id
    val checked = if (isDiscount) coverId in vm.selectedDiscountIds else coverId in vm.selectedCoverIds
    val params  = vm.getParam(coverId)
    val p1Opts  = cover.param1?.options ?: emptyList()
    val p2Opts  = cover.param2?.options ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked         = checked,
                onCheckedChange = { on ->
                    vm.toggleCover(coverId, isDiscount, on)
                    if (on) {
                        if (p1Opts.isNotEmpty()) vm.updateParam1(coverId, p1Opts[0])
                        if (p2Opts.isNotEmpty()) vm.updateParam2(coverId, p2Opts[0])
                    }
                }
            )
            Column(Modifier.weight(1f)) {
                Text(cover.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    cover.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (checked) {
            val p1 = cover.param1
            val p2 = cover.param2
            if (p1 != null && p1Opts.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 40.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabelledDropdown(
                        label    = p1.name,
                        options  = p1Opts,
                        selected = params.param1 ?: p1Opts[0],
                        onSelect = { vm.updateParam1(coverId, it) },
                        modifier = Modifier.weight(1f)
                    )
                    if (p2 != null && p2Opts.isNotEmpty()) {
                        LabelledDropdown(
                            label    = p2.name,
                            options  = p2Opts,
                            selected = params.param2 ?: p2Opts[0],
                            onSelect = { vm.updateParam2(coverId, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ── Premium result panel ──────────────────────────────────────────────────

@Composable
private fun PremiumResultPanel(res: QuoteResult) {
    // Summary
    SectionCard("Summary") {
        PremiumRow("Base Premium", res.basePremiumTotal)

        val addons = res.coverBreakdown.filter { !it.isDiscount }
        if (addons.isNotEmpty()) PremiumRow("Optional Covers", res.totalAddons)

        if (res.uwLoadingAmount > 0)
            PremiumRow("UW Loading (+)", res.uwLoadingAmount, LoadingColor)

        if (res.totalDiscountAmount != 0.0)
            PremiumRow("Total Discounts", res.totalDiscountAmount, DiscountColor)

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        PremiumRow("Annual Premium", res.totalAfterDiscount, MaterialTheme.colorScheme.primary)

        if (res.instalmentLoadingAmount > 0)
            PremiumRow("Instalment Loading (+)", res.instalmentLoadingAmount, LoadingColor)
        if (res.instalmentCount > 1)
            PremiumRow("Per Instalment (×${res.instalmentCount})", res.instalmentPremium)
    }

    // Year-wise breakdown
    if (res.yearlyBreakdown.isNotEmpty()) {
        SectionCard("Year-wise Breakdown") {
            res.yearlyBreakdown.forEach { yr ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Yr ${yr.year}  Age ${yr.age}  (${yr.ageBand})",
                        style    = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "₹ %,.0f".format(yr.subtotal),
                        style    = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
                if (yr.basePremium > 0) {
                    Text(
                        "  Base ₹%,.0f".format(yr.basePremium),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    // Cover add-ons
    val addons = res.coverBreakdown.filter { !it.isDiscount }
    if (addons.isNotEmpty()) {
        SectionCard("Optional Cover Loadings") {
            addons.forEach { cb ->
                PremiumRow(cb.coverName, cb.totalPremium, LoadingColor)
            }
        }
    }

    // Deductibles / adjustments (negative covers)
    val adjustments = res.coverBreakdown.filter { it.isDiscount }
    if (adjustments.isNotEmpty()) {
        SectionCard("Deductibles & Adjustments") {
            adjustments.forEach { cb ->
                PremiumRow(cb.coverName, cb.totalPremium, DiscountColor)
            }
        }
    }

    // Discounts
    if (res.discountBreakdown.isNotEmpty()) {
        SectionCard("Discount Breakdown") {
            res.discountBreakdown.forEach { db ->
                PremiumRow(
                    "${db.discountName} (${(db.rateApplied * 100).toInt()}%)",
                    db.amount,
                    DiscountColor
                )
            }
        }
    }
}

// ── Tenure comparison table ───────────────────────────────────────────────

@Composable
private fun TenureComparisonTable(results: Map<Tenure, QuoteResult>) {
    val tenures = Tenure.entries

    SectionCard("Premium Comparison — All Tenures") {
        // Header
        Row(Modifier.fillMaxWidth()) {
            TableCell("", weight = 1.6f, header = true)
            tenures.forEach { t ->
                TableCell(
                    t.label.replace(" Years", "yr").replace(" Year", "yr"),
                    weight = 1f, header = true
                )
            }
        }
        HorizontalDivider()

        // Base
        TableRow("Base Premium", tenures, results) { it.basePremiumTotal }

        // Add-ons
        TableRow("Add-ons", tenures, results) { it.totalAddons }

        // Discounts
        Row(Modifier.fillMaxWidth()) {
            TableCell("Discounts", weight = 1.6f, textColor = DiscountColor)
            tenures.forEach { t ->
                val disc = results[t]?.totalDiscountAmount ?: 0.0
                TableCell(
                    "₹%,.0f".format(disc), weight = 1f,
                    textColor = if (disc != 0.0) DiscountColor else Color.Unspecified
                )
            }
        }

        HorizontalDivider()

        // Annual (highlighted)
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            TableCell("Annual Premium", weight = 1.6f, header = true,
                textColor = MaterialTheme.colorScheme.primary)
            tenures.forEach { t ->
                TableCell(
                    "₹%,.0f".format(results[t]?.totalAfterDiscount ?: 0.0),
                    weight    = 1f,
                    textColor = MaterialTheme.colorScheme.primary,
                    header    = true
                )
            }
        }

        // Monthly EMI
        Row(Modifier.fillMaxWidth()) {
            TableCell("Monthly EMI", weight = 1.6f)
            tenures.forEach { t ->
                val r   = results[t]
                val emi = if ((r?.instalmentCount ?: 0) > 1) r?.instalmentPremium ?: 0.0 else 0.0
                TableCell(if (emi > 0) "₹%,.0f".format(emi) else "—", weight = 1f)
            }
        }

        // Effective per year
        Row(Modifier.fillMaxWidth()) {
            TableCell("Effective / yr", weight = 1.6f,
                textColor = MaterialTheme.colorScheme.outline)
            tenures.forEach { t ->
                val eff = (results[t]?.totalAfterDiscount ?: 0.0) / t.years
                TableCell("₹%,.0f".format(eff), weight = 1f,
                    textColor = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TableRow(
    label:   String,
    tenures: List<Tenure>,
    results: Map<Tenure, QuoteResult>,
    value:   (QuoteResult) -> Double
) {
    Row(Modifier.fillMaxWidth()) {
        TableCell(label, weight = 1.6f)
        tenures.forEach { t -> TableCell("₹%,.0f".format(results[t]?.let(value) ?: 0.0), weight = 1f) }
    }
}

@Composable
private fun RowScope.TableCell(
    text:      String,
    weight:    Float,
    header:    Boolean = false,
    textColor: Color   = Color.Unspecified
) {
    Text(
        text,
        modifier  = Modifier.weight(weight).padding(horizontal = 4.dp, vertical = 4.dp),
        style     = if (header) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
        color     = textColor
    )
}
