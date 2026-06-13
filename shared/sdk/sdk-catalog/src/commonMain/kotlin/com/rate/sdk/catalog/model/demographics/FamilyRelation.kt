package com.rate.sdk.catalog.model.demographics

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD family-relation master: the specific relationship a covered member has to the
 * proposer/employee (Self, Spouse, Son, Daughter, Father, …). Each relation rolls up to a broad
 * [memberTypeRef] (an id of a MemberType) for rating/eligibility, and [maxCount] caps how many of
 * that relation may be added on a single policy (e.g. 1 Self, 1 Spouse, up to 4 Sons).
 *
 * [memberTypeRef] holds the MemberType id as a plain String (entity-picker reference) — we never
 * import another domain's type into this one.
 */
@Serializable
data class FamilyRelation(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("memberTypeRef") val memberTypeRef: String = "",
    @SerialName("maxCount") val maxCount: Int = 1,
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
        fun defaults(): List<FamilyRelation> = listOf(
            FamilyRelation(code = "SELF", label = "Self", maxCount = 1, sortOrder = 1),
            FamilyRelation(code = "SPOUSE", label = "Spouse", maxCount = 1, sortOrder = 2),
            FamilyRelation(code = "SON", label = "Son", maxCount = 4, sortOrder = 3),
            FamilyRelation(code = "DAUGHTER", label = "Daughter", maxCount = 4, sortOrder = 4),
            FamilyRelation(code = "FATHER", label = "Father", maxCount = 1, sortOrder = 5),
            FamilyRelation(code = "MOTHER", label = "Mother", maxCount = 1, sortOrder = 6),
            FamilyRelation(code = "FATHER_IN_LAW", label = "Father-in-law", maxCount = 1, sortOrder = 7),
            FamilyRelation(code = "MOTHER_IN_LAW", label = "Mother-in-law", maxCount = 1, sortOrder = 8),
        )
    }
}
