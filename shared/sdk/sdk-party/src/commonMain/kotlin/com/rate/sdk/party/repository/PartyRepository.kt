package com.rate.sdk.party.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.party.model.PartyMember
import com.rate.sdk.party.model.PolicyHolder

/**
 * Persistence PORT for policy-holding parties ([PolicyHolder]: RetailProposer +
 * GroupEmployer). Extends the generic admin-CRUD [ConfigRepository] so operators
 * get list/get/create/update/soft-delete/restore/publish/bulk-upsert for free, and
 * adds the two natural lookups the buy-online & operator flows need:
 * [getByMobile] (resume an in-progress journey) and [getByPan] (KYC de-dup).
 *
 * The MongoDB-backed actual lives in `server-persistence`; this is a pure contract.
 */
interface PartyRepository : ConfigRepository<PolicyHolder> {
    /** Most recently updated, non-deleted party for an exact mobile, or null. */
    suspend fun getByMobile(mobile: String): PolicyHolder?

    /** Non-deleted party whose PAN matches (case-insensitive), or null. */
    suspend fun getByPan(pan: String): PolicyHolder?
}

/**
 * Persistence PORT for [PartyMember] floater members. These are transactional
 * ([BaseDataClass]) records, not admin config, so this is a focused CRUD contract
 * scoped to the owning proposer rather than the generic [ConfigRepository].
 */
interface PartyMemberRepository {
    suspend fun get(id: String): PartyMember?
    suspend fun listByProposer(proposerPartyRef: String): List<PartyMember>
    suspend fun upsert(member: PartyMember, actor: String?): PartyMember
    /** Replace the full member set for a proposer atomically; returns the saved members. */
    suspend fun replaceForProposer(
        proposerPartyRef: String,
        members: List<PartyMember>,
        actor: String?,
    ): List<PartyMember>
    suspend fun softDelete(id: String, actor: String?): Boolean
    suspend fun list(req: PageRequest = PageRequest()): Page<PartyMember>
}
