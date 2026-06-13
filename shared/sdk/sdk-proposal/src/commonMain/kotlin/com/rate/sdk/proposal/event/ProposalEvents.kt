package com.rate.sdk.proposal.event

import com.rate.sdk.proposal.model.ProposalStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain events emitted by the buy-online handlers. They are `@Serializable` so the server can
 * fan them into the sdk-audit log / a message bus (the monolith audit-recorded `proposal.created`
 * inline in the route — this lifts it to a transport-agnostic event), but the SDK itself stays
 * transport-agnostic: handlers push to a [ProposalEventSink] PORT whose [ProposalEventSink.NOOP]
 * default means tests and read-only client usage carry zero wiring.
 */
@Serializable
sealed class ProposalEvent {
    abstract val at: Instant

    /** An OTP was issued for a mobile/purpose (audit/anti-abuse signal — never carries the code). */
    @Serializable
    @SerialName("OtpSent")
    data class OtpSent(
        @SerialName("mobile") val mobile: String,
        @SerialName("purpose") val purpose: String,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    /** An OTP verification succeeded (login or KYC). */
    @Serializable
    @SerialName("OtpVerified")
    data class OtpVerified(
        @SerialName("mobile") val mobile: String,
        @SerialName("purpose") val purpose: String,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    @Serializable
    @SerialName("EligibilityChecked")
    data class EligibilityChecked(
        @SerialName("mobile") val mobile: String,
        @SerialName("coveredCount") val coveredCount: Int,
        @SerialName("uncoveredCount") val uncoveredCount: Int,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    @Serializable
    @SerialName("PremiumQuoted")
    data class PremiumQuoted(
        @SerialName("tier") val tier: String,
        @SerialName("planRef") val planRef: String,
        @SerialName("sumInsured") val sumInsured: Long,
        @SerialName("totalIncludingGst") val totalIncludingGst: Double,
        @SerialName("quoteRef") val quoteRef: String?,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    @Serializable
    @SerialName("KycSubmitted")
    data class KycSubmitted(
        @SerialName("mobile") val mobile: String,
        @SerialName("method") val method: String,
        @SerialName("status") val status: String,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    @Serializable
    @SerialName("ProposalCreated")
    data class ProposalCreated(
        @SerialName("proposalNumber") val proposalNumber: String,
        @SerialName("planTier") val planTier: String,
        @SerialName("planRef") val planRef: String,
        @SerialName("sumInsured") val sumInsured: Long,
        @SerialName("totalIncludingGst") val totalIncludingGst: Double,
        @SerialName("quoteRef") val quoteRef: String?,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()

    @Serializable
    @SerialName("ProposalStatusChanged")
    data class ProposalStatusChanged(
        @SerialName("proposalNumber") val proposalNumber: String,
        @SerialName("from") val from: ProposalStatus,
        @SerialName("to") val to: ProposalStatus,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : ProposalEvent()
}

/**
 * Sink PORT for [ProposalEvent]s. The server binds a real implementation (audit/bus); the
 * [NOOP] default lets handlers be constructed without a sink.
 */
fun interface ProposalEventSink {
    suspend fun emit(event: ProposalEvent)

    companion object {
        val NOOP: ProposalEventSink = ProposalEventSink { /* discard */ }
    }
}
