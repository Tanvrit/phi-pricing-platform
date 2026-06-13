package com.rate.sdk.party.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.sdk.party.model.Sex
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Relationship of a census life to the employee (group lives are keyed by the
 * employee, with dependents hanging off the same `empId`).
 */
@Serializable
enum class CensusRelation { EMPLOYEE, SPOUSE, CHILD, PARENT, PARENT_IN_LAW }

/**
 * One insured life on a group census row. `grade` lets an employer band benefits
 * (e.g. Grade A executives at ₹50L, Grade C staff at ₹5L); aggregation rolls up
 * by (grade × age-band) for rating.
 */
@Serializable
data class CensusMember(
    @SerialName("empId") val empId: String,
    @SerialName("name") val name: String = "",
    @SerialName("age") val age: Int,
    @SerialName("gender") val gender: Sex = Sex.MALE,
    @SerialName("grade") val grade: String = "DEFAULT",
    @SerialName("relation") val relation: CensusRelation = CensusRelation.EMPLOYEE,
    @SerialName("sumInsured") val sumInsured: Long = 0,
)

/**
 * A GROUP census — the full set of insured [lives] submitted for a [GroupEmployer]
 * (referenced by [employerPartyRef]). It is transactional party data ([BaseDataClass],
 * not config), persisted/queried through [com.rate.sdk.party.repository.CensusRepository].
 *
 * [lives] is denormalised onto the document for stamping/integrity; it is the
 * count of [members] but kept explicit because partial/streamed ingests update it.
 */
@Serializable
data class Census(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("employerPartyRef") val employerPartyRef: String,
    @SerialName("lives") val lives: Int = 0,
    @SerialName("members") val members: List<CensusMember> = emptyList(),
    /** Optional label for re-uploads / quarter (e.g. "FY26-Q1"). */
    @SerialName("label") val label: String = "",
    // ── BaseDataClass envelope ───────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    /** True when the denormalised [lives] count is consistent with [members]. */
    val livesConsistent: Boolean get() = lives == members.size
}
