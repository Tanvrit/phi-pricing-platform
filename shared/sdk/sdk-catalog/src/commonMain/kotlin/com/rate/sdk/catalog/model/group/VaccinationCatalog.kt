package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One vaccination entry (serial number + name) within a [VaccinationCatalog]. */
@Serializable
data class VaccinationItem(
    @SerialName("slNo") val slNo: Int = 0,
    @SerialName("name") val name: String,
)

/**
 * A named vaccination list (e.g. the GROUP "Adult Vaccination List") referenced by
 * vaccination covers. Relocated from `Product Benefit Table EE GHI/Annexure.csv`.
 * [listCode] is the stable lookup key covers point at; [items] is the ordered set of
 * covered vaccinations.
 */
@Serializable
data class VaccinationCatalog(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("listCode") val listCode: String,
    @SerialName("name") val name: String = "",
    @SerialName("items") val items: List<VaccinationItem> = emptyList(),
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
