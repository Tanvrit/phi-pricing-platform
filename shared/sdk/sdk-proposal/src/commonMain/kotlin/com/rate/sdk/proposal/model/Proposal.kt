package com.rate.sdk.proposal.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.Tenure
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle of a buy-online proposal. The monolith returned a free-text `status` String
 * ("Under Review"); enumerated here so the operator console and customer tracking share one
 * vocabulary. [label] is the customer-facing wording the original API used.
 */
@Serializable
enum class ProposalStatus(val label: String) {
    /** Created, persisted, but payment/KYC not yet completed. */
    DRAFT("Draft"),

    /** Submitted and queued for underwriting review (the monolith's "Under Review"). */
    UNDER_REVIEW("Under Review"),

    /** Underwriting approved; policy issuance pending. */
    APPROVED("Approved"),

    /** Policy issued (a sdk-policy Policy now exists). */
    ISSUED("Issued"),

    /** Declined by underwriting. */
    REJECTED("Rejected"),

    /** Customer abandoned / withdrew before issuance. */
    WITHDRAWN("Withdrawn");

    companion object {
        fun fromLabel(label: String): ProposalStatus =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: UNDER_REVIEW
    }
}

/**
 * One member as it appears ON a proposal — the rating-time [Member] (engine parity) plus the
 * journey eligibility flags and the resolved coverage decision. The buy-online eligibility
 * step excludes members who declared a PED or critical illness (see
 * [com.rate.sdk.proposal.handler.EligibilityHandler]); [covered] records that decision so the
 * proposal is self-describing without re-running eligibility.
 */
@Serializable
data class ProposalMember(
    @SerialName("member") val member: Member,
    @SerialName("name") val name: String = "",
    @SerialName("hasPed") val hasPed: Boolean = false,
    @SerialName("hasCriticalIllness") val hasCriticalIllness: Boolean = false,
    /** Coverage decision from the eligibility step (false = excluded). */
    @SerialName("covered") val covered: Boolean = true,
)

/**
 * The buy-online PROPOSAL — the transactional record that captures everything from the
 * customer's journey at submit time and tracks its lifecycle to issuance.
 *
 * Transactional, so it implements [BaseDataClass] (not ConfigEntity — a proposal is not
 * admin-managed catalog config; it has no draft/publish). It carries:
 *  - a human-readable [proposalNumber] ("PHI-...") for sharing/tracking (the monolith's
 *    generated suffix), indexed for [com.rate.sdk.proposal.repository.ProposalRepository.getByProposalNumber];
 *  - the [members] on the proposal with their eligibility decisions;
 *  - the plan/quote REFs ([planRef] actuarial Plan id, [quoteRef] saved sdk-quoting Quote id,
 *    [partyRef] the sdk-party RetailProposer id) — refs, not embedded copies, so the durable
 *    sources of truth stay in their owning features;
 *  - the [payment] and [kyc] snapshots;
 *  - the priced headline ([annualPremium] / [totalIncludingGst]) as [Money] (converted from
 *    the engine's Double at proposal assembly, per the rating contract).
 *
 * Relocated from the monolith's `ProposalRequest`/`ProposalResponse` route DTOs, which had no
 * durable entity behind them (the server only audit-logged a generated number). This is that
 * missing entity.
 */
@Serializable
data class Proposal(
    @SerialName("_id") override val id: String = newId(),
    /** Human-readable, shareable number, e.g. "PHI-ABC123-XY9Z". Unique. */
    @SerialName("proposalNumber") val proposalNumber: String,
    @SerialName("status") val status: ProposalStatus = ProposalStatus.UNDER_REVIEW,
    /** Proposer contact mobile (resume / track key). */
    @SerialName("mobile") val mobile: String,
    /** Customer-facing tier code chosen in the journey ("PREMIER"/"SIGNATURE"/"GLOBAL"). */
    @SerialName("planTier") val planTier: String = "",
    /** Actuarial Plan id (resolved from the tier via sdk-catalog AddOn.planRef). */
    @SerialName("planRef") val planRef: String = "",
    /** Saved sdk-quoting Quote id this proposal converted from (null if priced inline). */
    @SerialName("quoteRef") val quoteRef: String? = null,
    /** Owning sdk-party RetailProposer id (once identity is persisted). */
    @SerialName("partyRef") val partyRef: String? = null,
    @SerialName("sumInsured") val sumInsured: Long = 0L,
    @SerialName("tenure") val tenure: Tenure = Tenure.ONE_YEAR,
    @SerialName("members") val members: List<ProposalMember> = emptyList(),
    @SerialName("selectedAddOnIds") val selectedAddOnIds: List<String> = emptyList(),
    /** Annual premium (after discount, before GST) — Money, converted at assembly. */
    @SerialName("annualPremium") val annualPremium: Money = Money.ZERO,
    /** Total payable including GST — Money, converted at assembly. */
    @SerialName("totalIncludingGst") val totalIncludingGst: Money = Money.ZERO,
    @SerialName("payment") val payment: PaymentInfo = PaymentInfo(),
    @SerialName("kyc") val kyc: KycState = KycState(),
    @SerialName("submittedAt") val submittedAt: Instant? = null,
    // ── BaseDataClass envelope ─────────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    /** Members not excluded by the eligibility step. */
    val coveredMembers: List<ProposalMember> get() = members.filter { it.covered }
}
