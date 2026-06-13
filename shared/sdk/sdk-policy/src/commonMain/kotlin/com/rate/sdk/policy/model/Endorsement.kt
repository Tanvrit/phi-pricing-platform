package com.rate.sdk.policy.model

import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Member
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mid-term change to an in-force policy. Sealed across the supported variants; each carries a
 * `proRataChargeRationale` — the human-readable string the customer sees explaining how any
 * pro-rata charge/refund was computed. The actual rupee figure is computed by
 * [com.rate.sdk.policy.handler.EndorsementEngine.price].
 *
 * Polymorphic via `AppJson`'s `_class` discriminator (`@SerialName` on each subclass).
 */
@Serializable
sealed class Endorsement {

    abstract val policyId: String
    abstract val requestedAt: Instant
    abstract val proRataChargeRationale: String

    /** Add a new dependent to an existing floater policy. */
    @Serializable
    @SerialName("AddMember")
    data class AddMember(
        @SerialName("policyId") override val policyId: String,
        @SerialName("requestedAt") override val requestedAt: Instant,
        @SerialName("memberToAdd") val memberToAdd: Member,
        @SerialName("proRataChargeRationale") override val proRataChargeRationale: String =
            "Pro-rata premium = base member premium × (days remaining / 365). " +
                "Waiting periods apply afresh to the new member from the endorsement date.",
    ) : Endorsement()

    /** Increase the sum insured. New SI must be ≥ current; the delta drives the pro-rata charge. */
    @Serializable
    @SerialName("IncreaseSumInsured")
    data class IncreaseSumInsured(
        @SerialName("policyId") override val policyId: String,
        @SerialName("requestedAt") override val requestedAt: Instant,
        @SerialName("toAmount") val toAmount: Money,
        @SerialName("proRataChargeRationale") override val proRataChargeRationale: String =
            "Pro-rata charge = (premium at new SI − premium at current SI) × (days remaining / 365). " +
                "Enhanced limits subject to fresh underwriting review.",
    ) : Endorsement()

    /** Address-only change. Zone may change → premium delta if the zone tier shifts. */
    @Serializable
    @SerialName("AddressChange")
    data class AddressChange(
        @SerialName("policyId") override val policyId: String,
        @SerialName("requestedAt") override val requestedAt: Instant,
        @SerialName("newAddress") val newAddress: String,
        @SerialName("newPincode") val newPincode: String,
        @SerialName("newZone") val newZone: String? = null,
        @SerialName("proRataChargeRationale") override val proRataChargeRationale: String =
            "If the new pincode shifts the policy zone, pro-rata = (new zone premium − " +
                "old zone premium) × (days remaining / 365). Same-zone change → no charge.",
    ) : Endorsement()

    /** Update of nominee details. No premium impact. */
    @Serializable
    @SerialName("NomineeChange")
    data class NomineeChange(
        @SerialName("policyId") override val policyId: String,
        @SerialName("requestedAt") override val requestedAt: Instant,
        @SerialName("newNominees") val newNominees: List<Nominee>,
        @SerialName("proRataChargeRationale") override val proRataChargeRationale: String =
            "Nominee change has no premium impact.",
    ) : Endorsement()

    /** Remove a member. Refund = pro-rata premium for that member for the unexpired tenure. */
    @Serializable
    @SerialName("RemoveMember")
    data class RemoveMember(
        @SerialName("policyId") override val policyId: String,
        @SerialName("requestedAt") override val requestedAt: Instant,
        @SerialName("memberId") val memberId: Int,
        @SerialName("proRataChargeRationale") override val proRataChargeRationale: String =
            "Refund = pro-rata premium attributed to the removed member × " +
                "(days remaining / 365), minus admin charges.",
    ) : Endorsement()
}

/**
 * Priced result of an [Endorsement] (output of
 * [com.rate.sdk.policy.handler.EndorsementEngine.price]). A positive [netAmount] is an
 * additional charge to the customer, a negative one a refund.
 */
@Serializable
data class EndorsementResult(
    @SerialName("policyId") val policyId: String,
    @SerialName("endorsementType") val endorsementType: String,
    @SerialName("daysRemaining") val daysRemaining: Int,
    @SerialName("tenureDays") val tenureDays: Int,
    /** Full-tenure premium delta this change implies (before pro-rating). */
    @SerialName("fullTermDelta") val fullTermDelta: Money,
    /** Pro-rated charge (positive) or refund (negative) for the unexpired tenure. */
    @SerialName("netAmount") val netAmount: Money,
    @SerialName("isCharge") val isCharge: Boolean,
    @SerialName("rationale") val rationale: String,
    @SerialName("isValid") val isValid: Boolean = true,
    @SerialName("validationErrors") val validationErrors: List<String> = emptyList(),
)
