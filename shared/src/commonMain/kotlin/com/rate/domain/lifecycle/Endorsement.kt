package com.rate.domain.lifecycle

import com.rate.domain.model.Member
import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Mid-term changes to an in-force policy. Each variant records a `proRataChargeRationale`
 * — a human-readable string the customer sees on the endorsement confirmation explaining
 * how any pro-rata charge or refund was computed. The actual rupee figure is computed by
 * the (later, Phase 5b) `EndorsementService` on the server side.
 *
 * Persistence is out of scope for this phase — endorsements are produced and surfaced via
 * the routes layer but written to a stub repository.
 */
@Serializable
sealed class Endorsement {

    abstract val policyId: String
    abstract val requestedAt: Instant
    abstract val proRataChargeRationale: String

    /** Add a new dependent to an existing floater policy. */
    @Serializable
    data class AddMember(
        override val policyId: String,
        override val requestedAt: Instant,
        val memberToAdd: Member,
        override val proRataChargeRationale: String =
            "Pro-rata premium = base member premium × (days remaining / 365). " +
                "Waiting periods apply afresh to the new member from the endorsement date."
    ) : Endorsement()

    /** Increase the sum insured. New SI must be ≥ current; the delta drives the pro-rata charge. */
    @Serializable
    data class IncreaseSumInsured(
        override val policyId: String,
        override val requestedAt: Instant,
        val toAmount: Money,
        override val proRataChargeRationale: String =
            "Pro-rata charge = (premium at new SI − premium at current SI) × (days remaining / 365). " +
                "Enhanced limits subject to fresh underwriting review."
    ) : Endorsement()

    /** Address-only change. Zone may change → premium delta if zone tier shifts. */
    @Serializable
    data class AddressChange(
        override val policyId: String,
        override val requestedAt: Instant,
        val newAddress: String,
        val newPincode: String,
        override val proRataChargeRationale: String =
            "If the new pincode shifts the policy zone, pro-rata = (new zone premium − " +
                "old zone premium) × (days remaining / 365). Same-zone change → no charge."
    ) : Endorsement()

    /** Update of nominee details. No premium impact. */
    @Serializable
    data class NomineeChange(
        override val policyId: String,
        override val requestedAt: Instant,
        val newNominees: List<Nominee>,
        override val proRataChargeRationale: String = "Nominee change has no premium impact."
    ) : Endorsement()

    /** Remove a member. Refund = pro-rata premium for that member for the unexpired tenure. */
    @Serializable
    data class RemoveMember(
        override val policyId: String,
        override val requestedAt: Instant,
        val memberId: Int,
        override val proRataChargeRationale: String =
            "Refund = pro-rata premium attributed to the removed member × " +
                "(days remaining / 365), minus admin charges."
    ) : Endorsement()
}
