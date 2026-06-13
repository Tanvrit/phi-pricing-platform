package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.party.model.group.Census
import com.rate.sdk.party.model.group.CensusAggregation
import com.rate.sdk.party.repository.CensusRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [CensusRepository] — transactional GROUP census documents keyed by the
 * owning employer (`employerPartyRef`).
 *
 * [aggregate] loads the stored census and rolls it up via the pure
 * [CensusAggregation.from] (grade × age-band) — the rollup math lives in the SDK so it is
 * verified once and reused; the Mongo actual just supplies the input rather than re-deriving
 * the buckets in an aggregation pipeline (correctness over a marginal data-transfer win;
 * worth pushing into a `$facet` pipeline only when censuses get truly large).
 */
class CensusRepositoryImpl(db: MongoDatabase) :
    MongoRepository<Census>(db, CollectionNames.CENSUSES, Census::class.java),
    CensusRepository {

    override suspend fun get(id: String): Census? = findById(id)

    /** Census documents for an employer, newest first (forced), paged. */
    override suspend fun listByEmployer(employerPartyRef: String, req: PageRequest): Page<Census> {
        val filter = Filters.and(Filters.eq("employerPartyRef", employerPartyRef), notDeleted)
        val total = collection.countDocuments(filter)
        val items = collection.find(filter)
            .sort(Sorts.descending("updatedAt"))
            .skip(req.page.coerceAtLeast(0) * req.size.coerceAtLeast(1))
            .limit(req.size.coerceAtLeast(1))
            .toList()
        return Page(items, total, req.page, req.size)
    }

    override suspend fun latestForEmployer(employerPartyRef: String): Census? =
        collection.find(Filters.and(Filters.eq("employerPartyRef", employerPartyRef), notDeleted))
            .sort(Sorts.descending("updatedAt"))
            .limit(1)
            .firstOrNull()

    override suspend fun upsert(census: Census, actor: String?): Census {
        upsertEntity(census)
        return findByIdIncludingDeleted(census.id) ?: census
    }

    override suspend fun aggregate(censusId: String): CensusAggregation? {
        val census = findById(censusId) ?: return null
        return CensusAggregation.from(
            employerPartyRef = census.employerPartyRef,
            censusId = census.id,
            members = census.members,
        )
    }

    override suspend fun softDelete(id: String, actor: String?): Boolean = softDelete(id)

    override suspend fun bulkUpsert(censuses: List<Census>, actor: String?): Int {
        var written = 0
        for (c in censuses) {
            upsertEntity(c)
            written++
        }
        return written
    }
}
