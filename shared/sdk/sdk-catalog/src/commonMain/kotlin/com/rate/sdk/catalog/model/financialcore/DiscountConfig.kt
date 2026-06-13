package com.rate.sdk.catalog.model.financialcore

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kind of discount lever (Dorian slide 09). Replaces the previously hardcoded discount cap by an
 * effective-dated, admin-editable master so actuarial can re-tune levers without a code change.
 */
@Serializable
enum class DiscountType { GROUP_SIZE, TENURE, LONG_TERM, EMPLOYEE_VOLUNTARY, LOYALTY, PORTABILITY }

/**
 * One discount lever in the rating waterfall — its rate and the hard cap it contributes toward the
 * overall discount ceiling. Effective-dated so a future schedule can be staged alongside the live
 * one. `basis` documents what the rate is applied to (e.g. "Gross premium", "Group size band").
 */
@Serializable
data class DiscountConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("type") val type: DiscountType = DiscountType.GROUP_SIZE,
    @SerialName("basis") val basis: String = "Gross premium",
    @SerialName("ratePct") val ratePct: Double = 0.0,
    @SerialName("maxCapPct") val maxCapPct: Double = 0.0,
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    @SerialName("effectiveTo") val effectiveTo: String = "",
    @SerialName("productLine") val productLine: String = "GROUP",
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
        fun defaults(): List<DiscountConfig> = listOf(
            DiscountConfig(
                name = "Overall discount cap",
                type = DiscountType.GROUP_SIZE,
                basis = "Sum of all applied discounts",
                ratePct = 0.0,
                maxCapPct = 30.0,
                effectiveFrom = "2025-04-01",
                productLine = "GROUP",
                notes = "Hard ceiling — total of all levers may not exceed this.",
            ),
            DiscountConfig(
                name = "Group size discount (100-499 lives)",
                type = DiscountType.GROUP_SIZE,
                basis = "Census headcount band",
                ratePct = 5.0,
                maxCapPct = 10.0,
                effectiveFrom = "2025-04-01",
                productLine = "GROUP",
            ),
            DiscountConfig(
                name = "Group size discount (500+ lives)",
                type = DiscountType.GROUP_SIZE,
                basis = "Census headcount band",
                ratePct = 10.0,
                maxCapPct = 12.5,
                effectiveFrom = "2025-04-01",
                productLine = "GROUP",
            ),
            DiscountConfig(
                name = "Long-term tenure discount (3yr)",
                type = DiscountType.LONG_TERM,
                basis = "Annual gross premium",
                ratePct = 10.0,
                maxCapPct = 10.0,
                effectiveFrom = "2025-04-01",
                productLine = "RETAIL",
            ),
            DiscountConfig(
                name = "Multi-year tenure discount (2yr)",
                type = DiscountType.TENURE,
                basis = "Annual gross premium",
                ratePct = 7.5,
                maxCapPct = 7.5,
                effectiveFrom = "2025-04-01",
                productLine = "RETAIL",
            ),
            DiscountConfig(
                name = "Employee voluntary top-up discount",
                type = DiscountType.EMPLOYEE_VOLUNTARY,
                basis = "Voluntary SI premium",
                ratePct = 5.0,
                maxCapPct = 7.5,
                effectiveFrom = "2025-04-01",
                productLine = "GROUP",
            ),
            DiscountConfig(
                name = "Loyalty renewal discount",
                type = DiscountType.LOYALTY,
                basis = "Renewal gross premium",
                ratePct = 5.0,
                maxCapPct = 5.0,
                effectiveFrom = "2025-04-01",
                productLine = "GROUP",
            ),
            DiscountConfig(
                name = "Portability no-claim discount",
                type = DiscountType.PORTABILITY,
                basis = "Ported gross premium",
                ratePct = 2.5,
                maxCapPct = 5.0,
                effectiveFrom = "2025-04-01",
                productLine = "RETAIL",
            ),
        )
    }
}
