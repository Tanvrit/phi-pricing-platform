package com.rate.sdk.ingestion.repository

import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.ingestion.model.RateMeta

/**
 * Version-pointer PORT for rate batches.
 *
 * Holds the [RateMeta] rows that describe each imported rate version and tracks WHICH version is
 * active per [ProductLine] (the engine reads exactly one). It also provides SHA dedupe so that
 * re-uploading a byte-identical file is detected before any rows are written.
 *
 * The Mongo-backed actual lives in server-persistence. Pure-KMP contract only here.
 */
interface RateMetaRepository {

    /** Insert/replace a [RateMeta] (keyed by its id). */
    suspend fun upsert(meta: RateMeta): RateMeta

    suspend fun get(id: String): RateMeta?

    /** The currently-active version for [productLine], or null if none has been activated yet. */
    suspend fun getActive(productLine: ProductLine = ProductLine.RETAIL): RateMeta?

    /** Lookup by stable [version] string. */
    suspend fun getByVersion(version: String, productLine: ProductLine = ProductLine.RETAIL): RateMeta?

    /**
     * SHA dedupe lookup: an existing [RateMeta] whose [RateMeta.sourceFileSha256] == [sha], or null.
     * The handler uses this to short-circuit an import of an already-ingested file.
     */
    suspend fun findBySha(sha: String, productLine: ProductLine = ProductLine.RETAIL): RateMeta?

    /**
     * Atomically make [version] the active one for its product line, retiring (active=false) any
     * previously-active version. Returns the now-active [RateMeta], or null if the version is unknown.
     */
    suspend fun activate(version: String, productLine: ProductLine = ProductLine.RETAIL): RateMeta?

    /** All versions for [productLine], newest first (for an admin version-history view). */
    suspend fun listVersions(productLine: ProductLine = ProductLine.RETAIL): List<RateMeta>
}
