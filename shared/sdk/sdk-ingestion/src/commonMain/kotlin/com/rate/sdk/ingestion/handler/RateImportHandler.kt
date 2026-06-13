package com.rate.sdk.ingestion.handler

import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.ingestion.event.IngestionEvent
import com.rate.sdk.ingestion.event.IngestionEventSink
import com.rate.sdk.ingestion.model.ImportSummary
import com.rate.sdk.ingestion.model.RateMeta
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.sdk.ingestion.repository.RateImportRepository
import com.rate.sdk.ingestion.repository.RateMetaRepository

/**
 * Orchestrates the IMMUTABLE-rate import flow over the two PORTs ([RateImportRepository] for the
 * rows, [RateMetaRepository] for the version pointer + SHA dedupe) and emits [IngestionEvent]s.
 *
 * Relocated from the monolith's `server/import` pipeline, but transport-agnostic and pure-KMP: the
 * caller (server) supplies the parsed [RateRowBatch] (produced by the JVM-only POI importer in
 * server-persistence, or by tests) and the file's content [sha] + name. This handler:
 *  1. dedupes by SHA — a re-upload of the identical file is a no-op ([ImportSummary.deduped]);
 *  2. writes the rows (idempotent by deterministic row id);
 *  3. records/updates the [RateMeta];
 *  4. optionally activates the new version (flipping the engine's read pointer).
 *
 * Because the rows return Double (engine parity), no Money conversion happens here.
 */
class RateImportHandler(
    private val rows: RateImportRepository,
    private val meta: RateMetaRepository,
    private val sink: IngestionEventSink = IngestionEventSink.NOOP,
) {

    /**
     * Import [batch], dedupe-protected by [sha]. If [activate], the new version becomes the active
     * one on success. Returns an [ImportSummary] (deduped no-op, success with counts, or failure).
     */
    suspend fun importBatch(
        batch: RateRowBatch,
        sha: String,
        sourceFileName: String = "",
        productLine: ProductLine = ProductLine.RETAIL,
        activate: Boolean = true,
        actor: String? = null,
        note: String = "",
    ): ImportSummary {
        if (batch.version.isBlank()) {
            return ImportSummary.failed(productLine, "Rate batch version must not be blank")
        }
        // 1. SHA dedupe.
        if (sha.isNotBlank()) {
            val existing = meta.findBySha(sha, productLine)
            if (existing != null) {
                sink.emit(
                    IngestionEvent.RateImportDeduped(
                        version = existing.version,
                        productLine = productLine,
                        sourceFileSha256 = sha,
                        actor = actor,
                        at = Now.instant(),
                    ),
                )
                return ImportSummary.dedupedNoOp(productLine, existing.version, sha, sourceFileName)
            }
        }

        // 2. Write rows (idempotent by deterministic id).
        val written = rows.bulkUpsert(batch)

        // 3. Record the version pointer.
        val existingMeta = meta.getByVersion(batch.version, productLine)
        val record = (existingMeta ?: RateMeta(version = batch.version, productLine = productLine)).copy(
            version = batch.version,
            productLine = productLine,
            sourceFileSha256 = sha,
            sourceFileName = sourceFileName,
            rowCounts = batch.counts(),
            note = note,
            importedBy = actor,
            importedAt = Now.instant(),
            updatedAt = Now.instant(),
            v = (existingMeta?.v ?: 0L) + 1,
        )
        meta.upsert(record)

        sink.emit(
            IngestionEvent.RateImported(
                version = batch.version,
                productLine = productLine,
                sourceFileSha256 = sha,
                sourceFileName = sourceFileName,
                rowsWritten = written,
                actor = actor,
                at = Now.instant(),
            ),
        )

        // 4. Activate if requested.
        if (activate) activateVersion(batch.version, productLine, actor)

        return ImportSummary(
            ok = true,
            productLine = productLine,
            version = batch.version,
            sourceFileName = sourceFileName,
            sourceFileSha256 = sha,
            counts = batch.counts(),
        )
    }

    /**
     * Promote [version] to active, retiring the previously-active one and emitting
     * [IngestionEvent.RateVersionActivated]. Returns the now-active [RateMeta] or null if unknown.
     */
    suspend fun activateVersion(
        version: String,
        productLine: ProductLine = ProductLine.RETAIL,
        actor: String? = null,
    ): RateMeta? {
        val previous = meta.getActive(productLine)
        val activated = meta.activate(version, productLine) ?: return null
        sink.emit(
            IngestionEvent.RateVersionActivated(
                version = version,
                productLine = productLine,
                previousVersion = previous?.version?.takeIf { it != version },
                actor = actor,
                at = Now.instant(),
            ),
        )
        return activated
    }

    /** Convenience: hash raw file text and import in one call. */
    suspend fun importText(
        batch: RateRowBatch,
        fileText: String,
        sourceFileName: String = "",
        productLine: ProductLine = ProductLine.RETAIL,
        activate: Boolean = true,
        actor: String? = null,
    ): ImportSummary = importBatch(
        batch = batch,
        sha = SourceHash.ofText(fileText),
        sourceFileName = sourceFileName,
        productLine = productLine,
        activate = activate,
        actor = actor,
    )

    /** The version the engine currently reads, for diagnostics. */
    suspend fun activeVersion(productLine: ProductLine = ProductLine.RETAIL): RateMeta? =
        meta.getActive(productLine)
}
