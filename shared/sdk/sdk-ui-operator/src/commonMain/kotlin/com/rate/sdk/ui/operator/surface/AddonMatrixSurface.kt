package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.ui.kit.components.AegisBadge
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisChipTone
import com.rate.sdk.ui.kit.components.AegisToggle
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.viewmodel.AddonMatrixViewModel
import com.rate.sdk.ui.operator.viewmodel.CellFlag

private val ROW_LABEL_WIDTH = 240.dp
private val CELL_WIDTH = 168.dp
private val ROW_HEIGHT = 92.dp
private val HEADER_HEIGHT = 64.dp

/**
 * ADD-ON MATRIX EDITOR (Dorian slides 06/07/13) — the product x add-on configuration matrix.
 *
 * ROWS are add-ons (a sticky left label column); COLUMNS are products (a [horizontalScroll]ing row
 * of fixed-width cells). Each cell is backed by a [ProductAddonConfig] join row keyed by
 * (addonRef, productRef). A cell exposes an `included` [AegisToggle] plus `default` / `mandatory`
 * chips; toggling a cell with no backing config creates one, toggling an existing one updates it
 * with optimistic versioning. Each row has an "Apply to all" affordance that clones the row's
 * settings across every product column.
 *
 * Compose-safe: the surface ROOT is a verticalScroll [Column]; rows render as a plain Column (NOT a
 * LazyColumn) so the matrix participates in the page scroll, and the wide product axis uses a single
 * bounded [horizontalScroll]ing [Row] (horizontal-inside-vertical is allowed).
 *
 * @param api admin-CRUD transport (passed by the integrator from `ctx.configAdmin`).
 * @param actor operator identity stamped into mutations for audit attribution (`ctx.actor`).
 */
@Composable
fun AddonMatrixSurface(api: ConfigAdminApi, actor: String?) {
    val scope = rememberCoroutineScope()
    val vm = remember(api) { AddonMatrixViewModel(api, scope, actor) }
    LaunchedEffect(Unit) { vm.load() }

    val hScroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5),
    ) {
        Text("Add-on Matrix", style = AegisTypography.h1.copy(color = AegisColors.textPrimary))

        val nAddons = vm.addons.size
        val mProducts = vm.products.size
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "How the matrix works",
            body = "Rows are add-ons, columns are products. Each cell is one product x add-on rule: " +
                "toggle Included to offer the add-on on that product, then mark it Default (pre-ticked " +
                "in the journey) or Mandatory (always on). $nAddons add-ons x $mProducts products = " +
                "${nAddons * mProducts} cells.",
        )

        when {
            vm.loading -> AegisCallout(
                kind = CalloutKind.INFO,
                title = "Loading the matrix...",
                body = "Pulling products, add-ons and their join rules from the server.",
            )

            vm.error != null -> AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Could not load the matrix",
                body = vm.error,
            )

            vm.addons.isEmpty() || vm.products.isEmpty() -> AegisCallout(
                kind = CalloutKind.WARN,
                title = "Nothing to map yet",
                body = "The matrix needs at least one add-on and one product. Add some in the Add-ons " +
                    "and Products config editors, then return here.",
            )

            else -> {
                vm.error?.let {
                    AegisCallout(kind = CalloutKind.DANGER, title = "Last action failed", body = it)
                }

                AegisCard(
                    title = "Product x add-on rules",
                    subtitle = "Showing ${vm.addons.size} add-ons x ${vm.products.size} products " +
                        "(${vm.addons.size * vm.products.size} cells)",
                    padding = PaddingValues(0.dp),
                ) {
                    Column {
                        MatrixHeaderRow(products = vm.products, hScroll = hScroll)
                        vm.addons.forEachIndexed { index, addon ->
                            MatrixDataRow(
                                addon = addon,
                                products = vm.products,
                                vm = vm,
                                hScroll = hScroll,
                                zebra = index % 2 == 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixHeaderRow(
    products: List<Product>,
    hScroll: androidx.compose.foundation.ScrollState,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(AegisColors.surfaceMuted),
    ) {
        // Sticky corner cell.
        Box(
            Modifier
                .width(ROW_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = AegisSpacing.s4),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Add-on \\ Product",
                style = AegisTypography.caption.copy(color = AegisColors.textSecondary),
            )
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            products.forEach { product ->
                Column(
                    Modifier
                        .width(CELL_WIDTH)
                        .fillMaxHeight()
                        .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        product.name,
                        style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            product.code,
                            style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                            maxLines = 1,
                        )
                        AegisBadge(
                            label = product.productLine.name,
                            tone = AegisBadgeTone.Brand,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixDataRow(
    addon: AddOn,
    products: List<Product>,
    vm: AddonMatrixViewModel,
    hScroll: androidx.compose.foundation.ScrollState,
    zebra: Boolean,
) {
    val rowBg = if (zebra) AegisColors.surfaceMuted else AegisColors.surface
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(rowBg)
            .border(width = 0.5.dp, color = AegisColors.border),
    ) {
        // Sticky left label cell.
        Column(
            Modifier
                .width(ROW_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                addon.name,
                style = AegisTypography.body.copy(
                    color = AegisColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            Text(
                addon.code,
                style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                maxLines = 1,
            )
            AegisButton(
                label = "Apply to all",
                onClick = { vm.applyRowToAllProducts(addon) },
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
            )
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            products.forEach { product ->
                MatrixCell(addon = addon, product = product, vm = vm)
            }
        }
    }
}

@Composable
private fun MatrixCell(
    addon: AddOn,
    product: Product,
    vm: AddonMatrixViewModel,
) {
    val key = vm.cellKey(addon.id, product.id)
    val config: ProductAddonConfig? = vm.configFor(addon.id, product.id)
    val included = config?.included ?: false
    val busy = key in vm.busyCells

    Column(
        Modifier
            .width(CELL_WIDTH)
            .fillMaxHeight()
            .border(width = 0.5.dp, color = AegisColors.border)
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (included) "Included" else "Off",
                style = AegisTypography.small.copy(
                    color = if (included) AegisColors.success700 else AegisColors.textTertiary,
                    fontWeight = FontWeight.Medium,
                ),
            )
            AegisToggle(
                checked = included,
                enabled = !busy,
                onCheckedChange = { vm.toggleCell(addon, product, CellFlag.INCLUDED, it) },
            )
        }

        // Default / Mandatory chips — only meaningful once the add-on is included.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val defaultSelected = config?.defaultSelected ?: false
            val mandatory = config?.mandatory ?: false
            AegisChip(
                label = "Default",
                selected = defaultSelected,
                tone = if (defaultSelected) AegisChipTone.Success else AegisChipTone.Neutral,
                onClick = if (included && !busy) {
                    { vm.toggleCell(addon, product, CellFlag.DEFAULT_SELECTED, !defaultSelected) }
                } else null,
            )
            AegisChip(
                label = "Mandatory",
                selected = mandatory,
                tone = if (mandatory) AegisChipTone.Warn else AegisChipTone.Neutral,
                onClick = if (included && !busy) {
                    { vm.toggleCell(addon, product, CellFlag.MANDATORY, !mandatory) }
                } else null,
            )
        }
    }
}
