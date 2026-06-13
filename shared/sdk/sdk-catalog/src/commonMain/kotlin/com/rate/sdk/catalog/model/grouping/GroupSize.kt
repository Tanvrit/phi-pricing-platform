package com.rate.sdk.catalog.model.grouping

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD master: a group-size band (number of lives) and the size-based loading/discount
 * factor applied to the base premium. Larger groups pool risk better, so the loading factor
 * typically trends below 1.0 as lives increase. Relocated from hardcoded size-slab logic.
 *
 * [loadingFactor] is a plain multiplier on base premium (1.0 = neutral; <1.0 discount, >1.0 load).
 */
@Serializable
data class GroupSize(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("label") val label: String,
    /** Inclusive lower bound of lives in this band. */
    @SerialName("minLives") val minLives: Int = 0,
    /** Inclusive upper bound of lives (use a very large number for the open-ended top band). */
    @SerialName("maxLives") val maxLives: Int = 999_999,
    @SerialName("sortOrder") val sortOrder: Int = 0,
    /** Premium multiplier for groups in this size band (e.g. 0.92 = 8% discount). */
    @SerialName("loadingFactor") val loadingFactor: Double = 1.0,
    @SerialName("active") val active: Boolean = true,
    // ConfigEntity envelope (verbatim):
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
        fun defaults(): List<GroupSize> = listOf(
            GroupSize(label = "7-50 lives", minLives = 7, maxLives = 50, sortOrder = 1, loadingFactor = 1.10),
            GroupSize(label = "51-100 lives", minLives = 51, maxLives = 100, sortOrder = 2, loadingFactor = 1.05),
            GroupSize(label = "101-500 lives", minLives = 101, maxLives = 500, sortOrder = 3, loadingFactor = 1.00),
            GroupSize(label = "501-1000 lives", minLives = 501, maxLives = 1000, sortOrder = 4, loadingFactor = 0.96),
            GroupSize(label = "1001-5000 lives", minLives = 1001, maxLives = 5000, sortOrder = 5, loadingFactor = 0.92),
            GroupSize(label = "5000+ lives", minLives = 5001, maxLives = 999_999, sortOrder = 6, loadingFactor = 0.88),
        )
    }
}
