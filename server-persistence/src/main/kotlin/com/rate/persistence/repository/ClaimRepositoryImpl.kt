package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.policy.model.Claim
import com.rate.sdk.policy.model.ClaimStatus
import kotlinx.coroutines.flow.toList
import com.rate.sdk.policy.repository.ClaimRepository

/**
 * Mongo actual for [ClaimRepository] — transactional claim documents keyed by their owning
 * [Claim.policyId]. Backs the claims worklist plus the NCB streak count
 * ([countAgainstNcb]) and the open-claims check used by NCB / portability.
 *
 * The FSM transition is enforced by the handler before [updateStatus] is called; this method
 * just persists the new status with the standard envelope bump.
 */
class ClaimRepositoryImpl(db: MongoDatabase) :
    MongoRepository<Claim>(db, CollectionNames.CLAIMS, Claim::class.java),
    ClaimRepository {

    override suspend fun get(id: String): Claim? = findById(id)

    override suspend fun listByPolicy(policyId: String): List<Claim> =
        collection.find(Filters.and(Filters.eq("policyId", policyId), notDeleted))
            .sort(Sorts.descending("intimatedAt"))
            .toList()

    override suspend fun listOpenByPolicy(policyId: String): List<Claim> {
        val terminal = ClaimStatus.entries.filter { it.terminal }.map { it.name }
        return collection.find(
            Filters.and(
                Filters.eq("policyId", policyId),
                Filters.nin("status", terminal),
                notDeleted,
            ),
        ).sort(Sorts.descending("intimatedAt")).toList()
    }

    override suspend fun list(req: PageRequest): Page<Claim> = paged(req)

    override suspend fun countAgainstNcb(policyId: String, policyYear: Int): Int =
        collection.countDocuments(
            Filters.and(
                Filters.eq("policyId", policyId),
                Filters.eq("policyYear", policyYear),
                // Any non-withdrawn claim counts against the NCB streak.
                Filters.ne("status", ClaimStatus.WITHDRAWN.name),
                notDeleted,
            ),
        ).toInt()

    override suspend fun create(claim: Claim, actor: String?): Claim = insert(claim)

    override suspend fun update(claim: Claim, expectedV: Long, actor: String?): Claim =
        replaceWithVersionCheck(claim, expectedV, "Claim")

    override suspend fun updateStatus(id: String, toStatus: ClaimStatus, actor: String?): Claim? {
        val current = findById(id) ?: return null
        // The handler has already validated the transition; persist via an optimistic replace.
        return replaceWithVersionCheck(current.copy(status = toStatus), current.v, "Claim")
    }

    override suspend fun softDelete(id: String, actor: String?): Boolean = softDelete(id)
}
