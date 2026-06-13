package com.rate.sdk.quoting.event

import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain events emitted by [com.rate.sdk.quoting.handler.QuoteHandler]. `@Serializable` so the
 * server can fan them into the sdk-audit log / a message bus, but the SDK stays
 * transport-agnostic: the handler pushes to a [QuoteEventSink] PORT whose [QuoteEventSink.NOOP]
 * default means tests and read-only client usage carry zero wiring.
 */
@Serializable
sealed class QuoteEvent {
    abstract val at: Instant

    @Serializable
    @SerialName("QuoteCalculated")
    data class QuoteCalculated(
        @SerialName("quoteId") val quoteId: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("planId") val planId: String,
        @SerialName("totalIncludingGst") val totalIncludingGst: Double,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : QuoteEvent()

    @Serializable
    @SerialName("QuoteShared")
    data class QuoteShared(
        @SerialName("quoteId") val quoteId: String,
        @SerialName("channel") val channel: String,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : QuoteEvent()

    @Serializable
    @SerialName("QuoteConverted")
    data class QuoteConverted(
        @SerialName("quoteId") val quoteId: String,
        @SerialName("proposalRef") val proposalRef: String,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : QuoteEvent()
}

/**
 * Sink PORT for [QuoteEvent]s. The server binds a real implementation (audit/bus); the [NOOP]
 * default lets the handler be constructed without a sink.
 */
fun interface QuoteEventSink {
    suspend fun emit(event: QuoteEvent)

    companion object {
        val NOOP: QuoteEventSink = QuoteEventSink { /* discard */ }
    }
}
