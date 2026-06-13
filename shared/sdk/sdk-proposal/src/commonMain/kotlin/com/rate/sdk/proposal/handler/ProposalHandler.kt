package com.rate.sdk.proposal.handler

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.time.Now
import com.rate.core.base.util.ValidationResult
import com.rate.core.base.util.Validators
import com.rate.core.money.toMoney
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.proposal.event.ProposalEvent
import com.rate.sdk.proposal.event.ProposalEventSink
import com.rate.sdk.proposal.model.BankDetails
import com.rate.sdk.proposal.model.KycState
import com.rate.sdk.proposal.model.PaymentInfo
import com.rate.sdk.proposal.model.PaymentStatus
import com.rate.sdk.proposal.model.Proposal
import com.rate.sdk.proposal.model.ProposalMember
import com.rate.sdk.proposal.model.ProposalStatus
import com.rate.sdk.proposal.repository.ProposalRepository
import kotlin.random.Random

/**
 * Orchestrates the buy-online PROPOSAL: validate the submission (mobile / IFSC / account number
 * via the relocated core [Validators]), mint a human-readable proposal number, persist a durable
 * [Proposal] through the [ProposalRepository] PORT, and emit a [ProposalEvent.ProposalCreated]
 * (the monolith audit-recorded `proposal.created` inline; the re-arch lifts it to an event the
 * server can fan into sdk-audit).
 *
 * KEY DIFFERENCE FROM THE MONOLITH: the old `/proposal` route had NO durable entity — it only
 * generated a number and audit-logged it, so the customer-facing `GET /proposal/{number}` always
 * returned a canned "Under Review". This handler creates and persists a real [Proposal], so the
 * track endpoint returns the actual stored status and the operator console can list/manage them.
 *
 * Pure orchestration over the injected repository PORT — no IO of its own, runs on every KMP
 * target. The Money/Double boundary is honoured here: the journey's Double premiums are converted
 * to [Money] only at proposal assembly ([toMoney]).
 */
class ProposalHandler(
    private val repository: ProposalRepository,
    private val events: ProposalEventSink = ProposalEventSink.NOOP,
    private val numberGen: () -> String = ::defaultProposalNumber,
) {

    /**
     * Validate and create a proposal from a journey submission. Doubles ([annualPremium] /
     * [totalIncludingGst]) come straight off the journey premium step and are converted to Money
     * at assembly.
     */
    suspend fun create(submission: ProposalSubmission, actor: String? = null): AppResult<Proposal> {
        val errors = validate(submission)
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))

        val now = Now.instant()
        val proposal = Proposal(
            proposalNumber = numberGen(),
            status = ProposalStatus.UNDER_REVIEW,
            mobile = submission.mobile,
            planTier = submission.planTier,
            planRef = submission.planRef,
            quoteRef = submission.quoteRef,
            partyRef = submission.partyRef,
            sumInsured = submission.sumInsured,
            tenure = Tenure.entries.firstOrNull { it.years == submission.tenure } ?: Tenure.ONE_YEAR,
            members = submission.members,
            selectedAddOnIds = submission.selectedAddOnIds,
            annualPremium = submission.annualPremium.toMoney(),
            totalIncludingGst = submission.totalIncludingGst.toMoney(),
            payment = PaymentInfo(
                status = PaymentStatus.PENDING,
                amount = submission.totalIncludingGst.toMoney(),
                bankDetails = submission.bankDetails,
            ),
            kyc = submission.kyc ?: KycState(),
            submittedAt = now,
        )

        val saved = repository.create(proposal, actor)
        events.emit(
            ProposalEvent.ProposalCreated(
                proposalNumber = saved.proposalNumber,
                planTier = saved.planTier,
                planRef = saved.planRef,
                sumInsured = saved.sumInsured,
                totalIncludingGst = submission.totalIncludingGst,
                quoteRef = saved.quoteRef,
                actor = actor,
                at = now,
            ),
        )
        return AppResult.Ok(saved)
    }

    /** Re-hydrate a proposal by its human-readable number (the customer-facing track endpoint). */
    suspend fun track(proposalNumber: String): AppResult<Proposal> {
        val proposal = repository.getByProposalNumber(proposalNumber)
            ?: return AppResult.Err(DomainError.NotFound("Proposal", proposalNumber))
        return AppResult.Ok(proposal)
    }

    /** All non-deleted proposals for a mobile (resume / "my applications"). */
    suspend fun listByMobile(mobile: String): List<Proposal> = repository.listByMobile(mobile)

    /** Paged proposal list for the operator dashboard / buy-online funnel. */
    suspend fun list(req: PageRequest = PageRequest()): Page<Proposal> = repository.list(req)

    /**
     * Transition a proposal's lifecycle status (operator action — approve/reject/issue). Enforces
     * optimistic concurrency via the proposal's current [Proposal.v].
     */
    suspend fun changeStatus(
        proposalNumber: String,
        to: ProposalStatus,
        actor: String? = null,
    ): AppResult<Proposal> {
        val current = repository.getByProposalNumber(proposalNumber)
            ?: return AppResult.Err(DomainError.NotFound("Proposal", proposalNumber))
        if (current.status == to) return AppResult.Ok(current)

        val now = Now.instant()
        val updated = current.copy(status = to, updatedAt = now)
        val saved = repository.update(updated, current.v, actor)
        events.emit(
            ProposalEvent.ProposalStatusChanged(saved.proposalNumber, current.status, to, actor, now),
        )
        return AppResult.Ok(saved)
    }

    // ── validation ─────────────────────────────────────────────────────────────

    /** Validate a submission without persisting. Empty list = valid. */
    fun validate(submission: ProposalSubmission): List<String> = buildList {
        addError(Validators.mobile(submission.mobile))
        if (submission.planTier.isBlank()) add("Plan tier is required")
        if (submission.sumInsured <= 0) add("Sum insured must be positive")
        if (submission.annualPremium < 0.0) add("Annual premium cannot be negative")
        submission.bankDetails?.let { bank ->
            addError(Validators.accountNumber(bank.accountNumber))
            addError(Validators.ifsc(bank.ifsc))
        }
    }

    private fun MutableList<String>.addError(r: ValidationResult) {
        (r as? ValidationResult.Invalid)?.let { add(it.message) }
    }
}

/**
 * The inputs needed to create a [Proposal]. Premiums are Double (straight off the journey premium
 * step, per the rating contract) and converted to [Money] at assembly. RELOCATED/repackaged from
 * the monolith's `ProposalRequest` route DTO, enriched with the plan/quote/party refs the durable
 * entity needs.
 */
data class ProposalSubmission(
    val mobile: String,
    val planTier: String,
    val planRef: String = "",
    val quoteRef: String? = null,
    val partyRef: String? = null,
    val sumInsured: Long,
    val tenure: Int = 1,
    val annualPremium: Double,
    val totalIncludingGst: Double,
    val members: List<ProposalMember> = emptyList(),
    val selectedAddOnIds: List<String> = emptyList(),
    val bankDetails: BankDetails? = null,
    val kyc: KycState? = null,
)

/**
 * Pure-KMP proposal-number generator matching the monolith's `PHI-<base36(ms)>-<base36(rand)>`
 * shape, using [Now.epochMillis] (no `System.currentTimeMillis`) and [Random] for KMP parity.
 */
internal fun defaultProposalNumber(): String {
    val ms = Now.epochMillis()
    val rand = Random.nextInt(0, 0xFFFFFF)
    return "PHI-" + ms.toString(36).uppercase() + "-" + rand.toString(36).uppercase().padStart(5, '0')
}
