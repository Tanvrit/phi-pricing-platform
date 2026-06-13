package com.rate.sdk.catalog.model.rfq

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The product line a group-size sizing factor applies to. Kept local (a String-stable enum) so this
 * pure-KMP RFQ model never depends on another domain's type — RETAIL / GROUP mirrored by name.
 */
@Serializable
enum class GroupSizeProductLine {
    @SerialName("RETAIL") RETAIL,
    @SerialName("GROUP") GROUP,
}

/**
 * Admin-CRUD group-size-matrix master (Dorian demography sizing, slides 16-20): a sizing [factor]
 * applied to the price for a given group-size band ([groupSizeRef]) on a [productLine]. Larger
 * groups carry more credible, more diversified risk, so the factor typically falls as headcount
 * rises; [basis] documents what the factor multiplies (e.g. "base premium", "expense loading").
 */
@Serializable
data class GroupSizeMatrix(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("groupSizeRef") val groupSizeRef: String = "",
    @SerialName("factor") val factor: Double = 1.0,
    @SerialName("basis") val basis: String = "",
    @SerialName("productLine") val productLine: GroupSizeProductLine = GroupSizeProductLine.GROUP,
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
        fun defaults(): List<GroupSizeMatrix> = listOf(
            GroupSizeMatrix(name = "Small group loading", factor = 1.15, basis = "Base premium", productLine = GroupSizeProductLine.GROUP),
            GroupSizeMatrix(name = "Mid group neutral", factor = 1.00, basis = "Base premium", productLine = GroupSizeProductLine.GROUP),
            GroupSizeMatrix(name = "Large group discount", factor = 0.92, basis = "Base premium", productLine = GroupSizeProductLine.GROUP),
            GroupSizeMatrix(name = "Enterprise group discount", factor = 0.85, basis = "Base premium", productLine = GroupSizeProductLine.GROUP),
        )
    }
}
