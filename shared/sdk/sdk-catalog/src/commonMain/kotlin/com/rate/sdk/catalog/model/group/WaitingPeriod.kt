package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A waiting-period clause. Relocated from `Product Benefit Table EE GHI/Waiting Periods.csv`
 * (Sl no, Clause, Waiting Period, Options to modify).
 *
 * e.g. clause="Pre-Existing Disease", period="3 Years since date of inception",
 * optionsToModify="0-36 Months".
 */
@Serializable
data class WaitingPeriod(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("groupProductRef") val groupProductRef: String = "",
    @SerialName("slNo") val slNo: Int = 0,
    @SerialName("clause") val clause: String,
    @SerialName("period") val period: String,
    @SerialName("optionsToModify") val optionsToModify: String = "",
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
