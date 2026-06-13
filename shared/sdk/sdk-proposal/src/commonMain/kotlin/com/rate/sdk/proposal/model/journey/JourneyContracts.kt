package com.rate.sdk.proposal.model.journey

import com.rate.core.rating.ports.model.Member
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport-agnostic request/result contracts for the buy-online journey's pre-proposal steps:
 * pincode → hospital lookup, eligibility (PED / critical-illness exclusion), and premium
 * (delegated to the quoting engine). These are the inputs/outputs of the handlers in
 * [com.rate.sdk.proposal.handler]; the Ktor wire DTOs in
 * [com.rate.sdk.proposal.network] mirror them (preserving the monolith's JSON field names).
 *
 * RELOCATED from the monolith's `BuyOnlineRoutes` request/response data classes
 * (EligibilityRequest/Response, PremiumRequest/Response, HospitalsResponse), repackaged onto
 * the core rating-contract [Member] type.
 */

// ── Hospital / pincode lookup ────────────────────────────────────────────────

/** Pincode lookup request (the journey's location step). */
@Serializable
data class HospitalLookupRequest(
    @SerialName("pincode") val pincode: String,
)

/**
 * Network-hospital availability for a pincode. The monolith returned a stable, deterministic
 * count per pincode (Phase-2 replaces it with a real network-hospital lookup); the [zone]
 * resolved from the pincode (via sdk-catalog) is included so the journey can pre-fill the
 * rating zone in one round-trip.
 */
@Serializable
data class HospitalLookupResult(
    @SerialName("pincode") val pincode: String,
    @SerialName("hospitalsNearby") val hospitalsNearby: Int,
    /** Resolved rating zone label (e.g. "Zone 1"), or null if unresolved. */
    @SerialName("zone") val zone: String? = null,
)

// ── Eligibility (PED / critical-illness exclusion) ───────────────────────────

/**
 * Eligibility check inputs. Members are referenced by their journey label (MemberType.name /
 * relationship label) — the same strings the client uses for [pedMembers] /
 * [criticalIllnessMembers]. A member is excluded when they declared a PED or critical illness.
 */
@Serializable
data class EligibilityRequest(
    @SerialName("mobile") val mobile: String,
    @SerialName("hasPED") val hasPED: Boolean = false,
    @SerialName("pedMembers") val pedMembers: List<String> = emptyList(),
    @SerialName("hasCriticalIllness") val hasCriticalIllness: Boolean = false,
    @SerialName("criticalIllnessMembers") val criticalIllnessMembers: List<String> = emptyList(),
    @SerialName("members") val members: List<String> = emptyList(),
)

/** Eligibility outcome: which members can be covered and which are excluded. */
@Serializable
data class EligibilityResult(
    @SerialName("coveredMembers") val coveredMembers: List<String>,
    @SerialName("uncoveredMembers") val uncoveredMembers: List<String>,
)

// ── Premium (delegates to the quoting engine) ────────────────────────────────

/**
 * Premium request from the journey. The customer picks a tier ([tier] — "PREMIER" /
 * "SIGNATURE" / "GLOBAL"), a sum insured, a tenure (1..5 years) and a set of add-on bundle
 * ids; the [com.rate.sdk.proposal.handler.PremiumHandler] resolves the tier→Plan mapping and
 * the add-on ids→cover selections (via sdk-catalog), assembles a `QuoteRequest`, and prices it
 * through sdk-quoting's `QuoteHandler`.
 */
@Serializable
data class PremiumRequest(
    @SerialName("sumInsured") val sumInsured: Long,
    @SerialName("tier") val tier: String,
    @SerialName("tenure") val tenure: Int,
    @SerialName("addOnIds") val addOnIds: List<String> = emptyList(),
    @SerialName("primaryAge") val primaryAge: Int = 35,
    @SerialName("familyType") val familyType: String = "1A",
    @SerialName("zone") val zone: String = "Zone 1",
    @SerialName("members") val members: List<Member> = emptyList(),
)

/**
 * Priced premium for the journey. Doubles (INR) for parity with the engine — the buy-online
 * UI displays these directly. Conversion to [com.rate.core.money.Money] happens only when a
 * proposal is assembled (see [com.rate.sdk.proposal.model.Proposal]).
 */
@Serializable
data class PremiumResult(
    @SerialName("annualPremium") val annualPremium: Double,
    @SerialName("monthlyPremium") val monthlyPremium: Double,
    @SerialName("gstAmount") val gstAmount: Double,
    @SerialName("totalIncludingGst") val totalIncludingGst: Double,
    @SerialName("basePremium") val basePremium: Double,
    @SerialName("totalAddons") val totalAddons: Double,
    @SerialName("totalDiscountAmount") val totalDiscountAmount: Double,
    @SerialName("tenureDiscountRate") val tenureDiscountRate: Double = 0.0,
    /** Saved quote id when the premium was priced-and-saved (else null for calculate-only). */
    @SerialName("quoteRef") val quoteRef: String? = null,
)
