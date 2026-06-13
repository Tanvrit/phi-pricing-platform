package com.rate.sdk.party.event

import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain events emitted by the party handlers. They are `@Serializable` so the server
 * can fan them into the sdk-audit log / a message bus, but the SDK itself stays
 * transport-agnostic: handlers push to a [PartyEventSink] PORT, and the no-op default
 * means tests and read-only client usage carry zero wiring.
 */
@Serializable
sealed class PartyEvent {
    abstract val at: Instant

    @Serializable
    @SerialName("PartyCreated")
    data class PartyCreated(
        @SerialName("partyId") val partyId: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PartyEvent()

    @Serializable
    @SerialName("PartyUpdated")
    data class PartyUpdated(
        @SerialName("partyId") val partyId: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PartyEvent()

    @Serializable
    @SerialName("CensusIngested")
    data class CensusIngested(
        @SerialName("censusId") val censusId: String,
        @SerialName("employerPartyRef") val employerPartyRef: String,
        @SerialName("lives") val lives: Int,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : PartyEvent()
}

/**
 * Sink PORT for [PartyEvent]s. The server binds a real implementation (audit/bus); the
 * [NOOP] default lets handlers be constructed without a sink.
 */
fun interface PartyEventSink {
    suspend fun emit(event: PartyEvent)

    companion object {
        val NOOP: PartyEventSink = PartyEventSink { /* discard */ }
    }
}
