package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One benefit line in a schedule: which cover, at what limit, with optional sub-limit copy. */
@Serializable
data class BenefitScheduleLine(
    /** Catalog [com.rate.sdk.catalog.model.Cover] code this line configures. */
    @SerialName("coverCode") val coverCode: String,
    @SerialName("coverName") val coverName: String = "",
    @SerialName("limit") val limit: Money = Money.ZERO,
    /** Selected option label (e.g. chosen room-rent option). */
    @SerialName("selectedOption") val selectedOption: String? = null,
    @SerialName("subLimitText") val subLimitText: String = "",
    @SerialName("included") val included: Boolean = true,
)

/**
 * A named schedule of benefits (the resolved cover limits for a [GroupGrade]). The PBT "Part
 * 1 Base Covers / Part 2 Optional Covers" tables become BenefitSchedules per grade.
 */
@Serializable
data class BenefitSchedule(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("groupProductRef") val groupProductRef: String = "",
    @SerialName("name") val name: String,
    @SerialName("lines") val lines: List<BenefitScheduleLine> = emptyList(),
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
