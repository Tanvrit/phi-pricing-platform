package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which of the four consumables lists this entity holds. */
@Serializable
enum class ConsumablesListType {
    LIST_I,    // non-payable items
    LIST_II,   // items included in room charges
    LIST_III,  // items included in procedure charges
    LIST_IV,   // items included in cost of treatment
}

/**
 * One of the four consumables lists (I–IV). Relocated from
 * `Product Benefit Table EE GHI/Consumables List.csv` (four parallel columns split into
 * four entities). Each consumables-cover option (List I, List I-IV) references these.
 */
@Serializable
data class ConsumablesList(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("listType") val listType: ConsumablesListType,
    @SerialName("title") val title: String = "",
    @SerialName("items") val items: List<String> = emptyList(),
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
