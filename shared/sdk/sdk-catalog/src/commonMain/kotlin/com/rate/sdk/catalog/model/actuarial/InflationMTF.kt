package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * General (non-medical) inflation assumption used to escalate fixed expenses, sums insured and
 * indexed benefits over a contract's life — distinct from the clinical [MedicalTrendFactor].
 * Captured per financial year with the index basis it tracks and an effective window.
 *
 * Effective dates are ISO "YYYY-MM-DD" strings (no DATE field kind yet).
 */
@Serializable
data class InflationMTF(
    @SerialName("_id") override val id: String = newId(),
    /** Financial-year label (e.g. "FY2024-25"). */
    @SerialName("yearLabel") val yearLabel: String,
    /** General inflation percentage for the year (e.g. 6.0 = +6%). */
    @SerialName("inflationPct") val inflationPct: Double = 0.0,
    /** Index basis tracked ("CPI", "WPI", "Wage", "GDP deflator", …). */
    @SerialName("basis") val basis: String = "CPI",
    /** Effective-from date, ISO "YYYY-MM-DD". */
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    /** Effective-to date, ISO "YYYY-MM-DD" (blank = open-ended). */
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
        fun defaults(): List<InflationMTF> = listOf(
            InflationMTF(
                yearLabel = "FY2022-23", inflationPct = 6.7, basis = "CPI",
                effectiveFrom = "2022-04-01", effectiveTo = "2023-03-31", active = false,
            ),
            InflationMTF(
                yearLabel = "FY2023-24", inflationPct = 5.4, basis = "CPI",
                effectiveFrom = "2023-04-01", effectiveTo = "2024-03-31", active = false,
            ),
            InflationMTF(
                yearLabel = "FY2024-25", inflationPct = 4.8, basis = "CPI",
                effectiveFrom = "2024-04-01", effectiveTo = "2025-03-31",
            ),
            InflationMTF(
                yearLabel = "FY2024-25", inflationPct = 3.2, basis = "WPI",
                effectiveFrom = "2024-04-01", effectiveTo = "2025-03-31",
            ),
            InflationMTF(
                yearLabel = "FY2025-26", inflationPct = 4.5, basis = "CPI",
                effectiveFrom = "2025-04-01", effectiveTo = "2026-03-31",
            ),
        )
    }
}
