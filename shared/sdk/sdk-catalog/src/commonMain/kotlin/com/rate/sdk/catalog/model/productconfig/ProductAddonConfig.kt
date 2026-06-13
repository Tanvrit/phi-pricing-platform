package com.rate.sdk.catalog.model.productconfig

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD product x addon JOIN row backing the addon matrix: it declares whether a given addon
 * is offered on a given product, how it is pre-selected, whether it is mandatory, and any
 * product-specific price/sum-insured overrides.
 *
 * [productRef] points at a `products` master row and [addonRef] at an `addons` master row — each is
 * stored as the referenced entity's id string, never as a typed cross-domain reference. [included]
 * gates whether the addon appears at all on the product; [defaultSelected] pre-ticks it in the
 * journey; [mandatory] forces it on. [priceOverride] replaces the addon's own price for this product
 * (Money.ZERO = use the addon default); [minSumInsured]/[maxSumInsured] clamp the addon's SI on this
 * product. [displayOrder] orders addons in the matrix and [productLine] ("RETAIL"/"GROUP") scopes
 * the row to a product line.
 */
@Serializable
data class ProductAddonConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("productRef") val productRef: String = "",
    @SerialName("addonRef") val addonRef: String = "",
    @SerialName("included") val included: Boolean = true,
    @SerialName("defaultSelected") val defaultSelected: Boolean = false,
    @SerialName("mandatory") val mandatory: Boolean = false,
    @SerialName("priceOverride") val priceOverride: Money = Money.ZERO,
    @SerialName("minSumInsured") val minSumInsured: Money = Money.ZERO,
    @SerialName("maxSumInsured") val maxSumInsured: Money = Money.ZERO,
    @SerialName("displayOrder") val displayOrder: Int = 0,
    @SerialName("productLine") val productLine: String = "RETAIL",
    @SerialName("active") val active: Boolean = true,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity {
    companion object {
        fun defaults(): List<ProductAddonConfig> = listOf(
            ProductAddonConfig(
                productRef = "",
                addonRef = "",
                included = true,
                defaultSelected = false,
                mandatory = false,
                displayOrder = 1,
                productLine = "RETAIL",
            ),
            ProductAddonConfig(
                productRef = "",
                addonRef = "",
                included = true,
                defaultSelected = true,
                mandatory = false,
                displayOrder = 2,
                productLine = "GROUP",
            ),
        )
    }
}
