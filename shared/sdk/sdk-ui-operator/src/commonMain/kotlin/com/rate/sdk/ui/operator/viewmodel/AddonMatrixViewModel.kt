package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.PageRequest
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which boolean flag of a [ProductAddonConfig] cell a toggle mutates. [INCLUDED] gates whether the
 * add-on is offered on the product at all; [DEFAULT_SELECTED] pre-ticks it in the journey;
 * [MANDATORY] forces it on.
 */
enum class CellFlag { INCLUDED, DEFAULT_SELECTED, MANDATORY }

/**
 * State + admin-CRUD logic behind [com.rate.sdk.ui.operator.surface.AddonMatrixSurface] — the
 * product x add-on configuration matrix (Dorian slides 06/07/13).
 *
 * It loads the full add-on master list (ROWS), the full product master list (COLUMNS) and every
 * [ProductAddonConfig] join row, then indexes the configs by the composite key `addonRef|productRef`
 * so a cell lookup is O(1). Toggling a cell that has no backing config yet CREATEs one (with the
 * just-toggled flag set); toggling an existing one UPDATEs it with the loaded `v` for optimistic
 * concurrency. "Apply to all products" clones one add-on row's flags across every product column.
 *
 * Pure-KMP: plain Compose snapshot state, a [scope] passed in from the surface's
 * `rememberCoroutineScope()` (no Android ViewModel). Every mutation stamps [actor] for the audit
 * chain and refreshes only the affected config (the master lists never change here) so the matrix
 * stays responsive without a full reload.
 */
class AddonMatrixViewModel(
    private val api: ConfigAdminApi,
    private val scope: CoroutineScope,
    private val actor: String?,
) {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** ROWS — add-on master, ordered by displayOrder then name. */
    var addons by mutableStateOf<List<AddOn>>(emptyList())
        private set

    /** COLUMNS — product master, ordered by displayOrder then name. */
    var products by mutableStateOf<List<Product>>(emptyList())
        private set

    /** Join rows indexed by [cellKey] (addonRef|productRef). */
    var configs by mutableStateOf<Map<String, ProductAddonConfig>>(emptyMap())
        private set

    /** Set of [cellKey]s currently mid-flight, so the cell can show a busy/disabled state. */
    var busyCells by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Composite cell key — one [ProductAddonConfig] per (add-on, product) pair. */
    fun cellKey(addonId: String, productId: String): String = "$addonId|$productId"

    fun configFor(addonId: String, productId: String): ProductAddonConfig? =
        configs[cellKey(addonId, productId)]

    /** Load the two master lists + all join rows. PageRequest(size = 500) pulls the full matrix. */
    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                val productPage = api.list("products", Product.serializer(), PageRequest(size = 500))
                val addonPage = api.list("addons", AddOn.serializer(), PageRequest(size = 500))
                val configPage = api.list(
                    "product-addon-configs",
                    ProductAddonConfig.serializer(),
                    PageRequest(size = 500),
                )
                Triple(productPage.items, addonPage.items, configPage.items)
            }.onSuccess { (prods, adds, cfgs) ->
                products = prods.sortedWith(compareBy({ it.displayOrder }, { it.name }))
                addons = adds.sortedWith(compareBy({ it.displayOrder }, { it.name }))
                configs = cfgs.associateBy { cellKey(it.addonRef, it.productRef) }
                loading = false
            }.onFailure { t ->
                error = t.message ?: t::class.simpleName ?: "Failed to load the add-on matrix"
                loading = false
            }
        }
    }

    /**
     * Toggle one boolean [flag] of the cell at (add-on, product). Creates a backing
     * [ProductAddonConfig] if none exists yet, otherwise updates the existing one with its loaded `v`.
     *
     * When a cell with no config is first touched the new row is created with [flag] set to [value]
     * and `included` forced true (so the add-on actually appears on the product — a defaultSelected /
     * mandatory cell with included=false would be meaningless).
     */
    fun toggleCell(addon: AddOn, product: Product, flag: CellFlag, value: Boolean) {
        val key = cellKey(addon.id, product.id)
        if (key in busyCells) return
        busyCells = busyCells + key
        scope.launch {
            runCatching {
                val existing = configs[key]
                if (existing == null) {
                    val fresh = ProductAddonConfig(
                        productRef = product.id,
                        addonRef = addon.id,
                        included = if (flag == CellFlag.INCLUDED) value else true,
                        defaultSelected = flag == CellFlag.DEFAULT_SELECTED && value,
                        mandatory = flag == CellFlag.MANDATORY && value,
                        displayOrder = addon.displayOrder,
                        productLine = product.productLine.name,
                    )
                    api.create("product-addon-configs", ProductAddonConfig.serializer(), fresh, actor)
                } else {
                    val updated = existing.applyFlag(flag, value)
                    api.update(
                        "product-addon-configs",
                        ProductAddonConfig.serializer(),
                        updated,
                        existing.v,
                        actor,
                    )
                }
            }.onSuccess { saved ->
                configs = configs + (key to saved)
            }.onFailure { t ->
                error = t.message ?: "Could not update ${addon.name} on ${product.name}"
            }
            busyCells = busyCells - key
        }
    }

    /**
     * Clone one add-on row's settings across EVERY product column. The template is taken from the
     * row's first existing config (or sensible defaults if the row is entirely empty): every product
     * cell is created/updated to match that template's included / defaultSelected / mandatory flags.
     */
    fun applyRowToAllProducts(addon: AddOn) {
        if (products.isEmpty()) return
        val template = products.firstNotNullOfOrNull { configs[cellKey(addon.id, it.id)] }
        val included = template?.included ?: true
        val defaultSelected = template?.defaultSelected ?: false
        val mandatory = template?.mandatory ?: false
        scope.launch {
            for (product in products) {
                val key = cellKey(addon.id, product.id)
                if (key in busyCells) continue
                busyCells = busyCells + key
                runCatching {
                    val existing = configs[key]
                    if (existing == null) {
                        val fresh = ProductAddonConfig(
                            productRef = product.id,
                            addonRef = addon.id,
                            included = included,
                            defaultSelected = defaultSelected,
                            mandatory = mandatory,
                            displayOrder = addon.displayOrder,
                            productLine = product.productLine.name,
                        )
                        api.create("product-addon-configs", ProductAddonConfig.serializer(), fresh, actor)
                    } else {
                        val updated = existing.copy(
                            included = included,
                            defaultSelected = defaultSelected,
                            mandatory = mandatory,
                        )
                        api.update(
                            "product-addon-configs",
                            ProductAddonConfig.serializer(),
                            updated,
                            existing.v,
                            actor,
                        )
                    }
                }.onSuccess { saved ->
                    configs = configs + (key to saved)
                }.onFailure { t ->
                    error = t.message ?: "Could not apply ${addon.name} to ${product.name}"
                }
                busyCells = busyCells - key
            }
        }
    }

    private fun ProductAddonConfig.applyFlag(flag: CellFlag, value: Boolean): ProductAddonConfig =
        when (flag) {
            CellFlag.INCLUDED -> copy(included = value)
            CellFlag.DEFAULT_SELECTED -> copy(defaultSelected = value)
            CellFlag.MANDATORY -> copy(mandatory = value)
        }
}
