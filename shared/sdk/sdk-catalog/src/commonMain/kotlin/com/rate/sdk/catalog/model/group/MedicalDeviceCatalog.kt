package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One category of medical devices (a numbered heading from the GROUP device annexure) plus
 * the device names listed under it (e.g. "Diabetes & Metabolic Monitoring Devices" →
 * Glucometer, CGM System, Insulin Pump…).
 */
@Serializable
data class DeviceCategory(
    @SerialName("name") val name: String,
    @SerialName("devices") val devices: List<String> = emptyList(),
)

/**
 * A named medical-device list referenced by device/durable-equipment covers. Relocated from
 * `Product Benefit Table EE GHI/Annexure.csv`, where devices are grouped under numbered
 * category headings. [listCode] is the stable lookup key covers point at; [categories] holds
 * the ordered category → devices grouping.
 */
@Serializable
data class MedicalDeviceCatalog(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("listCode") val listCode: String,
    @SerialName("name") val name: String = "",
    @SerialName("categories") val categories: List<DeviceCategory> = emptyList(),
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
