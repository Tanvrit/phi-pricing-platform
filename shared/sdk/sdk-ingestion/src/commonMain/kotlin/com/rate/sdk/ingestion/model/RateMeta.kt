package com.rate.sdk.ingestion.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.ProductLine
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Version pointer for an immutable batch of rate rows.
 *
 * The rate engine reads ONE active version at a time (server-persistence loads the active
 * version into an in-RAM snapshot at boot). Each import writes a fresh, immutable set of rate
 * rows tagged with this [version] and inserts/updates exactly one [RateMeta] row; promoting a
 * new version flips [active]. Because rate rows are NEVER mutated in place, audit and
 * reproducibility are trivial — a [QuoteResult] stamps [version] so the exact tables that
 * produced it can always be re-loaded.
 *
 * [sourceFileSha256] is the lowercase-hex SHA-256 of the uploaded file's content (see
 * [com.rate.sdk.ingestion.handler.SourceHash]). It enables SHA dedupe: re-uploading the
 * byte-identical file is a no-op (the [RateMetaRepository] short-circuits via
 * [com.rate.sdk.ingestion.repository.RateMetaRepository.findBySha]).
 *
 * This is transactional metadata (NOT admin-CRUD), so it implements [BaseDataClass] rather
 * than ConfigEntity — there is no draft/publish lifecycle; activation is the [active] flag.
 */
@Serializable
data class RateMeta(
    @SerialName("_id") override val id: String = newId(),
    /**
     * Human/stable version identifier for this rate batch (e.g. "v13.0", "2026-05-12", or a
     * generated ULID). Every rate row carries the same value in its `version` field, and it is
     * stamped into each QuoteResult.rateTableVersion for audit.
     */
    @SerialName("version") val version: String,
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** Lowercase-hex SHA-256 of the originating file's bytes (for re-upload dedupe). */
    @SerialName("sourceFileSha256") val sourceFileSha256: String = "",
    /** Original file name of the upload (display/audit only). */
    @SerialName("sourceFileName") val sourceFileName: String = "",
    /** Whether THIS version is the one the engine reads. Exactly one active per productLine. */
    @SerialName("active") val active: Boolean = false,
    /** Denormalised row counts per kind, for dashboards (filled by the import summary). */
    @SerialName("rowCounts") val rowCounts: Map<String, Int> = emptyMap(),
    /** Free-text note on this import (e.g. operator comment, ingest tool version). */
    @SerialName("note") val note: String = "",
    @SerialName("importedBy") val importedBy: String? = null,
    @SerialName("importedAt") val importedAt: Instant = Now.instant(),
    // ── BaseDataClass envelope ─────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
) : BaseDataClass
