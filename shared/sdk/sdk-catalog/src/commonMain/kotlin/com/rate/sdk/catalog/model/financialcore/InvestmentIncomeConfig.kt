package com.rate.sdk.catalog.model.financialcore

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Investment-income / time-value-of-money assumptions used to discount future cash flows in the
 * pricing build. Effective-dated so the assumption set can roll over each financial year.
 */
@Serializable
data class InvestmentIncomeConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("expectedYieldPct") val expectedYieldPct: Double = 0.0,
    @SerialName("discountRatePct") val discountRatePct: Double = 0.0,
    @SerialName("durationYears") val durationYears: Int = 1,
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
        fun defaults(): List<InvestmentIncomeConfig> = listOf(
            InvestmentIncomeConfig(
                name = "1-year G-Sec assumption",
                expectedYieldPct = 6.8,
                discountRatePct = 6.5,
                durationYears = 1,
                effectiveFrom = "2025-04-01",
            ),
            InvestmentIncomeConfig(
                name = "3-year portfolio assumption",
                expectedYieldPct = 7.2,
                discountRatePct = 6.9,
                durationYears = 3,
                effectiveFrom = "2025-04-01",
            ),
            InvestmentIncomeConfig(
                name = "5-year portfolio assumption",
                expectedYieldPct = 7.5,
                discountRatePct = 7.1,
                durationYears = 5,
                effectiveFrom = "2025-04-01",
            ),
            InvestmentIncomeConfig(
                name = "10-year long-duration assumption",
                expectedYieldPct = 7.8,
                discountRatePct = 7.3,
                durationYears = 10,
                effectiveFrom = "2025-04-01",
            ),
            InvestmentIncomeConfig(
                name = "Conservative reserve assumption",
                expectedYieldPct = 6.0,
                discountRatePct = 5.8,
                durationYears = 1,
                effectiveFrom = "2025-04-01",
                notes = "Used for short-tail claim reserves.",
            ),
        )
    }
}
