package com.rate.domain.lifecycle

import com.rate.domain.model.Member
import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the rationale strings on every Endorsement variant. The strings are part of the
 * customer-visible contract — they appear on the endorsement confirmation a customer
 * receives. A regression that changes (or empties) any of them is a regulatory event.
 */
class EndorsementTest {

    private val t0 = Instant.parse("2026-05-22T00:00:00Z")
    private val anyDob = LocalDate(1990, 1, 1)

    @Test fun `AddMember rationale mentions pro-rata + fresh waiting periods`() {
        val e = Endorsement.AddMember(
            policyId = "POL-1", requestedAt = t0,
            memberToAdd = Member(2, 28, "Spouse", "F")
        )
        assertTrue("pro-rata" in e.proRataChargeRationale.lowercase())
        assertTrue("waiting period" in e.proRataChargeRationale.lowercase())
    }

    @Test fun `IncreaseSumInsured rationale mentions delta + underwriting review`() {
        val e = Endorsement.IncreaseSumInsured(
            policyId = "POL-1", requestedAt = t0,
            toAmount = Money.fromRupees(2_500_000L)
        )
        assertTrue("premium at new SI" in e.proRataChargeRationale)
        assertTrue("underwriting" in e.proRataChargeRationale.lowercase())
    }

    @Test fun `AddressChange rationale handles same-zone case`() {
        val e = Endorsement.AddressChange(
            policyId = "POL-1", requestedAt = t0,
            newAddress = "123 New Street, Mumbai", newPincode = "400001"
        )
        assertTrue("Same-zone" in e.proRataChargeRationale ||
                   "same-zone" in e.proRataChargeRationale.lowercase())
    }

    @Test fun `NomineeChange rationale states no premium impact`() {
        val e = Endorsement.NomineeChange(
            policyId = "POL-1", requestedAt = t0,
            newNominees = listOf(Nominee("Alice", "Spouse", anyDob, percentShare = 100))
        )
        assertEquals("Nominee change has no premium impact.", e.proRataChargeRationale)
    }

    @Test fun `RemoveMember rationale mentions refund + admin charges`() {
        val e = Endorsement.RemoveMember(policyId = "POL-1", requestedAt = t0, memberId = 3)
        assertTrue("Refund" in e.proRataChargeRationale)
        assertTrue("admin charges" in e.proRataChargeRationale)
    }

    @Test fun `every variant carries the policyId`() {
        val variants: List<Endorsement> = listOf(
            Endorsement.AddMember("POL-X", t0, Member(2, 30, "Self")),
            Endorsement.IncreaseSumInsured("POL-X", t0, Money.fromRupees(1_000_000L)),
            Endorsement.AddressChange("POL-X", t0, "addr", "560001"),
            Endorsement.NomineeChange("POL-X", t0, emptyList()),
            Endorsement.RemoveMember("POL-X", t0, 2)
        )
        variants.forEach { assertEquals("POL-X", it.policyId) }
    }

    @Test fun `every variant carries the requestedAt`() {
        val variants: List<Endorsement> = listOf(
            Endorsement.AddMember("P", t0, Member(2, 30, "Self")),
            Endorsement.IncreaseSumInsured("P", t0, Money.fromRupees(1_000_000L)),
            Endorsement.AddressChange("P", t0, "addr", "560001"),
            Endorsement.NomineeChange("P", t0, emptyList()),
            Endorsement.RemoveMember("P", t0, 2)
        )
        variants.forEach { assertEquals(t0, it.requestedAt) }
    }
}
