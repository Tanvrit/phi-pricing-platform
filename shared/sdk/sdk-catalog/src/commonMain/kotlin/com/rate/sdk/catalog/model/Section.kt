package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A benefit Section grouping a set of [Cover]s — modelled on the Product-Benefit-Table
 * "Section #" index (e.g. "1 Personal Accident", "2 Critical Illness", "9 OPD").
 *
 * `day1` / `day2` mark the GROUP PBT Day-1 vs Day-2 cover classification (see
 * `data/PBT Index.csv`). [coverRefs] is the ordered list of [Cover] ids in this section.
 */
@Serializable
data class Section(
    @SerialName("_id") override val id: String = newId(),
    /** PBT section number ("1", "1.1", "9"…). */
    @SerialName("sectionNumber") val sectionNumber: String,
    @SerialName("name") val name: String,
    /** Optional sub-section label (e.g. "1.1 Personal Accident"). */
    @SerialName("subSection") val subSection: String? = null,
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** PBT Day-1 cover (available from inception). */
    @SerialName("day1") val day1: Boolean = false,
    /** PBT Day-2 cover (indemnity / waiting-period gated). */
    @SerialName("day2") val day2: Boolean = false,
    @SerialName("coverRefs") val coverRefs: List<String> = emptyList(),
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
