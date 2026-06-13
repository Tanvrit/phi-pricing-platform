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
 * Admin-CRUD tenure configuration: an available policy term (in years) and the multi-year
 * single-premium discount applied for choosing it.
 *
 * NOTE: the rating enum [com.rate.core.rating.ports.model.Tenure] is the *engine* term;
 * this entity is the *catalog* config that makes the discount step admin-tunable (relocated
 * from the buy-online hardcoded "1yr=0%, 2yr=7.5%, 3yr=10%, 4yr=12.5%, 5yr=15%").
 */
@Serializable
data class Tenure(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("years") val years: Int,
    @SerialName("label") val label: String = "$years Year",
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** Multi-year single-premium discount fraction for this term (e.g. 0.075 for 2yr). */
    @SerialName("multiTenureDiscount") val multiTenureDiscount: Double = 0.0,
    /**
     * Net price multiplier applied to a one-year premium for this term (e.g. 0.925 = 7.5% off).
     * Defaults to (1 - [multiTenureDiscount]) when not explicitly configured.
     */
    @SerialName("discountFactor") val discountFactor: Double = 1.0 - multiTenureDiscount,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("displayOrder") val displayOrder: Int = years,
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
