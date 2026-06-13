package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.proposal.model.Proposal
import com.rate.sdk.proposal.repository.ProposalRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [ProposalRepository] — transactional buy-online proposals. The
 * `proposalNumber` is uniquely indexed (customer-facing track endpoint) and `mobile` is
 * indexed for resume / "my applications". Optimistic update guards concurrent edits.
 */
class ProposalRepositoryImpl(db: MongoDatabase) :
    MongoRepository<Proposal>(db, CollectionNames.PROPOSALS, Proposal::class.java),
    ProposalRepository {

    override suspend fun get(id: String): Proposal? = findById(id)

    override suspend fun getByProposalNumber(proposalNumber: String): Proposal? =
        collection.find(Filters.and(Filters.eq("proposalNumber", proposalNumber), notDeleted))
            .limit(1)
            .firstOrNull()

    override suspend fun listByMobile(mobile: String): List<Proposal> =
        collection.find(Filters.and(Filters.eq("mobile", mobile), notDeleted))
            .sort(Sorts.descending("createdAt"))
            .toList()

    override suspend fun create(proposal: Proposal, actor: String?): Proposal = insert(proposal)

    override suspend fun update(proposal: Proposal, expectedV: Long, actor: String?): Proposal =
        replaceWithVersionCheck(proposal, expectedV, "Proposal")

    override suspend fun list(req: PageRequest): Page<Proposal> = paged(req)
}
