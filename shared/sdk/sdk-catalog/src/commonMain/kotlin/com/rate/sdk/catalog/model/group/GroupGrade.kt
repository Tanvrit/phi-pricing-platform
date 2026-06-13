package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A group grade/band (e.g. "Grade A", "Management", "Workmen") that bundles a set of benefit
 * schedules and a sum-insured. Group policies assign employees to grades; each grade carries
 * its own [benefitScheduleRefs] and SI.
 */
@Serializable
data class GroupGrade(
    @SerialName("_id") override val id: String = newId(),
    /** Owning [GroupProductConfig] id. */
    @SerialName("groupProductRef") val groupProductRef: String = "",
    @SerialName("grade") val grade: String,
    @SerialName("description") val description: String = "",
    @SerialName("sumInsured") val sumInsured: Money = Money.ZERO,
    @SerialName("benefitScheduleRefs") val benefitScheduleRefs: List<String> = emptyList(),
    /** Owning [EligibilityCriteria] id when this grade has grade-specific eligibility. */
    @SerialName("eligibilityRef") val eligibilityRef: String? = null,
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
