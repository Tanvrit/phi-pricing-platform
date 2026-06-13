package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Medical Trend Factor (MTF) — the annual medical-cost-inflation assumption used to project claims
 * forward when pricing renewals and multi-year contracts. Captured per financial year, optionally
 * per region and per clinical category, with an effective window so a year's assumption can be
 * superseded mid-cycle.
 *
 * Effective dates are ISO "YYYY-MM-DD" strings (no DATE field kind yet).
 */
@Serializable
data class MedicalTrendFactor(
    @SerialName("_id") override val id: String = newId(),
    /** Financial-year label this factor applies to (e.g. "FY2024-25"). */
    @SerialName("yearLabel") val yearLabel: String,
    /** Region/zone the trend applies to ("All India", "Metro", "Zone A", …). */
    @SerialName("region") val region: String = "All India",
    /** Medical trend / inflation percentage for the year (e.g. 12.5 = +12.5%). */
    @SerialName("trendPct") val trendPct: Double = 0.0,
    /** Clinical category bucket ("Overall", "In-patient", "Pharmacy", "Diagnostics", …). */
    @SerialName("category") val category: String = "Overall",
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
        fun defaults(): List<MedicalTrendFactor> = listOf(
            MedicalTrendFactor(
                yearLabel = "FY2022-23", region = "All India", trendPct = 11.0, category = "Overall",
                effectiveFrom = "2022-04-01", effectiveTo = "2023-03-31", active = false,
            ),
            MedicalTrendFactor(
                yearLabel = "FY2023-24", region = "All India", trendPct = 12.0, category = "Overall",
                effectiveFrom = "2023-04-01", effectiveTo = "2024-03-31", active = false,
            ),
            MedicalTrendFactor(
                yearLabel = "FY2024-25", region = "All India", trendPct = 13.0, category = "Overall",
                effectiveFrom = "2024-04-01", effectiveTo = "2025-03-31",
            ),
            MedicalTrendFactor(
                yearLabel = "FY2024-25", region = "Metro", trendPct = 14.5, category = "In-patient",
                effectiveFrom = "2024-04-01", effectiveTo = "2025-03-31",
            ),
            MedicalTrendFactor(
                yearLabel = "FY2025-26", region = "All India", trendPct = 13.5, category = "Overall",
                effectiveFrom = "2025-04-01", effectiveTo = "2026-03-31",
            ),
        )
    }
}
