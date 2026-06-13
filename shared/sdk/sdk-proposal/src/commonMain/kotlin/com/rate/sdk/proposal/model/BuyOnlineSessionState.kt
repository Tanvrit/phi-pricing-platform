package com.rate.sdk.proposal.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Buy-online save+resume — snapshot of the customer's in-flight journey.
 *
 * RELOCATED from the monolith's `com.rate.domain.model.BuyOnlineSessionState`. The original
 * was a flat `@Serializable` data class keyed by a free [sessionId] string and persisted via
 * the server's `BuyOnlineSessionRepository`. Here it becomes a transactional [BaseDataClass]
 * so it persists uniformly through [com.rate.sdk.proposal.repository.SessionRepository] (the
 * Mongo-backed actual with a TTL index lives in server-persistence), while keeping the exact
 * journey fields the client save+resume contract relies on.
 *
 * The original [sessionId] is preserved as a first-class field (it is the resume bearer sent
 * via the `?session=` URL param — there is no auth on the session endpoints; the id IS the
 * bearer, mirroring how payment-aggregator resume links work). The [BaseDataClass.id] mirrors
 * it so generic persistence keys on the same value.
 *
 * Captures only the fields that take effort to re-enter; payment / KYC / proposal data is
 * excluded (sensitive + harder to safely rehydrate). [currentScreen] carries the journey
 * screen's simple name so the client can resolve it back via a `when`.
 */
@Serializable
data class BuyOnlineSessionState(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("currentScreen") val currentScreen: String,
    @SerialName("mobile") val mobile: String = "",
    @SerialName("pincode") val pincode: String = "",
    @SerialName("eldestAge") val eldestAge: String = "",
    /** MemberType.name values selected in the journey. */
    @SerialName("selectedMembers") val selectedMembers: List<String> = emptyList(),
    @SerialName("kidsCount") val kidsCount: Int = 0,
    @SerialName("hasPED") val hasPED: Boolean = false,
    @SerialName("pedMembers") val pedMembers: List<String> = emptyList(),
    @SerialName("hasCriticalIllness") val hasCriticalIllness: Boolean = false,
    @SerialName("criticalIllnessMembers") val criticalIllnessMembers: List<String> = emptyList(),
    @SerialName("selectedTier") val selectedTier: String = "PREMIER",
    @SerialName("selectedSumInsured") val selectedSumInsured: Long = 1_000_000L,
    @SerialName("selectedTenure") val selectedTenure: Int = 1,
    @SerialName("selectedAddOnIds") val selectedAddOnIds: List<String> = emptyList(),
    /** Client-saved timestamp (ISO-8601). NB: the server stamps its own [updatedAt] for funnel math. */
    @SerialName("updatedAtIso") val updatedAtIso: String = "",
    // ── BaseDataClass envelope (id mirrors sessionId so persistence keys on it) ─
    @SerialName("_id") override val id: String = sessionId.ifBlank { newId() },
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass
