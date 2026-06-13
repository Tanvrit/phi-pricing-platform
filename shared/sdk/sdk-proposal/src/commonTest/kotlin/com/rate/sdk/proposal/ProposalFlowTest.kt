package com.rate.sdk.proposal

import com.rate.core.base.error.AppResult
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.proposal.handler.EligibilityHandler
import com.rate.sdk.proposal.handler.ProposalHandler
import com.rate.sdk.proposal.handler.ProposalSubmission
import com.rate.sdk.proposal.model.BankDetails
import com.rate.sdk.proposal.model.Proposal
import com.rate.sdk.proposal.model.ProposalStatus
import com.rate.sdk.proposal.model.journey.EligibilityRequest
import com.rate.sdk.proposal.repository.ProposalRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeProposalRepo : ProposalRepository {
    val store = mutableMapOf<String, Proposal>()
    override suspend fun get(id: String): Proposal? = store[id]
    override suspend fun getByProposalNumber(proposalNumber: String): Proposal? =
        store.values.firstOrNull { it.proposalNumber == proposalNumber && !it.isDeleted }
    override suspend fun listByMobile(mobile: String): List<Proposal> =
        store.values.filter { it.mobile == mobile && !it.isDeleted }
    override suspend fun create(proposal: Proposal, actor: String?): Proposal {
        store[proposal.id] = proposal; return proposal
    }
    override suspend fun update(proposal: Proposal, expectedV: Long, actor: String?): Proposal {
        val cur = store[proposal.id]
        check(cur == null || cur.v == expectedV) { "version conflict" }
        val bumped = proposal.copy(v = proposal.v + 1)
        store[proposal.id] = bumped; return bumped
    }
    override suspend fun list(req: PageRequest): Page<Proposal> =
        Page(store.values.toList(), store.size.toLong(), req.page, req.size)
}

class ProposalFlowTest {

    @Test
    fun eligibilityExcludesPedAndCriticalIllnessMembers() = runTest {
        val h = EligibilityHandler()
        val res = h.evaluate(
            EligibilityRequest(
                mobile = "9876543210",
                hasPED = true,
                pedMembers = listOf("SELF"),
                hasCriticalIllness = true,
                criticalIllnessMembers = listOf("SPOUSE"),
                members = listOf("SELF", "SPOUSE", "SON"),
            ),
        )
        res as AppResult.Ok
        assertEquals(listOf("SON"), res.value.coveredMembers)
        assertEquals(setOf("SELF", "SPOUSE"), res.value.uncoveredMembers.toSet())
    }

    @Test
    fun eligibilityRejectsTooManyMembers() = runTest {
        val res = EligibilityHandler(maxMembers = 2).evaluate(
            EligibilityRequest(mobile = "9876543210", members = listOf("A", "B", "C")),
        )
        assertTrue(res is AppResult.Err)
    }

    @Test
    fun proposalCreateMintsNumberPersistsAndTracks() = runTest {
        val repo = FakeProposalRepo()
        val h = ProposalHandler(repo, numberGen = { "PHI-TEST-00001" })
        val res = h.create(
            ProposalSubmission(
                mobile = "9876543210",
                planTier = "PREMIER",
                planRef = "PHI_BASIC",
                sumInsured = 1_000_000L,
                annualPremium = 12345.0,
                totalIncludingGst = 14567.10,
                bankDetails = BankDetails(
                    accountHolderName = "Asha",
                    accountNumber = "123456789012",
                    ifsc = "SBIN0001234",
                ),
            ),
        )
        res as AppResult.Ok
        assertEquals("PHI-TEST-00001", res.value.proposalNumber)
        assertEquals(ProposalStatus.UNDER_REVIEW, res.value.status)
        // Money conversion at assembly: 14567.10 INR → 1,456,710 paise.
        assertEquals(1_456_710L, res.value.totalIncludingGst.paise)

        val tracked = h.track("PHI-TEST-00001")
        tracked as AppResult.Ok
        assertEquals("9876543210", tracked.value.mobile)
    }

    @Test
    fun proposalRejectsBadBankDetails() = runTest {
        val h = ProposalHandler(FakeProposalRepo())
        val res = h.create(
            ProposalSubmission(
                mobile = "9876543210",
                planTier = "PREMIER",
                sumInsured = 1_000_000L,
                annualPremium = 100.0,
                totalIncludingGst = 118.0,
                bankDetails = BankDetails(accountHolderName = "x", accountNumber = "12", ifsc = "BAD"),
            ),
        )
        assertTrue(res is AppResult.Err)
    }

    @Test
    fun statusChangeBumpsVersionAndIsOptimistic() = runTest {
        val repo = FakeProposalRepo()
        val h = ProposalHandler(repo, numberGen = { "PHI-TEST-00002" })
        (h.create(ProposalSubmission(
            mobile = "9876543210", planTier = "GLOBAL", sumInsured = 5_000_000L,
            annualPremium = 50000.0, totalIncludingGst = 59000.0,
        )) as AppResult.Ok)
        val changed = h.changeStatus("PHI-TEST-00002", ProposalStatus.APPROVED, actor = "ops")
        changed as AppResult.Ok
        assertEquals(ProposalStatus.APPROVED, changed.value.status)
        assertEquals(2L, changed.value.v)
    }
}
