package com.rate.sdk.catalog.model.demographics

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD gender master: the gender options offered on member/proposer forms and used in
 * gender-differentiated rating where applicable. [code] is the stable machine token persisted on
 * members; [label] is the display string; [sortOrder] controls picker ordering.
 */
@Serializable
data class Gender(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
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
        fun defaults(): List<Gender> = listOf(
            Gender(code = "MALE", label = "Male", sortOrder = 1),
            Gender(code = "FEMALE", label = "Female", sortOrder = 2),
            Gender(code = "OTHER", label = "Other", sortOrder = 3),
        )
    }
}
