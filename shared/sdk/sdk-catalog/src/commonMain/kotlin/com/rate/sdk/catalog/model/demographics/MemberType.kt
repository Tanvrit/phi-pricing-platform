package com.rate.sdk.catalog.model.demographics

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Broad family-coverage bucket a member type falls into, used for floater/sum-insured rules. */
@Serializable
enum class MemberCategory { SELF, SPOUSE, CHILD, PARENT, PARENT_IN_LAW, OTHER }

/**
 * Admin-CRUD member-type master: the categories of people who can be covered under a group/retail
 * health policy (self, spouse, child, parent, …). [isAdult] distinguishes adults from dependent
 * children (which gates child-age limits and adult-count rules); [category] groups types for
 * floater and parent/parent-in-law eligibility logic.
 */
@Serializable
data class MemberType(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("isAdult") val isAdult: Boolean = true,
    @SerialName("category") val category: MemberCategory = MemberCategory.OTHER,
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
        fun defaults(): List<MemberType> = listOf(
            MemberType(code = "SELF", label = "Self / Employee", isAdult = true, category = MemberCategory.SELF, sortOrder = 1),
            MemberType(code = "SPOUSE", label = "Spouse", isAdult = true, category = MemberCategory.SPOUSE, sortOrder = 2),
            MemberType(code = "CHILD", label = "Child", isAdult = false, category = MemberCategory.CHILD, sortOrder = 3),
            MemberType(code = "PARENT", label = "Parent", isAdult = true, category = MemberCategory.PARENT, sortOrder = 4),
            MemberType(code = "PARENT_IN_LAW", label = "Parent-in-law", isAdult = true, category = MemberCategory.PARENT_IN_LAW, sortOrder = 5),
        )
    }
}
