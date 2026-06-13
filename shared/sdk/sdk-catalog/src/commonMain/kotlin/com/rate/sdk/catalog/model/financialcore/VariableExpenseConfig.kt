package com.rate.sdk.catalog.model.financialcore

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Category of variable (premium-proportional) expense loaded into the pricing build (Dorian slide
 * 09). Replaces the previously hardcoded expense ratios.
 */
@Serializable
enum class VariableExpenseType { COMMISSION, ADMIN, MARKETING, OPERATIONS, TECHNOLOGY }

/**
 * A variable expense ratio loaded into premium as a percentage of `basis`. Effective-dated so the
 * expense schedule can be re-tuned each financial year without a deploy.
 */
@Serializable
data class VariableExpenseConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("expenseType") val expenseType: VariableExpenseType = VariableExpenseType.ADMIN,
    @SerialName("ratePct") val ratePct: Double = 0.0,
    @SerialName("basis") val basis: String = "Gross written premium",
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    @SerialName("effectiveTo") val effectiveTo: String = "",
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
        fun defaults(): List<VariableExpenseConfig> = listOf(
            VariableExpenseConfig(
                name = "Broker commission",
                expenseType = VariableExpenseType.COMMISSION,
                ratePct = 7.5,
                basis = "Gross written premium",
                effectiveFrom = "2025-04-01",
            ),
            VariableExpenseConfig(
                name = "Direct sales commission",
                expenseType = VariableExpenseType.COMMISSION,
                ratePct = 2.5,
                basis = "Gross written premium",
                effectiveFrom = "2025-04-01",
            ),
            VariableExpenseConfig(
                name = "Policy administration",
                expenseType = VariableExpenseType.ADMIN,
                ratePct = 4.0,
                basis = "Gross written premium",
                effectiveFrom = "2025-04-01",
            ),
            VariableExpenseConfig(
                name = "Marketing and acquisition",
                expenseType = VariableExpenseType.MARKETING,
                ratePct = 3.0,
                basis = "Gross written premium",
                effectiveFrom = "2025-04-01",
            ),
            VariableExpenseConfig(
                name = "Operations and claims handling",
                expenseType = VariableExpenseType.OPERATIONS,
                ratePct = 5.0,
                basis = "Earned premium",
                effectiveFrom = "2025-04-01",
            ),
            VariableExpenseConfig(
                name = "Technology and platform",
                expenseType = VariableExpenseType.TECHNOLOGY,
                ratePct = 2.0,
                basis = "Gross written premium",
                effectiveFrom = "2025-04-01",
            ),
        )
    }
}
