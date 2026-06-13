package com.rate.sdk.ingestion.repository

import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.MemberLevelRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch

/**
 * Write PORT for the IMMUTABLE actuarial rate rows.
 *
 * Rows are versioned and never mutated in place: an import writes a fresh set under a new
 * version, then [com.rate.sdk.ingestion.repository.RateMetaRepository.activate] flips the
 * active pointer. [bulkUpsert] is idempotent by each row's deterministic composite id, so
 * re-running the same import (same version) over-writes the same documents rather than
 * duplicating them.
 *
 * The Mongo-backed actual (one collection per kind, indexed on the engine's lookup tuple) lives
 * in server-persistence; that same layer also implements
 * [com.rate.core.rating.ports.RateDataProvider] by loading the ACTIVE version's rows into an
 * in-RAM snapshot at boot. This SDK only declares the contract.
 *
 * Returns the number of rows written per call.
 */
interface RateImportRepository {

    /** Write a whole batch transactionally (the common path). Returns total rows written. */
    suspend fun bulkUpsert(batch: RateRowBatch): Int

    suspend fun upsertBaseRates(rows: List<BaseRateRow>): Int
    suspend fun upsertCoverRates(rows: List<CoverRateRow>): Int
    suspend fun upsertMemberLevelRates(rows: List<MemberLevelRow>): Int
    suspend fun upsertDiscountRates(rows: List<DiscountRow>): Int
    suspend fun upsertInstalmentConfig(rows: List<InstalmentRow>): Int
    suspend fun upsertCoverAvailability(rows: List<CoverAvailabilityRow>): Int

    /** Hard-delete every rate row of a (now superseded/retired) [version]. Returns rows removed. */
    suspend fun deleteVersion(version: String): Int

    /** Count rows persisted for [version] (sanity-check after an import). */
    suspend fun countForVersion(version: String): Int

    // ── Read paths (consumed by the rate-provider actual in server-persistence) ──
    suspend fun listBaseRates(version: String): List<BaseRateRow>
    suspend fun listCoverRates(version: String): List<CoverRateRow>
    suspend fun listMemberLevelRates(version: String): List<MemberLevelRow>
    suspend fun listDiscountRates(version: String): List<DiscountRow>
    suspend fun listInstalmentConfig(version: String): List<InstalmentRow>
    suspend fun listCoverAvailability(version: String): List<CoverAvailabilityRow>
}
