package com.rate.domain.lifecycle

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Nominee validation rules. IRDAI requires:
 *   - at least one nominee on every policy
 *   - percent shares across all nominees sum to exactly 100
 *
 * These pin those rules — a regression that lets a 95%-summing nominee list through would
 * generate a policy with an unallocated 5% payout, which is a legal disaster.
 */
class NomineeTest {

    private val anyDob = LocalDate(1990, 1, 1)

    @Test fun `single nominee with 100 percent share passes`() {
        val n = Nominee("Alice Doe", "Spouse", anyDob, percentShare = 100)
        assertNull(Nominee.validateShares(listOf(n)))
    }

    @Test fun `empty list fails`() {
        val msg = Nominee.validateShares(emptyList())
        assertNotNull(msg)
        assert("At least one nominee" in msg)
    }

    @Test fun `two nominees summing to 100 pass`() {
        val a = Nominee("Alice", "Spouse", anyDob, percentShare = 60)
        val b = Nominee("Bob", "Son", anyDob, percentShare = 40)
        assertNull(Nominee.validateShares(listOf(a, b)))
    }

    @Test fun `shares summing to 95 fail`() {
        val a = Nominee("Alice", "Spouse", anyDob, percentShare = 60)
        val b = Nominee("Bob", "Son", anyDob, percentShare = 35)
        val msg = Nominee.validateShares(listOf(a, b))
        assertNotNull(msg)
        assert("sum to 100" in msg)
        assert("95" in msg)
    }

    @Test fun `shares summing to 105 fail`() {
        val a = Nominee("Alice", "Spouse", anyDob, percentShare = 60)
        val b = Nominee("Bob", "Son", anyDob, percentShare = 45)
        val msg = Nominee.validateShares(listOf(a, b))
        assertNotNull(msg)
        assert("sum to 100" in msg)
    }

    @Test fun `share zero rejected at construction time`() {
        assertFailsWith<IllegalArgumentException> {
            Nominee("Alice", "Spouse", anyDob, percentShare = 0)
        }
    }

    @Test fun `share over 100 rejected at construction time`() {
        assertFailsWith<IllegalArgumentException> {
            Nominee("Alice", "Spouse", anyDob, percentShare = 101)
        }
    }

    @Test fun `blank name rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Nominee("   ", "Spouse", anyDob, percentShare = 100)
        }
    }

    @Test fun `blank relationship rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Nominee("Alice", "", anyDob, percentShare = 100)
        }
    }

    @Test fun `three-way split of 33-33-34 passes`() {
        val a = Nominee("A", "Son",    anyDob, percentShare = 33)
        val b = Nominee("B", "Son",    anyDob, percentShare = 33)
        val c = Nominee("C", "Spouse", anyDob, percentShare = 34)
        assertNull(Nominee.validateShares(listOf(a, b, c)))
    }

    @Test fun `pan and mobile optional, dob required by type`() {
        val n = Nominee("Alice", "Spouse", anyDob, percentShare = 100)
        assertEquals(null, n.mobile)
        assertEquals(null, n.pan)
        assertEquals(anyDob, n.dob)
    }
}
