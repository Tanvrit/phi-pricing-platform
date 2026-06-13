package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a [SurgicalSublimit] band is applied — whether the percent-of-SI cap is per eye,
 * per insured person, or shared across the whole family. Drives quoting / claims limits
 * (e.g. cataract has distinct per-eye, per-person both-eye and per-family rows).
 */
@Serializable
enum class SublimitScope {
    PER_EYE,
    PER_PERSON,
    PER_FAMILY,
}

/**
 * One row of the "Sublimits on Treatments/Illness/Disease/Surgery/Medical Condition (Per Claim)"
 * table — relocated from the GROUP EE annexure
 * (`Product Benefit Table EE GHI/Annexure.csv`). Caps are expressed as a percentage of the
 * opted Sum Insured (e.g. Min 10% of SI, Max 90% of SI), so they remain SI-agnostic and are
 * resolved at quote time. The list is indicative; customised sublimits can be offered per group.
 */
@Serializable
data class SurgicalSublimit(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("slNo") val slNo: Int = 0,
    @SerialName("surgeryName") val surgeryName: String,
    /** Optional grouping/category heading for the surgery (free text; null when ungrouped). */
    @SerialName("category") val category: String? = null,
    /** Lower cap as a fraction of opted SI (e.g. 0.10 for "10% of SI"); null if open-ended. */
    @SerialName("minPercentOfSI") val minPercentOfSI: Double? = null,
    /** Upper cap as a fraction of opted SI (e.g. 0.90 for "90% of SI"); null if open-ended. */
    @SerialName("maxPercentOfSI") val maxPercentOfSI: Double? = null,
    @SerialName("scope") val scope: SublimitScope = SublimitScope.PER_PERSON,
    @SerialName("displayOrder") val displayOrder: Int = 0,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity
