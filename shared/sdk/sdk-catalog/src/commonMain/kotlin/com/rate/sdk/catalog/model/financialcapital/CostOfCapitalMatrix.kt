package com.rate.sdk.catalog.model.financialcapital

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The risk tier a cost-of-capital row applies to (Dorian slide 10 — cost of capital by risk). */
@Serializable
enum class RiskTier { LOW, MEDIUM, HIGH }

/**
 * A cost-of-capital / capital-charge row by risk tier. The pricing loadings add a return-on-capital
 * margin so the premium covers the capital the regulator requires the insurer to hold against the
 * risk. [cocRatePct] is the cost-of-capital rate (return demanded on held capital) and
 * [capitalChargePct] is the solvency capital charge applied to the exposure — both percentages.
 * [effectiveFrom]/[effectiveTo] are ISO "YYYY-MM-DD" date strings.
 */
@Serializable
data class CostOfCapitalMatrix(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("riskTier") val riskTier: RiskTier = RiskTier.MEDIUM,
    @SerialName("cocRatePct") val cocRatePct: Double = 0.0,
    @SerialName("capitalChargePct") val capitalChargePct: Double = 0.0,
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    @SerialName("effectiveTo") val effectiveTo: String = "",
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
        fun defaults(): List<CostOfCapitalMatrix> = listOf(
            CostOfCapitalMatrix(
                name = "Low Risk - FY26",
                riskTier = RiskTier.LOW,
                cocRatePct = 8.0,
                capitalChargePct = 15.0,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            CostOfCapitalMatrix(
                name = "Medium Risk - FY26",
                riskTier = RiskTier.MEDIUM,
                cocRatePct = 10.0,
                capitalChargePct = 22.5,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            CostOfCapitalMatrix(
                name = "High Risk - FY26",
                riskTier = RiskTier.HIGH,
                cocRatePct = 13.0,
                capitalChargePct = 30.0,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
        )
    }
}
