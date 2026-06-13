package com.rate.sdk.party

import com.rate.core.base.error.AppResult
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.sdk.party.handler.PartyHandler
import com.rate.sdk.party.model.Address
import com.rate.sdk.party.model.BankAccount
import com.rate.sdk.party.model.GroupEmployer
import com.rate.sdk.party.model.PolicyHolder
import com.rate.sdk.party.model.RetailProposer
import com.rate.sdk.party.repository.PartyRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakePartyRepo : PartyRepository {
    val store = mutableMapOf<String, PolicyHolder>()
    override suspend fun getByMobile(mobile: String): PolicyHolder? =
        store.values.firstOrNull { it.mobile == mobile && !it.isDeleted }
    override suspend fun getByPan(pan: String): PolicyHolder? =
        store.values.firstOrNull {
            val p = when (it) {
                is RetailProposer -> it.pan
                is GroupEmployer -> it.pan
            }
            p.equals(pan, ignoreCase = true) && !it.isDeleted
        }
    override suspend fun list(req: PageRequest): Page<PolicyHolder> =
        Page(store.values.toList(), store.size.toLong(), req.page, req.size)
    override suspend fun get(id: String): PolicyHolder? = store[id]
    override suspend fun create(entity: PolicyHolder, actor: String?): PolicyHolder {
        store[entity.id] = entity; return entity
    }
    override suspend fun update(entity: PolicyHolder, expectedV: Long, actor: String?): PolicyHolder {
        store[entity.id] = entity; return entity
    }
    override suspend fun softDelete(id: String, actor: String?): Boolean = store.remove(id) != null
    override suspend fun restore(id: String, actor: String?): Boolean = false
    override suspend fun publishDraft(draftId: String, actor: String?): PolicyHolder = store.getValue(draftId)
    override suspend fun bulkUpsert(entities: List<PolicyHolder>, actor: String?): Int {
        entities.forEach { store[it.id] = it }; return entities.size
    }
}

class PartyHandlerTest {

    @Test
    fun rejectsInvalidMobileAndPan() {
        val h = PartyHandler(FakePartyRepo())
        val errors = h.validate(
            RetailProposer(name = "Asha", mobile = "12345", pan = "BADPAN"),
        )
        assertTrue(errors.any { it.contains("mobile", ignoreCase = true) })
        assertTrue(errors.any { it.contains("PAN", ignoreCase = true) })
    }

    @Test
    fun createsValidProposerAndBlocksDuplicateMobile() = runTest {
        val repo = FakePartyRepo()
        val h = PartyHandler(repo)
        val p = RetailProposer(
            name = "Asha Rao",
            mobile = "9876543210",
            email = "asha@example.com",
            pan = "ABCDE1234F",
            address = Address(pincode = "560001"),
            bankAccount = BankAccount(
                accountHolderName = "Asha Rao",
                accountNumber = "1234567890",
                ifsc = "SBIN0001234",
            ),
        )
        val ok = h.create(p, actor = "test")
        assertTrue(ok is AppResult.Ok)

        val dup = h.create(p.copy(pan = "ZZZZE9999Z"), actor = "test")
        assertTrue(dup is AppResult.Err)
    }

    @Test
    fun validatesGroupEmployerGstin() {
        val h = PartyHandler(FakePartyRepo())
        val errors = h.validate(
            GroupEmployer(companyName = "Acme Ltd", mobile = "9876543210", gstin = "INVALID"),
        )
        assertTrue(errors.any { it.contains("GSTIN", ignoreCase = true) })
        // valid GSTIN passes
        val clean = h.validate(
            GroupEmployer(companyName = "Acme Ltd", mobile = "9876543210", gstin = "27AAAAA1234A1Z5"),
        )
        assertEquals(emptyList(), clean)
    }
}
