package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.policy.model.Policy
import com.rate.sdk.policy.model.PolicyStatus
import com.rate.sdk.policy.repository.PolicyRepository
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant

/**
 * Mongo actual for [PolicyRepository] — transactional policy documents produced by the
 * proposal → issuance flow (not admin config). Provides the servicing lookups (by holder, by
 * mobile), the operator paged list, optimistic update, soft-delete/restore, bulk seed, and the
 * renewal-due range query that drives the renewal worklist + reminder batch.
 */
class PolicyRepositoryImpl(db: MongoDatabase) :
    MongoRepository<Policy>(db, CollectionNames.POLICIES, Policy::class.java),
    PolicyRepository {

    override suspend fun get(id: String): Policy? = findById(id)

    override suspend fun getByHolder(holderId: String): List<Policy> =
        collection.find(Filters.and(Filters.eq("holderId", holderId), notDeleted))
            .sort(Sorts.descending("issuedAt"))
            .toList()

    override suspend fun getByMobile(mobile: String): List<Policy> =
        collection.find(Filters.and(Filters.eq("holderMobile", mobile), notDeleted))
            .sort(Sorts.descending("issuedAt"))
            .toList()

    override suspend fun list(req: PageRequest): Page<Policy> = paged(req)

    override suspend fun create(policy: Policy, actor: String?): Policy = insert(policy)

    override suspend fun update(policy: Policy, expectedV: Long, actor: String?): Policy =
        replaceWithVersionCheck(policy, expectedV, "Policy")

    override suspend fun softDelete(id: String, actor: String?): Boolean = softDelete(id)

    override suspend fun restore(id: String, actor: String?): Boolean = restore(id)

    override suspend fun bulkUpsert(policies: List<Policy>, actor: String?): Int {
        var written = 0
        for (p in policies) {
            upsertEntity(p)
            written++
        }
        return written
    }

    override suspend fun findRenewalsDue(
        from: Instant,
        to: Instant,
        statuses: Set<PolicyStatus>,
        req: PageRequest,
    ): Page<Policy> {
        // NB: Instant fields are stored as ISO-8601 strings by the kotlinx compiled serializer
        // (see module README "Money/Instant BSON representation"). ISO-8601 UTC strings compare
        // chronologically under lexicographic ordering, so string $gte/$lte is correct here.
        val filter = Filters.and(
            notDeleted,
            Filters.`in`("status", statuses.map { it.name }),
            Filters.gte("expiresAt", from.toString()),
            Filters.lte("expiresAt", to.toString()),
        )
        val total = collection.countDocuments(filter)
        val items = collection.find(filter)
            .sort(Sorts.ascending("expiresAt"))
            .skip(req.page.coerceAtLeast(0) * req.size.coerceAtLeast(1))
            .limit(req.size.coerceAtLeast(1))
            .toList()
        return Page(items, total, req.page, req.size)
    }
}
