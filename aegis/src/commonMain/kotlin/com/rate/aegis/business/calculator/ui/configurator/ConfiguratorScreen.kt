package com.rate.aegis.business.calculator.ui.configurator

import com.rate.domain.money.formatRupees
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.DeepLink
import com.rate.aegis.LocalAegisDeepLink
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.business.calculator.navigation.Screen
import com.rate.aegis.business.calculator.ui.components.*
import com.rate.aegis.util.buildCsv
import com.rate.aegis.util.saveCsv
import com.rate.aegis.util.todayIsoDate
import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// ── Helpers ───────────────────────────────────────────────────────────────

private fun Long.toSILabel(): String {
    val cr = this / 10_000_000.0
    val l  = this / 100_000.0
    return when {
        this >= 10_000_000L -> "₹${if (cr % 1 == 0.0) cr.toInt() else cr}Cr"
        this >= 100_000L    -> "₹${if (l  % 1 == 0.0) l.toInt()  else l }L"
        else                -> formatRupees(this.toDouble())
    }
}

private fun planAccentColor(type: PlanType): Color = when (type) {
    PlanType.DOMESTIC             -> Color(0xFF2E7D32)
    PlanType.DOMESTIC_FLAGSHIP    -> Color(0xFF6A1B9A)
    PlanType.DOMESTIC_SENIOR      -> Color(0xFFE65100)
    PlanType.DOMESTIC_SUBSTANDARD -> Color(0xFFC62828)
    PlanType.DOMESTIC_POSP        -> Color(0xFF00695C)
    PlanType.GLOBAL               -> Color(0xFF1565C0)
    PlanType.GLOBAL_PLUS          -> Color(0xFF4527A0)
}

private fun planBadgeColor(type: PlanType): Color = when (type) {
    PlanType.DOMESTIC             -> Color(0xFFE8F5E9)
    PlanType.DOMESTIC_FLAGSHIP    -> Color(0xFFEDE7F6)
    PlanType.DOMESTIC_SENIOR      -> Color(0xFFFBE9E7)
    PlanType.DOMESTIC_SUBSTANDARD -> Color(0xFFFCE4EC)
    PlanType.DOMESTIC_POSP        -> Color(0xFFE0F2F1)
    PlanType.GLOBAL               -> Color(0xFFE3F2FD)
    PlanType.GLOBAL_PLUS          -> Color(0xFFEDE7F6)
}

/**
 * Pick the next unused plan id when duplicating [baseId].
 *
 * Strips trailing digits from [baseId] to find a stem, then bumps the trailing
 * number until the id is free. e.g. `PHI_FLAGSHIP1` → `PHI_FLAGSHIP2`, and if
 * `PHI_FLAGSHIP2` already exists, it keeps walking forward. Ids with no
 * trailing digit ([PHI_GLOBAL_EXCL]) start from `2` so the original `1` slot
 * is reserved for the source plan.
 */
private fun nextAvailableId(baseId: String, existing: List<Plan>): String {
    val existingIds = existing.map { it.id }.toSet()
    val stem = baseId.trimEnd { it.isDigit() }
    val startNum = baseId.removePrefix(stem).toIntOrNull() ?: 1
    var n = startNum + 1
    while ("$stem$n" in existingIds) n++
    return "$stem$n"
}

private val FILTER_ALL         = "All"
private val FILTER_DOMESTIC    = "Domestic"
private val FILTER_GLOBAL      = "Global"
private val FILTER_GLOBAL_PLUS = "Global Plus"

// ── Main Screen ───────────────────────────────────────────────────────────

@Composable
fun ConfiguratorScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    Scaffold(
        bottomBar = { AppNavBar(current = Screen.Configurator, onNavigate = onNavigate) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ConfiguratorBody(client)
        }
    }
}

/**
 * Configurator body — same plan-CRUD logic as [ConfiguratorScreen] but with no
 * Scaffold / bottom-nav chrome, so the Aegis BUSINESS shell can embed it
 * inside its own layout without nested navigation bars.
 */
