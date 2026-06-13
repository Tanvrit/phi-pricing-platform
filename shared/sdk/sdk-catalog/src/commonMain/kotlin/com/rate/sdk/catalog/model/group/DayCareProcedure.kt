package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One day-care procedure entry (number + name within its category group). */
@Serializable
data class DayCareItem(
    @SerialName("number") val number: Int,
    @SerialName("name") val name: String,
)

/**
 * The list of day-care treatments/surgeries/procedures (Annexure II, 1–546). Relocated from
 * `Product Benefit Table EE GHI/Day Care List.csv`. Items are grouped by [category] heading
 * (e.g. "Microsurgical Operations on the middle ear").
 */
@Serializable
data class DayCareProcedure(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("listCode") val listCode: String = "DAY_CARE",
    /** Category heading these procedures fall under. */
    @SerialName("category") val category: String,
    @SerialName("items") val items: List<DayCareItem> = emptyList(),
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
