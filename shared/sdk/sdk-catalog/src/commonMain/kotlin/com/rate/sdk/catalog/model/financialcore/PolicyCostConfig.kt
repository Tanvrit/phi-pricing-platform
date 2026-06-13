package com.rate.sdk.catalog.model.financialcore

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Category of fixed/per-policy cost loaded into the pricing build. Either a flat [Money] amount or a
 * percentage of `basis` (or both, for tiered costs).
 */
@Serializable
enum class PolicyCostType { ISSUANCE, STAMP_DUTY, SERVICING, MEDICAL_EXAM }

/**
 * A per-policy cost line — a flat rupee amount and/or a percentage of `basis`. Used to load policy
 * issuance, stamp duty, servicing and medical-exam costs into the premium build.
 */
@Serializable
data class PolicyCostConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("costType") val costType: PolicyCostType = PolicyCostType.ISSUANCE,
    @SerialName("amount") val amount: Money = Money.ZERO,
    @SerialName("ratePct") val ratePct: Double = 0.0,
    @SerialName("basis") val basis: String = "Per policy",
    @SerialName("notes") val notes: String = "",
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
        fun defaults(): List<PolicyCostConfig> = listOf(
            PolicyCostConfig(
                name = "Policy issuance fee",
                costType = PolicyCostType.ISSUANCE,
                amount = Money.fromRupees(250L),
                basis = "Per policy",
            ),
            PolicyCostConfig(
                name = "Stamp duty",
                costType = PolicyCostType.STAMP_DUTY,
                amount = Money.fromRupees(1L),
                ratePct = 0.005,
                basis = "Per policy + % of SI",
            ),
            PolicyCostConfig(
                name = "Annual servicing charge",
                costType = PolicyCostType.SERVICING,
                amount = Money.fromRupees(150L),
                basis = "Per policy per year",
            ),
            PolicyCostConfig(
                name = "Pre-policy medical exam",
                costType = PolicyCostType.MEDICAL_EXAM,
                amount = Money.fromRupees(1500L),
                basis = "Per insured member (age 45+)",
            ),
            PolicyCostConfig(
                name = "Per-member servicing add-on",
                costType = PolicyCostType.SERVICING,
                amount = Money.fromRupees(50L),
                basis = "Per insured member",
            ),
            PolicyCostConfig(
                name = "Group master policy issuance",
                costType = PolicyCostType.ISSUANCE,
                amount = Money.fromRupees(1000L),
                basis = "Per group master policy",
            ),
        )
    }
}
