package com.rate.sdk.policy.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.policy.model.Claim
import com.rate.sdk.policy.model.ClaimStatus

/**
 * Persistence PORT for [Claim] documents. Claims are transactional
 * ([com.rate.core.base.model.BaseDataClass]) records keyed by their owning [Claim.policyId];
 * this is a focused CRUD contract plus the lookups the claims worklist and the NCB reset job
 * need. The MongoDB-backed actual lives in `server-persistence`.
 */
interface ClaimRepository {
    suspend fun get(id: String): Claim?

    /** All claims for a policy, newest first. */
    suspend fun listByPolicy(policyId: String): List<Claim>

    /** Open (non-terminal) claims for a policy — used by NCB and portability checks. */
    suspend fun listOpenByPolicy(policyId: String): List<Claim>

    /** Paged, filtered listing (claims worklist). */
    suspend fun list(req: PageRequest = PageRequest()): Page<Claim>

    /** Count of claims for a policy that count against the NCB streak, in a given policy year. */
    suspend fun countAgainstNcb(policyId: String, policyYear: Int): Int

    suspend fun create(claim: Claim, actor: String?): Claim

    /** Optimistic update — fails with Conflict if [expectedV] != stored v. */
    suspend fun update(claim: Claim, expectedV: Long, actor: String?): Claim

    /** Transition a claim to [toStatus] (FSM-checked by the handler before this call). */
    suspend fun updateStatus(id: String, toStatus: ClaimStatus, actor: String?): Claim?

    suspend fun softDelete(id: String, actor: String?): Boolean
}
