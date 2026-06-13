package com.rate.sdk.ingestion.event

import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain events emitted by the ingestion handlers. `@Serializable` so the server can fan them into
 * the sdk-audit log / a message bus, but the SDK stays transport-agnostic: handlers push to an
 * [IngestionEventSink] PORT whose [IngestionEventSink.NOOP] default means tests and offline usage
 * carry zero wiring.
 *
 * Mirrors the sdk-party event pattern (sealed @Serializable hierarchy + fun-interface sink).
 */
@Serializable
sealed class IngestionEvent {
    abstract val at: Instant

    /** A rate batch was imported (rows written, not yet necessarily active). */
    @Serializable
    @SerialName("RateImported")
    data class RateImported(
        @SerialName("version") val version: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("sourceFileSha256") val sourceFileSha256: String,
        @SerialName("sourceFileName") val sourceFileName: String,
        @SerialName("rowsWritten") val rowsWritten: Int,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : IngestionEvent()

    /** An import was skipped because the same file (by SHA) was already ingested. */
    @Serializable
    @SerialName("RateImportDeduped")
    data class RateImportDeduped(
        @SerialName("version") val version: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("sourceFileSha256") val sourceFileSha256: String,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : IngestionEvent()

    /** A rate version was promoted to active (the engine snapshot should reload). */
    @Serializable
    @SerialName("RateVersionActivated")
    data class RateVersionActivated(
        @SerialName("version") val version: String,
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("previousVersion") val previousVersion: String?,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : IngestionEvent()

    /** Catalog entities were seeded/re-imported from CSV sources. */
    @Serializable
    @SerialName("CatalogSeeded")
    data class CatalogSeeded(
        @SerialName("productLine") val productLine: ProductLine,
        @SerialName("totalWritten") val totalWritten: Int,
        @SerialName("counts") val counts: Map<String, Int>,
        @SerialName("actor") val actor: String?,
        @SerialName("at") override val at: Instant,
    ) : IngestionEvent()
}

/**
 * Sink PORT for [IngestionEvent]s. The server binds a real implementation (audit/bus); the
 * [NOOP] default lets handlers be constructed without a sink (tests / offline).
 */
fun interface IngestionEventSink {
    suspend fun emit(event: IngestionEvent)

    companion object {
        val NOOP: IngestionEventSink = IngestionEventSink { /* discard */ }
    }
}
