package com.rate.domain.lifecycle

import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Lifecycle status of a policy. State machine:
 *
 *   DRAFT          → IN_REVIEW → ACTIVE
 *   ACTIVE         → LAPSED (grace period expired without renewal)
 *   ACTIVE         → CANCELLED (customer-initiated cancellation, post free-look)
 *   ACTIVE         → FREE_LOOK_RETURNED (within 15 days of issue)
 *   ACTIVE         → EXPIRED (natural end of tenure with no renewal)
 *   LAPSED         → REVIVED → ACTIVE (within 2-year revival window per IRDAI)
 *   ACTIVE         → EXPIRED
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
    EXPIRED
}

/**
 * A bound policy in the platform. Created once a quote is accepted and payment realised.
 *
 * Notes:
 * - `uin` references [com.rate.domain.regulatory.UinRegistry] and must match a registered
 *   product UIN.
 * - `freeLookEndsAt` is `issuedAt + 15 days` per IRDAI standard.
 * - `claimsToDate` and `claimsAmountToDate` are denormalised counters; the source of truth
 *   lives in the `claims` table once Phase 5b lands. Renewal uses them for NCB eligibility.
 * - `gstRate` is snapshotted at issue so historical figures remain reproducible if the rate
 *   ever changes.
 */
@Serializable
data class Policy(
    val id: String,
    val uin: String,
    val planId: String,
    val status: PolicyStatus,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val holderId: String,
    val holderMobile: String,
    val proposalId: String,
    val currentSumInsured: Long,
    val currentTenure: Int,           // years
    val currentZone: String,
    val quotedAt: Instant,
    val lastRenewedAt: Instant? = null,
    val lapsedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val freeLookEndsAt: Instant,
    val claimsToDate: Int = 0,
    val claimsAmountToDate: Money = Money.ZERO,
    /** Number of consecutive claim-free policy years used by the NCB engine. */
    val claimFreeYears: Int = 0,
    val gstRate: Double = 0.18,
    val primaryAge: Int,
    val familyType: String,
    val nominees: List<Nominee> = emptyList()
)

/**
 * Minimal seed produced when accepting a portability request — used to construct a Policy
 * with carried-over waiting periods.
 */
@Serializable
data class PolicySeed(
    val planId: String,
    val carryForwardWaitingDays: Int,
    val maternityWaitingSatisfied: Boolean,
    val portedFromInsurer: String,
    val portedFromPolicyNumber: String
)

/** Minimal claim record (stub — Phase 5b will flesh this out). */
@Serializable
data class ClaimRecord(
    val claimId: String,
    val policyId: String,
    val intimatedAt: Instant,
    val amount: Money,
    val cause: String,
    val isSettled: Boolean = false
)
