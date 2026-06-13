package com.rate.sdk.policy.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle status of a policy. Finite state machine:
 *
 * ```
 *   DRAFT  → IN_REVIEW → ACTIVE
 *   ACTIVE → LAPSED              (grace period expired without renewal)
 *   ACTIVE → CANCELLED          (customer-initiated cancellation, post free-look)
 *   ACTIVE → FREE_LOOK_RETURNED (within 15 days of issue)
 *   ACTIVE → EXPIRED            (natural end of tenure with no renewal)
 *   LAPSED → REVIVED → ACTIVE   (within 2-year revival window per IRDAI)
 *   REVIVED → ... (re-enters the ACTIVE transition set)
 * ```
 *
 * Legal transitions are enforced by [canTransitionTo]; [PolicyStatus.terminal] marks the
 * absorbing states a policy can never leave.
 */
@Serializable
enum class PolicyStatus {
    DRAFT,
    IN_REVIEW,
    ACTIVE,
    LAPSED,
    REVIVED,
    FREE_LOOK_RETURNED,
    CANCELLED,
    EXPIRED;

    /** Absorbing states — no outgoing transition. */
    val terminal: Boolean
        get() = this == FREE_LOOK_RETURNED || this == CANCELLED || this == EXPIRED

    /** Is [to] a legal next state from `this`? Pure — the single source of FSM truth. */
    fun canTransitionTo(to: PolicyStatus): Boolean = to in ALLOWED.getValue(this)

    companion object {
        private val ALLOWED: Map<PolicyStatus, Set<PolicyStatus>> = mapOf(
            DRAFT to setOf(IN_REVIEW, CANCELLED),
            IN_REVIEW to setOf(ACTIVE, DRAFT, CANCELLED),
            ACTIVE to setOf(LAPSED, CANCELLED, FREE_LOOK_RETURNED, EXPIRED),
            LAPSED to setOf(REVIVED, EXPIRED, CANCELLED),
            REVIVED to setOf(LAPSED, CANCELLED, EXPIRED),
            FREE_LOOK_RETURNED to emptySet(),
            CANCELLED to emptySet(),
            EXPIRED to emptySet(),
        )
    }
}

/**
 * A bound policy in the platform. Created once a quote is accepted and payment realised —
 * a transactional [BaseDataClass] record, NOT admin config (operators never hand-author a
 * Policy; it is produced by the proposal → issuance flow).
 *
 * Notes:
 * - `uin` references the catalog UIN registry and must match a registered product UIN.
 * - `freeLookEndsAt` is `issuedAt + 15 days` per IRDAI standard (see [FreeLookEngine]).
 * - `claimsToDate` / `claimsAmountToDate` are denormalised counters; the source of truth is
 *   the claims collection (see [Claim]). Renewal/NCB read `claimFreeYears` from here.
 * - `gstRate` is snapshotted at issue so historical figures stay reproducible if the rate
 *   ever changes.
 * - `proposerPartyRef` / `holderId` point at the sdk-party `PolicyHolder` `_id`; nominees are
 *   embedded ([Nominee]).
 * - `seed` carries portability carry-forward when the policy was issued via a port-in.
 */
@Serializable
data class Policy(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("uin") val uin: String,
    @SerialName("planId") val planId: String,
    @SerialName("status") val status: PolicyStatus = PolicyStatus.DRAFT,
    @SerialName("issuedAt") val issuedAt: Instant,
    @SerialName("expiresAt") val expiresAt: Instant,
    /** Owning party (`PolicyHolder._id`). */
    @SerialName("holderId") val holderId: String,
    @SerialName("holderMobile") val holderMobile: String,
    @SerialName("proposalId") val proposalId: String,
    @SerialName("currentSumInsured") val currentSumInsured: Long,
    /** Tenure in years of the current term. */
    @SerialName("currentTenure") val currentTenure: Int,
    @SerialName("currentZone") val currentZone: String,
    @SerialName("quotedAt") val quotedAt: Instant,
    @SerialName("lastRenewedAt") val lastRenewedAt: Instant? = null,
    @SerialName("lapsedAt") val lapsedAt: Instant? = null,
    @SerialName("revivedAt") val revivedAt: Instant? = null,
    @SerialName("cancelledAt") val cancelledAt: Instant? = null,
    @SerialName("freeLookEndsAt") val freeLookEndsAt: Instant,
    @SerialName("claimsToDate") val claimsToDate: Int = 0,
    @SerialName("claimsAmountToDate") val claimsAmountToDate: Money = Money.ZERO,
    /** Number of consecutive claim-free policy years used by the NCB engine. */
    @SerialName("claimFreeYears") val claimFreeYears: Int = 0,
    @SerialName("gstRate") val gstRate: Double = 0.18,
    @SerialName("primaryAge") val primaryAge: Int,
    @SerialName("familyType") val familyType: String,
    @SerialName("nominees") val nominees: List<Nominee> = emptyList(),
    /** Set when the policy was issued via a Section-21A port-in. */
    @SerialName("seed") val seed: PolicySeed? = null,
    // ── BaseDataClass envelope ───────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {

    /** True while the policy is in force (ACTIVE or REVIVED). */
    val isInForce: Boolean get() = status == PolicyStatus.ACTIVE || status == PolicyStatus.REVIVED

    /** Delegates to the FSM: is [to] a legal next status for this policy? */
    fun canTransitionTo(to: PolicyStatus): Boolean = status.canTransitionTo(to)
}

/**
 * Minimal seed produced when accepting a portability request — used to construct a [Policy]
 * with carried-over waiting periods. Embedded into [Policy.seed] on a port-in issuance.
 */
@Serializable
data class PolicySeed(
    @SerialName("planId") val planId: String,
    @SerialName("carryForwardWaitingDays") val carryForwardWaitingDays: Int,
    @SerialName("maternityWaitingSatisfied") val maternityWaitingSatisfied: Boolean,
    @SerialName("portedFromInsurer") val portedFromInsurer: String,
    @SerialName("portedFromPolicyNumber") val portedFromPolicyNumber: String,
)
