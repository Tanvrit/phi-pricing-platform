package com.rate.sdk.catalog.model.grouping

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD master: the KIND of group a policy is issued to. Drives eligibility (minimum lives
 * per IRDAI group-insurance norms), whether a formal employer-employee relationship is required,
 * and downstream rating treatment. Relocated from the buy-online/group hardcoded group taxonomy.
 *
 * Examples (Indian group health): Employer-Employee (formal, 7+ lives), Affinity, Bank Customer
 * groups, Professional Associations, Microfinance/SHG groups.
 */
@Serializable
data class GroupType(
    @SerialName("_id") override val id: String = newId(),
    /** Stable machine code (e.g. "EE", "AFFINITY", "BANK_CUSTOMER"). */
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("description") val description: String = "",
    /** IRDAI-style minimum number of lives to constitute this group. */
    @SerialName("minLives") val minLives: Int = 7,
    /** True when a formal (employer-employee / homogeneous) relationship is mandatory. */
    @SerialName("formal") val formal: Boolean = false,
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
        fun defaults(): List<GroupType> = listOf(
            GroupType(
                code = "EE",
                label = "Employer-Employee",
                description = "Formal employer-employee group; benefits extend to employees and dependants.",
                minLives = 7,
                formal = true,
                sortOrder = 1,
            ),
            GroupType(
                code = "AFFINITY",
                label = "Affinity Group",
                description = "Members sharing a common affinity other than employment (clubs, alumni, societies).",
                minLives = 50,
                formal = false,
                sortOrder = 2,
            ),
            GroupType(
                code = "BANK_CUSTOMER",
                label = "Bank Customer Group",
                description = "Account/loan holders of a bank offered group cover by the master policyholder.",
                minLives = 100,
                formal = false,
                sortOrder = 3,
            ),
            GroupType(
                code = "PROF_ASSOC",
                label = "Professional Association",
                description = "Registered professional/trade body extending cover to its members.",
                minLives = 25,
                formal = false,
                sortOrder = 4,
            ),
            GroupType(
                code = "MFI",
                label = "Microfinance Group",
                description = "Microfinance / self-help borrower group; typically high-volume, low-ticket cover.",
                minLives = 500,
                formal = false,
                sortOrder = 5,
            ),
        )
    }
}
