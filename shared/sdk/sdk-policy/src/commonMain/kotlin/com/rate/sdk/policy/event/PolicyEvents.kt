package com.rate.sdk.policy.event

import com.rate.sdk.policy.model.PolicyStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain events emitted by the policy-lifecycle handlers. `@Serializable` so the server can
 * fan them into the sdk-audit log / a message bus, but the SDK stays transport-agnostic:
 * handlers push to a [PolicyEventSink] PORT, and the no-op default means tests and read-only
 * client usage carry zero wiring.
 *
 * Polymorphic via `AppJson`'s `_class` discriminator (`@SerialName` on each subclass).
 */
@Serializable
sealed class PolicyEvent {
    abstract val policyId: String
    abstract val at: Instant

    @Serializable
    @SerialName("PolicyIssued")
    data class PolicyIssued(
        @SerialName("policyId") override val policyId: String,
        @SerialName("uin") val uin: String,
        @SerialName("holderId") val holderId: String,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("PolicyStatusChanged")
    data class PolicyStatusChanged(
        @SerialName("policyId") override val policyId: String,
        @SerialName("from") val from: PolicyStatus,
        @SerialName("to") val to: PolicyStatus,
        @SerialName("reason") val reason: String? = null,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("RenewalQuoted")
    data class RenewalQuoted(
        @SerialName("policyId") override val policyId: String,
        @SerialName("projectedPremium") val projectedPremium: Double,
        @SerialName("ncbPercent") val ncbPercent: Double,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("PolicyRenewed")
    data class PolicyRenewed(
        @SerialName("policyId") override val policyId: String,
        @SerialName("newExpiresAt") val newExpiresAt: Instant,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("EndorsementApplied")
    data class EndorsementApplied(
        @SerialName("policyId") override val policyId: String,
        @SerialName("endorsementType") val endorsementType: String,
        @SerialName("netAmountPaise") val netAmountPaise: Long,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("FreeLookReturned")
    data class FreeLookReturned(
        @SerialName("policyId") override val policyId: String,
        @SerialName("refundPaise") val refundPaise: Long,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("PortabilityAccepted")
    data class PortabilityAccepted(
        @SerialName("policyId") override val policyId: String,
        @SerialName("fromInsurer") val fromInsurer: String,
        @SerialName("carryForwardWaitingDays") val carryForwardWaitingDays: Int,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()

    @Serializable
    @SerialName("ClaimIntimated")
    data class ClaimIntimated(
        @SerialName("policyId") override val policyId: String,
        @SerialName("claimId") val claimId: String,
        @SerialName("claimedAmountPaise") val claimedAmountPaise: Long,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PolicyEvent()
}

/**
 * Sink PORT for [PolicyEvent]s. The server binds a real implementation (audit/bus); the
 * [NOOP] default lets handlers be constructed without a sink.
 */
fun interface PolicyEventSink {
    suspend fun emit(event: PolicyEvent)

    companion object {
        val NOOP: PolicyEventSink = PolicyEventSink { /* discard */ }
    }
}
