package com.rate.sdk.party.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusAggregation

/**
 * Persistence PORT for GROUP [Census] documents. Census rows are transactional party
 * data (not admin config), so this is a focused contract keyed by the owning
 * [com.rate.sdk.party.model.GroupEmployer] (`employerPartyRef`):
 *  - paged listing per employer,
 *  - the latest census (the one rating uses),
 *  - server-side grade/age-band [aggregate]ion (so a 50k-life census is rolled up in
 *    the database rather than shipped whole to the client),
 *  - idempotent [bulkUpsert] for chunked ingestion of large uploads.
 *
 * The MongoDB-backed actual lives in `server-persistence`.
 */
interface CensusRepository {
    suspend fun get(id: String): Census?

    /** Census documents for an employer, newest first, paged. */
    suspend fun listByEmployer(
        employerPartyRef: String,
        req: PageRequest = PageRequest(),
    ): Page<Census>

    /** The most recently updated, non-deleted census for an employer (the rating census). */
    suspend fun latestForEmployer(employerPartyRef: String): Census?

    suspend fun upsert(census: Census, actor: String?): Census

    /**
     * Grade × age-band roll-up of a stored census, computed repository-side. The default
     * surfaces the contract; Mongo actuals may push this into an aggregation pipeline.
     */
    suspend fun aggregate(censusId: String): CensusAggregation?

    suspend fun softDelete(id: String, actor: String?): Boolean

    /** Idempotent bulk insert/update (chunked census ingestion). Returns count written. */
    suspend fun bulkUpsert(censuses: List<Census>, actor: String?): Int
}
