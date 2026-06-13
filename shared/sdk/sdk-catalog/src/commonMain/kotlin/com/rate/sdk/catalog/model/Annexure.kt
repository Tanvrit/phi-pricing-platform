package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Broad classification of an [Annexure]. */
@Serializable
enum class AnnexureCategory {
    DAY_CARE_PROCEDURES,
    CONSUMABLES,
    OPD_MINOR_PROCEDURES,
    EXCLUSIONS,
    MODERN_TREATMENTS,
    DEFINITIONS,
    OTHER,
}

/**
 * Structural shape of an [Annexure]'s payload — tells the operator UI / consumers which of the
 * typed payload fields to read (vs. the generic [Annexure.items] string list). Structured
 * variants (sublimit table, CI tier matrix) populate their dedicated fields; [GENERIC] uses
 * only [Annexure.items].
 */
@Serializable
enum class AnnexureType {
    OPD_MINOR_PROC,
    SUBLIMIT_TABLE,
    CI_TIER_MATRIX,
    DEVICE_CATEGORIES,
    VACCINATION_LIST,
    GENERIC,
}

/**
 * One row of an [AnnexureType.SUBLIMIT_TABLE] payload — a surgery/condition with its
 * percent-of-SI caps expressed as the original free-text bounds (e.g. "10% of SI Opted").
 * Authoritative structured sublimits live in [com.rate.sdk.catalog.model.group.SurgicalSublimit];
 * this denormalised row keeps the annexure self-describing for display.
 */
@Serializable
data class AnnexureSublimitRow(
    @SerialName("slNo") val slNo: Int = 0,
    @SerialName("label") val label: String,
    @SerialName("min") val min: String = "",
    @SerialName("max") val max: String = "",
)

/**
 * One row of an [AnnexureType.CI_TIER_MATRIX] payload — a critical-illness condition mapped to
 * the tier(s)/list(s) it belongs to (e.g. CI-92 vs CI-101) with optional per-tier notes.
 */
@Serializable
data class AnnexureCiTierRow(
    @SerialName("condition") val condition: String,
    @SerialName("tiers") val tiers: List<String> = emptyList(),
    @SerialName("note") val note: String = "",
)

/**
 * A free-text list annexure referenced by covers (day-care list, OPD minor procedures,
 * consumables list, exclusions…). Relocated from `data/Annexure.csv` and the EE annexure.
 *
 * Structured day-care / consumables variants get their own typed entities
 * ([com.rate.sdk.catalog.model.group.DayCareProcedure], [..group.ConsumablesList]); this is
 * the generic catch-all list used where item structure is just an ordered set of strings.
 */
@Serializable
data class Annexure(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("annexureCode") val annexureCode: String,
    @SerialName("title") val title: String,
    @SerialName("category") val category: AnnexureCategory = AnnexureCategory.OTHER,
    @SerialName("type") val type: AnnexureType = AnnexureType.GENERIC,
    @SerialName("items") val items: List<String> = emptyList(),
    // ── Structured payload (populated per [type]; empty for GENERIC) ────────
    @SerialName("sublimitRows") val sublimitRows: List<AnnexureSublimitRow> = emptyList(),
    @SerialName("ciTierMatrix") val ciTierMatrix: List<AnnexureCiTierRow> = emptyList(),
    /** Free-text notes/instructions (e.g. "Selection of any one or more …", "indicative list"). */
    @SerialName("instructions") val instructions: List<String> = emptyList(),
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
