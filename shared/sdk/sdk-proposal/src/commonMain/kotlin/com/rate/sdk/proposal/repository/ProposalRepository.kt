package com.rate.sdk.proposal.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.proposal.model.Proposal

/**
 * Persistence PORT for buy-online [Proposal]s. Proposals are transactional ([BaseDataClass])
 * records, not admin-managed catalog config, so this is a focused CRUD contract rather than the
 * generic [com.rate.core.base.repository.ConfigRepository] — but it still exposes a paged
 * [list] so the operator console can run the buy-online funnel/dashboard.
 *
 * The MongoDB-backed actual lives in `server-persistence`; this is a pure contract. The
 * `proposalNumber` field is uniquely indexed for [getByProposalNumber] (the customer-facing
 * track endpoint) and the `mobile` field is indexed for [listByMobile] (resume / "my
 * applications").
 */
interface ProposalRepository {

    suspend fun get(id: String): Proposal?

    /** Resolve a proposal by its human-readable, shareable number (track endpoint). */
    suspend fun getByProposalNumber(proposalNumber: String): Proposal?

    /** All non-deleted proposals for a mobile, newest first. */
    suspend fun listByMobile(mobile: String): List<Proposal>

    /** Create a new proposal; stamps timestamps/version generically in the actual. */
    suspend fun create(proposal: Proposal, actor: String?): Proposal

    /** Optimistic update — fails with Conflict if [expectedV] != stored v. */
    suspend fun update(proposal: Proposal, expectedV: Long, actor: String?): Proposal

    /** Paged proposal list for the operator dashboard / buy-online funnel. */
    suspend fun list(req: PageRequest = PageRequest()): Page<Proposal>
}
