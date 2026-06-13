package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An approval authority tier (Dorian slide 10 — actuarial masters). Each level pins the coarse RBAC
 * role it maps to, the largest premium/payout amount that role may sign off, and the maximum
 * percentage discount it may approve. Underwriting and discount-cascade approvals will later be
 * gated against the [AuthorityLevel] of the acting user's role.
 *
 * Pure config projection: the role vocabulary mirrors the auth token's coarse roles
 * ("CUSTOMER","BUSINESS","ADMIN","OWNER") but is stored as a plain string so this master never
 * imports the auth domain.
 */
@Serializable
data class AuthorityLevel(
    @SerialName("_id") override val id: String = newId(),
    /** Operator-facing name of the tier (e.g. "Branch Underwriter"). */
    @SerialName("name") val name: String,
    /** Coarse RBAC role this authority maps to — one of CUSTOMER/BUSINESS/ADMIN/OWNER. */
    @SerialName("roleName") val roleName: String = "BUSINESS",
    /** Largest premium/claim amount this tier may approve. */
    @SerialName("maxApprovalAmount") val maxApprovalAmount: Money = Money.ZERO,
    /** Maximum discount percentage (0..100) this tier may sign off. */
    @SerialName("discountApprovalCapPct") val discountApprovalCapPct: Double = 0.0,
    /** Scope/remit description (e.g. "Branch", "Regional", "National", "Board"). */
    @SerialName("scope") val scope: String = "",
    /** Display/escalation order — lower sorts first. */
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
        fun defaults(): List<AuthorityLevel> = listOf(
            AuthorityLevel(
                name = "Branch Underwriter", roleName = "BUSINESS", scope = "Branch",
                maxApprovalAmount = Money.fromRupees(500_000L), discountApprovalCapPct = 5.0, sortOrder = 10,
            ),
            AuthorityLevel(
                name = "Regional Underwriter", roleName = "BUSINESS", scope = "Regional",
                maxApprovalAmount = Money.fromRupees(2_500_000L), discountApprovalCapPct = 10.0, sortOrder = 20,
            ),
            AuthorityLevel(
                name = "Chief Underwriter", roleName = "ADMIN", scope = "National",
                maxApprovalAmount = Money.fromRupees(25_000_000L), discountApprovalCapPct = 20.0, sortOrder = 30,
            ),
            AuthorityLevel(
                name = "Appointed Actuary", roleName = "ADMIN", scope = "National",
                maxApprovalAmount = Money.fromRupees(100_000_000L), discountApprovalCapPct = 30.0, sortOrder = 40,
            ),
            AuthorityLevel(
                name = "Board / Owner", roleName = "OWNER", scope = "Board",
                maxApprovalAmount = Money.fromRupees(10_000_000_000L), discountApprovalCapPct = 100.0, sortOrder = 50,
            ),
        )
    }
}
