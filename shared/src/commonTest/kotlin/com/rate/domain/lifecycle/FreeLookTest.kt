package com.rate.domain.lifecycle

import com.rate.domain.money.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the IRDAI 15-day free-look refund formula.
 *
 * Property-grade tests:
 *  - Day 0   cancel → near-full refund (only fixed deductions)
 *  - Day 7   cancel → ~half tenure-week of risk premium deducted
 *  - Day 15  cancel → still within window
 *  - Day 16  cancel → outside window → refundAmount = 0
 *  - Refund is never negative (deductions clamped)
 *  - Window starts at policy.issuedAt
 */
class FreeLookTest {

    private val issued = Instant.parse("2026-05-01T00:00:00Z")
    private val policy = Policy(
        id = "POL-FL-1", uin = "TEST-UIN", planId = "TEST_BASIC",
        status = PolicyStatus.ACTIVE,
        issuedAt = issued,
        expiresAt = Instant.parse("2027-05-01T00:00:00Z"),
        holderId = "H1", holderMobile = "9876543210", proposalId = "P1",
        currentSumInsured = 1_000_000L, currentTenure = 1, currentZone = "Zone 1",
        quotedAt = issued, freeLookEndsAt = Instant.parse("2026-05-16T00:00:00Z"),
        primaryAge = 35, familyType = "1A"
    )

    private fun atDay(n: Int): Instant =
        Instant.fromEpochMilliseconds(issued.toEpochMilliseconds() + n.toLong() * 24 * 60 * 60 * 1000)

    @Test fun `day 0 cancel returns full premium minus fixed deductions`() {
        val r = FreeLookEngine.refund(
            policy, atDay(0),
            totalPaid = Money.fromRupees(12_000.0),
            stampDuty = Money.fromRupees(200.0),
            adminCharges = Money.fromRupees(300.0)
        )
        assertTrue(r.withinWindow)
        assertEquals(0, r.daysEnjoyed)
        assertEquals(Money.ZERO, r.proportionalRiskPremium)
        // refund = 12_000 - 0 - 200 - 0 - 300 = 11_500
        assertEquals(Money.fromRupees(11_500.0), r.refundAmount)
    }

    @Test fun `day 7 cancel deducts proportional risk premium`() {
        val r = FreeLookEngine.refund(
            policy, atDay(7),
            totalPaid = Money.fromRupees(36_500.0)   // exactly ₹100/day for 365-day tenure
        )
        assertTrue(r.withinWindow)
        assertEquals(7, r.daysEnjoyed)
        // 7/365 * 36_500 = 700 to the rupee
        assertEquals(Money.fromRupees(700.0), r.proportionalRiskPremium)
        // refund = 36_500 - 700 = 35_800
        assertEquals(Money.fromRupees(35_800.0), r.refundAmount)
    }

    @Test fun `day 15 cancel is still within window`() {
        val r = FreeLookEngine.refund(
            policy, atDay(15),
            totalPaid = Money.fromRupees(36_500.0)
        )
        assertTrue(r.withinWindow)
        assertEquals(15, r.daysEnjoyed)
        // 15/365 * 36_500 = 1500
        assertEquals(Money.fromRupees(1500.0), r.proportionalRiskPremium)
        assertEquals(Money.fromRupees(35_000.0), r.refundAmount)
    }

    @Test fun `day 16 cancel is outside the window`() {
        val r = FreeLookEngine.refund(
            policy, atDay(16),
            totalPaid = Money.fromRupees(36_500.0)
        )
        assertFalse(r.withinWindow)
        assertEquals(Money.ZERO, r.refundAmount)
    }

    @Test fun `cancel before issue date does not crash and reports outside`() {
        val r = FreeLookEngine.refund(
            policy,
            Instant.fromEpochMilliseconds(issued.toEpochMilliseconds() - 86400_000L),
            totalPaid = Money.fromRupees(10_000.0)
        )
        assertFalse(r.withinWindow)
    }

    @Test fun `refund clamped to zero when deductions exceed payment`() {
        // tiny totalPaid + large stamp duty + medical exam + admin → would be negative
        val r = FreeLookEngine.refund(
            policy, atDay(0),
            totalPaid = Money.fromRupees(500.0),
            stampDuty = Money.fromRupees(300.0),
            medicalExamExpense = Money.fromRupees(800.0),
            adminCharges = Money.fromRupees(200.0)
        )
        assertEquals(Money.ZERO, r.refundAmount)
    }

    @Test fun `isWithinWindow boundary checks`() {
        assertTrue(FreeLookEngine.isWithinWindow(policy, atDay(0)))
        assertTrue(FreeLookEngine.isWithinWindow(policy, atDay(15)))
        assertFalse(FreeLookEngine.isWithinWindow(policy, atDay(16)))
        assertFalse(FreeLookEngine.isWithinWindow(policy, atDay(-1)))
    }

    @Test fun `WINDOW_DAYS is fifteen per IRDAI`() {
        // If this is ever changed without updating the test, regulatory review will catch it.
        assertEquals(15, FreeLookEngine.WINDOW_DAYS)
    }
}
