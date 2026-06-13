package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A catalogued insurance product (RETAIL or GROUP) — the top of the catalog tree.
 *
 * A [Product] groups [Section]s (each holding [Cover]s). For RETAIL this is a PHI Basic /
 * Flagship / Global product; for GROUP it is the Employer-Employee / GHI offering whose
 * extra constructs live under [com.rate.sdk.catalog.model.group].
 *
 * The actuarial [com.rate.core.rating.ports.model.Plan] (the rating anchor) lives in core
 * and references a Product by [code]; a Product is the *catalog* face, a Plan the *rating*
 * face. They are kept separate so admins edit benefit copy without touching rate tables.
 */
@Serializable
data class Product(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** Stable business code (e.g. "PHI_BASIC", "GROUP_EE"). Unique within productLine. */
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    /** UIN once IRDAI-approved (admin-managed; null while filing). */
    @SerialName("uin") val uin: String? = null,
    /** Ordered ids of the [Section]s that compose this product. */
    @SerialName("sectionRefs") val sectionRefs: List<String> = emptyList(),
    /** Ids of [Plan]s (core) that rate against this product. */
    @SerialName("planRefs") val planRefs: List<String> = emptyList(),
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