@Composable
fun ConfiguratorBody(client: ApiClient) {
    val scope        = rememberCoroutineScope()
    var plans        by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    var filter       by remember { mutableStateOf(FILTER_ALL) }
    var editPlan     by remember { mutableStateOf<Plan?>(null) }
    var showDialog   by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true; error = null
        try { plans = client.getPlans() }
        catch (e: Exception) { error = "Cannot reach server: ${e.message}" }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    // Deep-link from command palette: open the edit dialog on a specific plan once
    // the plan list has loaded. Keying on `plans` re-runs the effect when the
    // server fetch lands so a late deep-link still resolves.
    val deepLink = LocalAegisDeepLink.current
    LaunchedEffect(deepLink.value.planId, plans) {
        val target = deepLink.value.planId
        if (target != null) {
            val match = plans.firstOrNull { it.id == target }
            if (match != null) {
                editPlan = match
                showDialog = true
                deepLink.value = DeepLink.NONE
            }
        }
    }

    // Filtered view
    val displayed = remember(plans, filter) {
        when (filter) {
            FILTER_DOMESTIC    -> plans.filter { it.planType.name.startsWith("DOMESTIC") }
            FILTER_GLOBAL      -> plans.filter { it.planType == PlanType.GLOBAL }
            FILTER_GLOBAL_PLUS -> plans.filter { it.planType == PlanType.GLOBAL_PLUS }
            else               -> plans
        }
    }

    // Stats
    val active   = plans.count { it.isActive }
    val domestic = plans.count { it.planType.name.startsWith("DOMESTIC") }
    val global   = plans.count { it.planType == PlanType.GLOBAL || it.planType == PlanType.GLOBAL_PLUS }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Product Configurator",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "View and configure plans loaded from Excel import",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    // Export the *currently filtered* plan list. Sibling of
                    // Refresh — same OutlinedButton chrome so the header stays
                    // visually unified (the configurator body is still
                    // Material3, not Aegis Foundations, hence no AegisButton).
                    OutlinedButton(
                        onClick = {
                            val csv = buildCsv(
                                headers = listOf(
                                    "ID", "Name", "Type", "Lifecycle", "Active",
                                    "Min age", "Max age", "GST rate", "Max discount cap",
                                    "SI grid", "Zones"
                                ),
                                rows = displayed.map { p ->
                                    listOf(
                                        p.id, p.name, p.planType.name, p.lifecycle.name, p.isActive,
                                        p.minAge, p.maxAge, p.gstRate, p.maxDiscountCap,
                                        p.availableSumInsureds.joinToString("|"),
                                        p.availableZones.joinToString("|")
                                    )
                                }
                            )
                            saveCsv("aegis-plans-${todayIsoDate()}.csv", csv)
                        },
                        enabled = displayed.isNotEmpty()
                    ) {
                        Text("Export CSV")
                    }
                    OutlinedButton(
                        onClick = { scope.launch { refresh() } },
                        enabled = !loading
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
            }

            error?.let { ErrorBanner(it) }

            // ── Stats ───────────────────────────────────────────────────────
            if (plans.isNotEmpty()) {
                StatsRow(
                    total    = plans.size,
                    active   = active,
                    domestic = domestic,
                    global   = global
                )
            }

            // ── Filter chips ────────────────────────────────────────────────
            if (plans.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FILTER_ALL         to plans.size,
                        FILTER_DOMESTIC    to domestic,
                        FILTER_GLOBAL      to plans.count { it.planType == PlanType.GLOBAL },
                        FILTER_GLOBAL_PLUS to plans.count { it.planType == PlanType.GLOBAL_PLUS }
                    ).forEach { (label, count) ->
                        FilterChip(
                            selected = filter == label,
                            onClick  = { filter = label },
                            label    = { Text("$label ($count)") }
                        )
                    }
                }
            }

            // ── Empty states ────────────────────────────────────────────────
            if (!loading && plans.isEmpty() && error == null) {
                EmptyState()
            } else if (displayed.isEmpty() && plans.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No plans in this category", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                // ── Plan grid ────────────────────────────────────────────────
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayed, key = { it.id }) { plan ->
                        PlanCard(
                            plan           = plan,
                            onEdit         = { editPlan = plan; showDialog = true },
                            onToggleActive = {
                                scope.launch {
                                    try {
                                        client.savePlan(plan.copy(isActive = !plan.isActive))
                                        refresh()
                                    } catch (e: Exception) { error = e.message }
                                }
                            },
                            onDuplicate    = {
                                // Pre-fill the dialog with a clone of the source plan.
                                // The new id is collision-checked against the full plan
                                // list (not just the filtered view) so we never collide
                                // with a hidden plan. Lifecycle is forced to DRAFT so a
                                // duplicate never auto-goes-live, regardless of source.
                                editPlan = plan.copy(
                                    id        = nextAvailableId(plan.id, plans),
                                    name      = "${plan.name} (copy)",
                                    lifecycle = PlanLifecycle.DRAFT
                                )
                                showDialog = true
                            }
                        )
                    }
                }
            }
        }

        // ── Edit dialog ──────────────────────────────────────────────────────
        if (showDialog) {
            PlanEditDialog(
                client    = client,
                initial   = editPlan,
                onSave    = { p ->
                    scope.launch {
                        try { client.savePlan(p); showDialog = false; refresh() }
                        catch (e: Exception) { error = e.message }
                    }
                },
                onDismiss = { showDialog = false }
            )
        }
}

// ── Stats Row ─────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(total: Int, active: Int, domestic: Int, global: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatTile("Total Plans",  "$total",    MaterialTheme.colorScheme.primary)
            StatDivider()
            StatTile("Active",       "$active",   Color(0xFF2E7D32))
            StatDivider()
            StatTile("Domestic",     "$domestic", Color(0xFF1565C0))
            StatDivider()
            StatTile("Global",       "$global",   Color(0xFF4527A0))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)))
}

// ── Plan Card ─────────────────────────────────────────────────────────────

@Composable
private fun PlanCard(
    plan: Plan,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDuplicate: () -> Unit
) {
    val accent = planAccentColor(plan.planType)
    val siMin  = plan.availableSumInsureds.minOrNull()
    val siMax  = plan.availableSumInsureds.maxOrNull()
    val siRange = if (siMin != null && siMax != null) "${siMin.toSILabel()} – ${siMax.toSILabel()}" else "—"

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (plan.isActive) MaterialTheme.colorScheme.surface
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Coloured accent bar
            Box(Modifier.width(5.dp).fillMaxHeight().background(
                if (plan.isActive) accent else accent.copy(alpha = 0.4f)
            ))

            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // ── Name + Active toggle ─────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        plan.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (plan.isActive) MaterialTheme.colorScheme.onSurface
                                     else MaterialTheme.colorScheme.outline,
                        modifier   = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (plan.isActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (plan.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                        )
                        Switch(
                            checked         = plan.isActive,
                            onCheckedChange = { onToggleActive() },
                            modifier        = Modifier.height(20.dp).padding(0.dp),
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor       = Color.White,
                                checkedTrackColor       = Color(0xFF2E7D32),
                                uncheckedThumbColor     = Color.White,
                                uncheckedTrackColor     = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // ── Type chips ───────────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeChip(plan.planType.displayName, accent)
                    if (plan.geographyScope != GeographyScope.DOMESTIC) {
                        TypeChip(plan.geographyScope.label, Color(0xFF01579B))
                    }
                    if (plan.underwritingCategory != UnderwritingCategory.STANDARD) {
                        TypeChip(plan.underwritingCategory.name, Color(0xFFC62828))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Key metrics ──────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricItem("Sum Insured", siRange, Modifier.weight(1f))
                    MetricItem("Age Range", "${plan.minAge} – ${plan.maxAge} yrs", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricItem(
                        "Zones",
                        "${plan.availableZones.size} zone${if (plan.availableZones.size > 1) "s" else ""}",
                        Modifier.weight(1f)
                    )
                    MetricItem("Family Types", "${plan.availableFamilyTypes.size} types", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricItem("Co-Pay Table", plan.coPaymentTable.name, Modifier.weight(1f))
                    MetricItem("Max Discount", "${(plan.maxDiscountCap * 100).toInt()}%", Modifier.weight(1f))
                }

                // ── Action buttons ───────────────────────────────────────────
                // Edit (primary) + Duplicate (secondary) sit on the right edge
                // of the card. The active-toggle Switch already lives in the
                // header row above, so logically Duplicate is sandwiched
                // between Edit and the Switch as the prompt requested.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick  = onEdit,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Edit", Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick  = onDuplicate,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Duplicate", Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Duplicate", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ── Empty State ───────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("No Plans Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Import the Rate Calculator v13.0 Excel file to load all 14 plans.\n" +
                "Go to the Import tab, select the Excel file, and click Upload & Import.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Expected: 14 plans  ·  54 optional covers  ·  19 sum insured tiers  ·  28 business rules",
                style     = MaterialTheme.typography.labelMedium,
                color     = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Edit Dialog ───────────────────────────────────────────────────────────

@Composable
private fun PlanEditDialog(
    client: ApiClient,
    initial: Plan?,
    onSave: (Plan) -> Unit,
    onDismiss: () -> Unit
) {
    var name        by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var minAge      by remember { mutableStateOf(initial?.minAge?.toString() ?: "5") }
    var maxAge      by remember { mutableStateOf(initial?.maxAge?.toString() ?: "99") }
    var maxDiscount by remember { mutableStateOf(((initial?.maxDiscountCap ?: 0.30) * 100).toInt().toString()) }
    var isActive    by remember { mutableStateOf(initial?.isActive ?: true) }
    var allowedCovers by remember(initial) { mutableStateOf(initial?.allowedCoverIds ?: emptySet()) }

    // Covers shown in the picker — all non-discount entries, sorted by name.
    val coverPickerItems = remember {
        CoverCatalog.ALL.filter { !it.isDiscount }.sortedBy { it.name }
    }
    val coverPickerIds = remember(coverPickerItems) { coverPickerItems.map { it.id }.toSet() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier         = Modifier.width(560.dp),
        title = {
            Column {
                Text(
                    if (initial == null) "New Plan" else "Edit — ${initial.name}",
                    style = MaterialTheme.typography.titleLarge
                )
                if (initial != null) {
                    Text(
                        "ID: ${initial.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Business configuration (editable) ────────────────────────
                Text(
                    "Business Configuration",
                    style     = MaterialTheme.typography.labelLarge,
                    color     = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Display Name") },
                    modifier      = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description (shown to customers)") },
                    minLines      = 2,
                    modifier      = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = minAge,
                        onValueChange = { if (it.all { c -> c.isDigit() }) minAge = it },
                        label         = { Text("Min Age") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = maxAge,
                        onValueChange = { if (it.all { c -> c.isDigit() }) maxAge = it },
                        label         = { Text("Max Age") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = maxDiscount,
                        onValueChange = { if (it.all { c -> c.isDigit() }) maxDiscount = it },
                        label         = { Text("Max Discount %") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f)
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Plan Active", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (isActive) "This plan is available for quoting"
                            else "This plan is hidden from the calculator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked         = isActive,
                        onCheckedChange = { isActive = it },
                        colors          = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF2E7D32)
                        )
                    )
                }

                // ── Rate configuration (read-only, from Excel) ────────────────
                if (initial != null) {
                    HorizontalDivider()

                    Text(
                        "Rate Configuration  (set by Excel import — read only)",
                        style     = MaterialTheme.typography.labelLarge,
                        color     = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )

                    ReadOnlyRow("Plan Type",    initial.planType.displayName)
                    ReadOnlyRow("Geography",    initial.geographyScope.label)
                    ReadOnlyRow("Co-Pay Table", initial.coPaymentTable.name)
                    ReadOnlyRow("Rate Table",   initial.rateTableId.ifEmpty { "—" })
                    ReadOnlyRow(
                        "Zones",
                        initial.availableZones.joinToString(", ")
                    )
                    ReadOnlyRow(
                        "Family Types",
                        "${initial.availableFamilyTypes.size} types: ${initial.availableFamilyTypes.joinToString(", ")}"
                    )
                    ReadOnlyRow(
                        "Sum Insureds (${initial.availableSumInsureds.size})",
                        initial.availableSumInsureds.joinToString("  ·  ") { it.toSILabel() }
                    )
                }

                // ── Allowed covers ────────────────────────────────────────────
                HorizontalDivider()

                val selectedCount = allowedCovers.count { it in coverPickerIds }
                val allSelected   = selectedCount == coverPickerItems.size && coverPickerItems.isNotEmpty()

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Allowed covers  ·  $selectedCount of ${coverPickerItems.size} selected",
                            style     = MaterialTheme.typography.labelLarge,
                            color     = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Searches the cover catalogue (${coverPickerItems.size} entries).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(
                        onClick = {
                            allowedCovers = if (allSelected) {
                                // Clear all picker covers (preserve any unrelated ids that might exist)
                                allowedCovers - coverPickerIds
                            } else {
                                allowedCovers + coverPickerIds
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (allSelected) "Clear all" else "Select all",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    LazyColumn(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(coverPickerItems, key = { it.id }) { cover ->
                            val checked = cover.id in allowedCovers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        allowedCovers =
                                            if (checked) allowedCovers - cover.id
                                            else allowedCovers + cover.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked         = checked,
                                    onCheckedChange = { isChecked ->
                                        allowedCovers =
                                            if (isChecked) allowedCovers + cover.id
                                            else allowedCovers - cover.id
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        cover.name,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        cover.id,
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = MaterialTheme.colorScheme.outline,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize   = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Recent quotes (read-only history under this plan) ─────────
                // Only meaningful for existing plans — a brand-new (initial == null)
                // dialog has no plan id to filter by.
                if (initial != null) {
                    var recentQuotes by remember(initial) {
                        mutableStateOf<List<Map<String, JsonElement>>>(emptyList())
                    }
                    var loadingQuotes by remember(initial) { mutableStateOf(false) }
                    LaunchedEffect(initial.id) {
                        loadingQuotes = true
                        recentQuotes = runCatching {
                            client.getQuotesByPlan(initial.id, limit = 10)
                        }.getOrElse { emptyList() }
                        loadingQuotes = false
                    }

                    HorizontalDivider()
                    Text(
                        "Recent quotes priced under this plan",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    when {
                        loadingQuotes -> Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        recentQuotes.isEmpty() -> Text(
                            "No quotes priced under this plan yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        else -> Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier            = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Histogram of totalIncludingGst across valid (non-INVALID) rows.
                            RecentQuotesHistogram(recentQuotes)
                            recentQuotes.forEach { row ->
                                val id        = row["id"]?.jsonPrimitive?.contentOrNull ?: "—"
                                val createdAt = row["createdAt"]?.jsonPrimitive?.contentOrNull
                                    ?.take(16)?.replace('T', ' ') ?: "—"
                                val age       = row["age"]?.jsonPrimitive?.intOrNull ?: 0
                                val total     = row["totalIncludingGst"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        id,
                                        modifier   = Modifier.weight(1.4f),
                                        fontSize   = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        createdAt,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "age $age",
                                        modifier = Modifier.weight(0.6f),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        formatRupees(total),
                                        modifier  = Modifier.weight(0.8f),
                                        fontSize  = 12.sp,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }

                // ── New plan: technical fields ────────────────────────────────
                if (initial == null) {
                    HorizontalDivider()
                    WarningBanner(
                        "Plans are normally loaded via Excel import.\n" +
                        "Use this only for custom plan definitions not covered by the v13.0 Excel file."
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val plan = initial?.copy(
                        name          = name.trim(),
                        description   = description.trim(),
                        minAge        = minAge.toIntOrNull() ?: initial.minAge,
                        maxAge        = maxAge.toIntOrNull() ?: initial.maxAge,
                        maxDiscountCap = (maxDiscount.toIntOrNull() ?: (initial.maxDiscountCap * 100).toInt()) / 100.0,
                        isActive      = isActive,
                        allowedCoverIds = allowedCovers
                    ) ?: Plan(
                        id                   = name.trim().uppercase().replace(" ", "_"),
                        name                 = name.trim(),
                        planType             = PlanType.DOMESTIC,
                        underwritingCategory = UnderwritingCategory.STANDARD,
                        geographyScope       = GeographyScope.DOMESTIC,
                        coPaymentTable       = CoPaymentTable.OMNIBUS,
                        description          = description.trim(),
                        availableSumInsureds = listOf(
                            200_000L, 500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L
                        ),
                        availableZones       = Zone.entries.map { it.label },
                        availableFamilyTypes = FAMILY_TYPES.map { it.code },
                        maxDiscountCap       = (maxDiscount.toIntOrNull() ?: 30) / 100.0,
                        rateTableId          = "",
                        minAge               = minAge.toIntOrNull() ?: 5,
                        maxAge               = maxAge.toIntOrNull() ?: 99,
                        isActive             = isActive,
                        allowedCoverIds      = allowedCovers
                    )
                    onSave(plan)
                }
            ) { Text("Save Changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Recent-quotes histogram ───────────────────────────────────────────────
//
// Tiny 10-bucket horizontal bar chart of totalIncludingGst over the rows
// returned by `getQuotesByPlan`. Rows with totalIncludingGst == 0.0 are
// skipped (INVALID quotes). Renders nothing when fewer than 2 valid points
// remain — bucketing single observations is meaningless.

private const val HISTOGRAM_BUCKET_COUNT = 10
private const val HISTOGRAM_LABEL_ROUNDING = 10_000.0   // ₹10k granularity

private fun roundForLabel(v: Double): Double {
    if (v <= 0.0) return 0.0
    val r = HISTOGRAM_LABEL_ROUNDING
    // round to nearest ₹10k without String.format
    return (kotlin.math.round(v / r)) * r
}

@Composable
private fun RecentQuotesHistogram(rows: List<Map<String, JsonElement>>) {
    val totals = rows
        .mapNotNull { it["totalIncludingGst"]?.jsonPrimitive?.doubleOrNull }
        .filter { it > 0.0 }

    if (totals.size < 2) return   // silently omit — not enough data to bucket

    val minV = totals.min()
    val maxV = totals.max()
    val avg  = totals.sum() / totals.size

    // Degenerate spread (all identical premiums): show summary line only,
    // skip the chart since 10 equal-width buckets would all be width 0.
    val spread = maxV - minV
    val showChart = spread > 0.0

    val summary = buildString {
        append(totals.size).append(" valid quotes · ")
        append(formatRupees(minV)).append(" to ").append(formatRupees(maxV))
        if (totals.size >= 3) {
            append(" · avg ").append(formatRupees(avg))
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier            = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (!showChart) return@Column

        val bucketCount = HISTOGRAM_BUCKET_COUNT
        val width = spread / bucketCount
        val counts = IntArray(bucketCount)
        totals.forEach { v ->
            // last bucket is inclusive of max
            val idx = if (v >= maxV) bucketCount - 1
                      else ((v - minV) / width).toInt().coerceIn(0, bucketCount - 1)
            counts[idx] = counts[idx] + 1
        }
        val peak = counts.max().coerceAtLeast(1)

        for (i in 0 until bucketCount) {
            val lo = minV + width * i
            val hi = if (i == bucketCount - 1) maxV else minV + width * (i + 1)
            val loLabel = roundForLabel(lo)
            val hiLabel = roundForLabel(hi)
            val label = "${formatRupees(loLabel)} – ${formatRupees(hiLabel)}"
            val c = counts[i]
            val frac = (c.toDouble() / peak.toDouble()).toFloat()
            val isPeak = c == peak && c > 0
            val barColor =
                if (isPeak) MaterialTheme.colorScheme.primary
                else        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    label,
                    style    = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color    = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(150.dp)
                )
                // fixed-width track; bar fills a fraction of it
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (c > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(8.dp)
                                .background(barColor)
                        )
                    }
                }
                Text(
                    c.toString(),
                    style    = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
