package com.rate.sdk.policy.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.policy.model.Policy
import com.rate.sdk.policy.model.PolicyStatus
import kotlinx.datetime.Instant

/**
 * Persistence PORT for [Policy] documents. Policies are TRANSACTIONAL records
 * ([com.rate.core.base.model.BaseDataClass]), not admin-managed config — operators never
 * hand-author a policy; it is produced by the proposal → issuance flow. So this is a focused
 * CRUD contract (the same shape as sdk-party's member/census repositories) rather than the
 * generic [com.rate.core.base.repository.ConfigRepository], plus the natural lookups the
 * servicing flows need.
 *
 * The MongoDB-backed actual lives in `server-persistence`; this is a pure contract.
 */
interface PolicyRepository {
    suspend fun get(id: String): Policy?

    /** Active/most-recent policy for a holder party (`PolicyHolder._id`). */
    suspend fun getByHolder(holderId: String): List<Policy>

    /** Resolve by holder mobile (servicing lookup). Newest first. */
    suspend fun getByMobile(mobile: String): List<Policy>

    /** Paged, filtered listing (operator console). Filters interpreted by the implementation. */
    suspend fun list(req: PageRequest = PageRequest()): Page<Policy>

    suspend fun create(policy: Policy, actor: String?): Policy

    /** Optimistic update — fails with Conflict if [expectedV] != stored v. */
    suspend fun update(policy: Policy, expectedV: Long, actor: String?): Policy

    suspend fun softDelete(id: String, actor: String?): Boolean
    suspend fun restore(id: String, actor: String?): Boolean

    /** Idempotent bulk insert/update (migration/seed). Returns count written. */
    suspend fun bulkUpsert(policies: List<Policy>, actor: String?): Int

    /**
     * Renewal-due query: in-force policies whose `expiresAt` falls within `[from, to]`,
     * driving the renewal-reminder batch and the operator's renewals worklist. Defaults to
     * the ACTIVE/REVIVED in-force set; pass [statuses] to widen (e.g. include LAPSED within
     * the grace window).
     */
    suspend fun findRenewalsDue(
        from: Instant,
        to: Instant,
        statuses: Set<PolicyStatus> = setOf(PolicyStatus.ACTIVE, PolicyStatus.REVIVED),
        req: PageRequest = PageRequest(),
    ): Page<Policy>
}
