package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.CoverParam
import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One cover inside an add-on bundle, with the param the engine needs pre-filled. */
@Serializable
data class AddOnItem(
    @SerialName("coverCode") val coverCode: String,
    @SerialName("defaultParam") val defaultParam: CoverParam = CoverParam(),
    /** Customer-facing label/description override (else falls back to the Cover's). */
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
)

/**
 * A curated add-on bundle exposed in the buy-online journey — a subset of the 50+ engine
 * covers grouped under a tier ("PRU Premier/Signature/Global"). Relocated from
 * `buyonline/BuyOnlinePlanMapping.kt` (BuyOnlineTier + defaultAddOnCovers + BUYONLINE_ADDONS)
 * into admin-CRUD config so bundles are editable, not hardcoded.
 *
 * [planRef] maps the tier to the actuarial [Plan] id (was BuyOnlineTier.toPlanId()).
 * [preSelected] = true means the bundle ships pre-checked (Signature/Global); Premier=false.
 */
@Serializable
data class AddOn(
    @SerialName("_id") override val id: String = newId(),
    /** Stable bundle code (e.g. "premier", "signature", "global"). */
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** Actuarial Plan id this bundle rates against (was BuyOnlineTier.toPlanId()). */
    @SerialName("planRef") val planRef: String = "",
    @SerialName("items") val items: List<AddOnItem> = emptyList(),
    @SerialName("preSelected") val preSelected: Boolean = false,
    @SerialName("displayOrder") val displayOrder: Int = 0,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity
