package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.party.model.PartyMember
import com.rate.sdk.party.model.PolicyHolder
import com.rate.sdk.party.repository.PartyMemberRepository
import com.rate.sdk.party.repository.PartyRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [PartyRepository] — admin-CRUD over the sealed [PolicyHolder]
 * (RetailProposer / GroupEmployer) plus the two natural lookups: [getByMobile] (resume an
 * in-progress journey, newest first) and [getByPan] (KYC de-dup, case-insensitive). The
 * sealed type round-trips via the `_class` discriminator.
 */
class PartyRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<PolicyHolder>(
        db, CollectionNames.PARTIES, PolicyHolder::class.java, "PolicyHolder",
    ),
    PartyRepository {

    override suspend fun getByMobile(mobile: String): PolicyHolder? =
        collection.find(Filters.and(Filters.eq("mobile", mobile), notDeleted))
            .sort(Sorts.descending("updatedAt"))
            .limit(1)
            .firstOrNull()

    override suspend fun getByPan(pan: String): PolicyHolder? =
        collection.find(
            Filters.and(
                Filters.regex("pan", "^${java.util.regex.Pattern.quote(pan)}$", "i"),
                notDeleted,
            ),
        ).limit(1).firstOrNull()
}

/**
 * Mongo actual for [PartyMemberRepository] — transactional floater members scoped to their
 * owning proposer (`proposerPartyRef`). [replaceForProposer] swaps the proposer's whole member
 * set: it soft-deletes the existing rows then upserts the new ones, so a re-submitted family
 * never leaves orphans.
 */
class PartyMemberRepositoryImpl(db: MongoDatabase) :
    MongoRepository<PartyMember>(db, CollectionNames.PARTY_MEMBERS, PartyMember::class.java),
    PartyMemberRepository {

    override suspend fun get(id: String): PartyMember? = findById(id)

    override suspend fun listByProposer(proposerPartyRef: String): List<PartyMember> =
        collection.find(Filters.and(Filters.eq("proposerPartyRef", proposerPartyRef), notDeleted))
            .sort(Sorts.ascending("createdAt"))
            .toList()

    override suspend fun upsert(member: PartyMember, actor: String?): PartyMember {
        upsertEntity(member)
        return findByIdIncludingDeleted(member.id) ?: member
    }

    override suspend fun replaceForProposer(
        proposerPartyRef: String,
        members: List<PartyMember>,
        actor: String?,
    ): List<PartyMember> {
        // Drop existing members for the proposer, then write the new set.
        raw.deleteMany(Filters.eq("proposerPartyRef", proposerPartyRef))
        members.forEach { upsertEntity(it) }
        return listByProposer(proposerPartyRef)
    }

    override suspend fun softDelete(id: String, actor: String?): Boolean = softDelete(id)

    override suspend fun list(req: PageRequest): Page<PartyMember> = paged(req)
}
