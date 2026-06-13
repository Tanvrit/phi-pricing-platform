package com.rate.sdk.catalog.model.demographics

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD age-band master: an inclusive age bracket that drives eligibility checks and the
 * rate-table lookups (premiums in group/retail health are quoted per age band, not per exact age).
 * The buy-online and quoting engines bucket each member's age into exactly one band by
 * [minAge]..[maxAge]; [sortOrder] keeps the bands in natural ascending order in pickers/tables.
 */
@Serializable
data class AgeBand(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("label") val label: String,
    @SerialName("minAge") val minAge: Int = 0,
    @SerialName("maxAge") val maxAge: Int = 999,
    @SerialName("sortOrder") val sortOrder: Int = 0,
    @SerialName("active") val active: Boolean = true,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
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
        fun defaults(): List<AgeBand> = listOf(
            AgeBand(label = "0-17", minAge = 0, maxAge = 17, sortOrder = 1),
            AgeBand(label = "18-25", minAge = 18, maxAge = 25, sortOrder = 2),
            AgeBand(label = "26-35", minAge = 26, maxAge = 35, sortOrder = 3),
            AgeBand(label = "36-45", minAge = 36, maxAge = 45, sortOrder = 4),
            AgeBand(label = "46-55", minAge = 46, maxAge = 55, sortOrder = 5),
            AgeBand(label = "56-65", minAge = 56, maxAge = 65, sortOrder = 6),
            AgeBand(label = "66-75", minAge = 66, maxAge = 75, sortOrder = 7),
            AgeBand(label = "76-99", minAge = 76, maxAge = 99, sortOrder = 8),
        )
    }
}
