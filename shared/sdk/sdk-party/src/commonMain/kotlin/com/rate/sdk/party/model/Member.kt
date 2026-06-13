package com.rate.sdk.party.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.Member
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Relationship of a member to the retail proposer. String-backed (the rating
 * model's `Member.relationship` is a free string for engine parity) but enumerated
 * here so the UI and validation have a closed vocabulary.
 */
@Serializable
enum class Relationship(val label: String, val isAdult: Boolean) {
    SELF("Self", true),
    SPOUSE("Spouse", true),
    SON("Son", false),
    DAUGHTER("Daughter", false),
    FATHER("Father", true),
    MOTHER("Mother", true),
    FATHER_IN_LAW("Father-in-law", true),
    MOTHER_IN_LAW("Mother-in-law", true);

    companion object {
        fun fromLabel(label: String): Relationship? =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

/**
 * A persisted retail floater member. The transient rating-time shape is
 * [com.rate.core.rating.ports.model.Member] (Double/engine parity, no persistence);
 * `PartyMember` is the durable, transactional record belonging to a
 * [RetailProposer]. It is a [BaseDataClass] (not a config entity) — members are
 * transactional party data, not admin-managed catalog config.
 *
 * Use [toRatingMember] to project into the rating model when assembling a QuoteRequest.
 */
@Serializable
data class PartyMember(
    @SerialName("_id") override val id: String = newId(),
    /** Owning proposer's `_id`. */
    @SerialName("proposerPartyRef") val proposerPartyRef: String,
    /** 1-based stable ordinal within the floater (mirrors rating `Member.memberId`). */
    @SerialName("memberNo") val memberNo: Int,
    @SerialName("name") val name: String = "",
    @SerialName("relationship") val relationship: Relationship = Relationship.SELF,
    @SerialName("age") val age: Int,
    @SerialName("gender") val gender: Sex = Sex.MALE,
    @SerialName("dob") val dob: LocalDate? = null,
    /** Pre-existing disease declared for this member (excludes from cover where required). */
    @SerialName("hasPed") val hasPed: Boolean = false,
    /** Critical illness declared for this member. */
    @SerialName("hasCriticalIllness") val hasCriticalIllness: Boolean = false,
    /** Per-member sum insured (individual-SI plans); null = uses the floater SI. */
    @SerialName("sumInsured") val sumInsured: Long? = null,
    // ── BaseDataClass envelope ───────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {

    val isAdult: Boolean get() = age >= 18

    /** Project into the engine's rating-time member shape. */
    fun toRatingMember(): Member = Member(
        memberId = memberNo,
        age = age,
        relationship = relationship.label,
        gender = gender.code,
    )
}
