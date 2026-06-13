package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a checkup is performed. */
@Serializable
enum class CheckupPackageType { HOME_VISIT, CENTER_VISIT }

/**
 * A health check-up package. Relocated from
 * `Product Benefit Table EE GHI/Health Check Up Packages.csv`
 * (Package Name, Test Details, Package Type).
 */
@Serializable
data class HealthCheckupPackage(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    /** Tests included (split from the comma-separated "Test Details" column). */
    @SerialName("tests") val tests: List<String> = emptyList(),
    @SerialName("packageType") val packageType: CheckupPackageType = CheckupPackageType.HOME_VISIT,
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
