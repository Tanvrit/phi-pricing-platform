package com.rate.sdk.catalog.model.underwriting

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The rating dimension a [RatingParameter] keys off — the nine axes of the Dorian "slide 03"
 * U-factor grid. Each row in the grid says "for this dimension at this key, multiply by this
 * factor", and the rating engine multiplies the matching rows together.
 */
@Serializable
enum class RatingDimension {
    AGE, ZONE, SUM_INSURED, FAMILY_TYPE, INDUSTRY, GROUP_SIZE, TENURE, GENDER, CLAIMS_HISTORY
}

/**
 * One cell of the underwriting rating (U-factor) grid: a multiplicative factor applied when a risk
 * matches the given [dimension] + [paramKey], scoped to a product line and an effective-date window.
 * Rows are versioned + draft/publish via the [ConfigEntity] envelope so an actuary can stage a new
 * factor table and publish it atomically.
 */
@Serializable
data class RatingParameter(
    @SerialName("_id") override val id: String = newId(),
    /** Operator-facing label for this row (e.g. "Age 46-55 loading"). */
    @SerialName("name") val name: String,
    /** Which rating axis this row keys off. */
    @SerialName("dimension") val dimension: RatingDimension = RatingDimension.AGE,
    /** The matched value within the dimension (e.g. "46-55", "ZONE_A", "MANUFACTURING"). */
    @SerialName("paramKey") val paramKey: String,
    /** Multiplicative factor applied to the base rate when this row matches (1.0 = neutral). */
    @SerialName("factorValue") val factorValue: Double = 1.0,
    /** Product line this factor applies to: "RETAIL" or "GROUP" (enum-as-text). */
    @SerialName("productLine") val productLine: String = "GROUP",
    /** Inclusive start of the effective window, ISO "YYYY-MM-DD". */
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    /** Inclusive end of the effective window, ISO "YYYY-MM-DD" (blank = open-ended). */
    @SerialName("effectiveTo") val effectiveTo: String = "",
    /** Display/priority ordering within a dimension. */
    @SerialName("sortOrder") val sortOrder: Int = 0,
    /** Whether this factor row is currently used by the rating engine. */
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
        fun defaults(): List<RatingParameter> = listOf(
            RatingParameter(name = "Age 18-35", dimension = RatingDimension.AGE, paramKey = "18-35", factorValue = 1.0, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 10),
            RatingParameter(name = "Age 46-55", dimension = RatingDimension.AGE, paramKey = "46-55", factorValue = 1.35, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 20),
            RatingParameter(name = "Age 56-65", dimension = RatingDimension.AGE, paramKey = "56-65", factorValue = 1.75, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 30),
            RatingParameter(name = "Zone A (Metro)", dimension = RatingDimension.ZONE, paramKey = "ZONE_A", factorValue = 1.15, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 40),
            RatingParameter(name = "Zone C (Rest of India)", dimension = RatingDimension.ZONE, paramKey = "ZONE_C", factorValue = 0.90, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 50),
            RatingParameter(name = "Sum Insured 5L", dimension = RatingDimension.SUM_INSURED, paramKey = "500000", factorValue = 1.0, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 60),
            RatingParameter(name = "Floater 2A+2C", dimension = RatingDimension.FAMILY_TYPE, paramKey = "2A2C", factorValue = 1.60, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 70),
            RatingParameter(name = "Industry: Manufacturing", dimension = RatingDimension.INDUSTRY, paramKey = "MANUFACTURING", factorValue = 1.20, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 80),
            RatingParameter(name = "Group Size 100-499", dimension = RatingDimension.GROUP_SIZE, paramKey = "100-499", factorValue = 0.95, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 90),
            RatingParameter(name = "Tenure 1 year", dimension = RatingDimension.TENURE, paramKey = "1", factorValue = 1.0, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 100),
            RatingParameter(name = "Gender Female", dimension = RatingDimension.GENDER, paramKey = "FEMALE", factorValue = 1.05, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 110),
            RatingParameter(name = "Adverse Claims History", dimension = RatingDimension.CLAIMS_HISTORY, paramKey = "CR_GT_100", factorValue = 1.40, productLine = "GROUP", effectiveFrom = "2026-04-01", sortOrder = 120),
        )
    }
}
