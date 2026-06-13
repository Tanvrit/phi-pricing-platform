package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single critical-illness in a tiered CI list (sl-no + name). */
@Serializable
data class CIItem(
    @SerialName("slNo") val slNo: Int,
    @SerialName("name") val name: String,
)

/**
 * A tiered Critical-Illness list (1/4/16/32/48/92/101 CI variants) referenced by CI covers.
 * Relocated from `data/List of CI 101 and 92.csv` + the EE `CI List.csv` Plan 1..5 tiers.
 *
 * [tieredPlanRefs] maps plan/tier labels (e.g. "Plan 1", "Plan 5") to the [Plan] ids that
 * offer this CI list count, so a CI cover can resolve its applicable list by plan.
 */
@Serializable
data class CriticalIllnessList(
    @SerialName("_id") override val id: String = newId(),
    /** Stable list code (e.g. "CI_101", "CI_92", "CI_TIER5"). */
    @SerialName("listCode") val listCode: String,
    @SerialName("name") val name: String = "",
    /** Number of CIs in the list (denormalised count = items.size). */
    @SerialName("size") val size: Int = 0,
    @SerialName("items") val items: List<CIItem> = emptyList(),
    /** Plan/tier label → list of Plan ids offering this CI count. */
    @SerialName("tieredPlanRefs") val tieredPlanRefs: Map<String, List<String>> = emptyMap(),
    /** True when this list covers women-specific critical illnesses. */
    @SerialName("isWomenSpecific") val isWomenSpecific: Boolean = false,
    /** Plan-variant label this CI list belongs to (e.g. "Premier", "Signature", ""). */
    @SerialName("planVariant") val planVariant: String? = "",
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
