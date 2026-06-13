package com.rate.sdk.policy.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Settlement status of a claim. Cashless and reimbursement both flow through the same FSM:
 *
 * ```
 *   INTIMATED → REGISTERED → UNDER_REVIEW → APPROVED  → SETTLED
 *                                         → REPUDIATED (declined)
 *   any non-terminal              → WITHDRAWN (customer pulls the claim)
 * ```
 */
@Serializable
enum class ClaimStatus {
    INTIMATED,
    REGISTERED,
    UNDER_REVIEW,
    APPROVED,
    SETTLED,
    REPUDIATED,
    WITHDRAWN;

    val terminal: Boolean get() = this == SETTLED || this == REPUDIATED || this == WITHDRAWN

    /** A claim that counts AGAINST the NCB streak (any non-withdrawn intimation on the policy). */
    val countsAgainstNcb: Boolean get() = this != WITHDRAWN
}

/** Cashless (network hospital pre-auth) vs reimbursement (pay-then-claim). */
@Serializable
enum class ClaimType { CASHLESS, REIMBURSEMENT }

/**
 * A claim raised against an in-force [Policy]. Transactional [BaseDataClass] record. The
 * denormalised counters on [Policy] (`claimsToDate`, `claimsAmountToDate`, `claimFreeYears`)
 * are maintained from this collection; this is the source of truth.
 */
@Serializable
data class Claim(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("policyId") val policyId: String,
    @SerialName("claimNumber") val claimNumber: String,
    @SerialName("type") val type: ClaimType = ClaimType.REIMBURSEMENT,
    @SerialName("status") val status: ClaimStatus = ClaimStatus.INTIMATED,
    @SerialName("intimatedAt") val intimatedAt: Instant,
    /** Policy year (1-based) in which the loss occurred — drives NCB reset. */
    @SerialName("policyYear") val policyYear: Int = 1,
    @SerialName("claimedAmount") val claimedAmount: Money,
    @SerialName("approvedAmount") val approvedAmount: Money = Money.ZERO,
    @SerialName("settledAmount") val settledAmount: Money = Money.ZERO,
    @SerialName("cause") val cause: String = "",
    @SerialName("hospitalName") val hospitalName: String? = null,
    @SerialName("settledAt") val settledAt: Instant? = null,
    @SerialName("repudiationReason") val repudiationReason: String? = null,
    // ── BaseDataClass envelope ───────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass {
    val isSettled: Boolean get() = status == ClaimStatus.SETTLED
    val isOpen: Boolean get() = !status.terminal
}

/**
 * Lightweight claim summary carried over from a previous insurer on a portability request
 * (see [com.rate.sdk.policy.handler.PortabilityEngine]). Not persisted on its own — it is an
 * input record describing the customer's history at the source insurer.
 */
@Serializable
data class ClaimRecord(
    @SerialName("claimId") val claimId: String,
    @SerialName("policyId") val policyId: String,
    @SerialName("intimatedAt") val intimatedAt: Instant,
    @SerialName("amount") val amount: Money,
    @SerialName("cause") val cause: String,
    @SerialName("isSettled") val isSettled: Boolean = false,
)
