package com.rate.sdk.catalog.model.grouping

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD master: a family-definition combination (adults + children) used to compute the
 * per-member premium basis and the family floater entitlement. Relocated from the buy-online
 * hardcoded family-type set (1A, 2A, 2A+2C, floater, …).
 *
 * [isFloater] marks the band as a single shared (floater) sum-insured across the family; otherwise
 * each life carries an individual sum insured.
 */
@Serializable
data class FamilyType(
    @SerialName("_id") override val id: String = newId(),
    /** Stable compact code (e.g. "1A", "2A", "2A+2C"). */
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    /** Number of adult lives covered. */
    @SerialName("adults") val adults: Int = 1,
    /** Number of child lives covered. */
    @SerialName("children") val children: Int = 0,
    @SerialName("definition") val definition: String = "",
    /** True when the band uses a shared family floater sum insured. */
    @SerialName("isFloater") val isFloater: Boolean = false,
    @SerialName("sortOrder") val sortOrder: Int = 0,
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
        fun defaults(): List<FamilyType> = listOf(
            FamilyType(code = "1A", label = "Self only", adults = 1, children = 0, definition = "Employee/member only.", sortOrder = 1),
            FamilyType(code = "1A+1C", label = "Self + 1 Child", adults = 1, children = 1, definition = "Member and one dependent child.", sortOrder = 2),
            FamilyType(code = "1A+2C", label = "Self + 2 Children", adults = 1, children = 2, definition = "Member and two dependent children.", sortOrder = 3),
            FamilyType(code = "2A", label = "Self + Spouse", adults = 2, children = 0, definition = "Member and spouse.", sortOrder = 4),
            FamilyType(code = "2A+1C", label = "Self + Spouse + 1 Child", adults = 2, children = 1, definition = "Member, spouse and one child.", sortOrder = 5),
            FamilyType(code = "2A+2C", label = "Self + Spouse + 2 Children", adults = 2, children = 2, definition = "Member, spouse and two children.", sortOrder = 6),
            FamilyType(code = "2A+3C", label = "Self + Spouse + 3 Children", adults = 2, children = 3, definition = "Member, spouse and three children.", sortOrder = 7),
            FamilyType(
                code = "FLOATER_2A2C",
                label = "Family Floater (2A+2C)",
                adults = 2,
                children = 2,
                definition = "Shared floater sum insured across self, spouse and up to two children.",
                isFloater = true,
                sortOrder = 8,
            ),
        )
    }
}
