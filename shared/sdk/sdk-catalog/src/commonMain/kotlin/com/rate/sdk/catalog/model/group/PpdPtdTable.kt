package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which permanent-disability schedule this table encodes. */
@Serializable
enum class DisabilityTableType { PPD, PTD }

/**
 * Which side-by-side source block within the original PPD/PTD spreadsheet a table came from.
 * The source CSV groups events into LEFT/RIGHT-limb blocks plus FRACTURES and BURNS sub-tables.
 */
@Serializable
enum class PpdPtdSourceGroup { LEFT, RIGHT, FRACTURES, BURNS }

/** A single insured-event row: event description percentage of SI payable. */
@Serializable
data class PpdPtdRow(
    @SerialName("slNo") val slNo: String,
    @SerialName("insuredEvent") val insuredEvent: String,
    /** Fraction of the SI payable for this event (e.g. 1.0 = 100%, 0.5 = 50%). */
    @SerialName("percentOfSI") val percentOfSI: Double,
    /** Optional grouping/category label for this row (e.g. "Fingers", "Toes", "Burns"). */
    @SerialName("category") val category: String? = null,
    /** [slNo] of the parent row when this row is a sub-row of another event. */
    @SerialName("parentSlNo") val parentSlNo: String? = null,
    /** True when this row is a nested sub-row beneath [parentSlNo]. */
    @SerialName("isSubRow") val isSubRow: Boolean = false,
)

/**
 * A Permanent Partial / Permanent Total Disability schedule. Relocated from
 * `PBT Employer Employee Benefit (PA CI)/PPD PTD Tables.csv` (two side-by-side tables —
 * stored as two entities, one per [tableType]).
 */
@Serializable
data class PpdPtdTable(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("groupProductRef") val groupProductRef: String = "",
    @SerialName("tableType") val tableType: DisabilityTableType = DisabilityTableType.PTD,
    @SerialName("name") val name: String = "",
    @SerialName("rows") val rows: List<PpdPtdRow> = emptyList(),
    /** Which side-by-side source block this table was extracted from. */
    @SerialName("sourceGroup") val sourceGroup: PpdPtdSourceGroup = PpdPtdSourceGroup.LEFT,
    /** Free-text reviewer notes captured during ingestion/QA. */
    @SerialName("reviewNotes") val reviewNotes: String = "",
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
