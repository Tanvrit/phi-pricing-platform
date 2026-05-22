package com.rate.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {

    @Test fun `fromRupees Double rounds to paise`() {
        assertEquals(12345L, Money.fromRupees(123.45).paise)
        assertEquals(12345L, Money.fromRupees(123.454).paise)
        // half-even rounding: 123.455 → banker's round → nearest even hundredth
        assertEquals(12346L, Money.fromRupees(123.455).paise)
    }

    @Test fun `fromRupees Long is exact`() {
        assertEquals(10000000L, Money.fromRupees(100_000L).paise)
    }

    @Test fun `plus and minus`() {
        val a = Money.fromRupees(100.50)
        val b = Money.fromRupees(25.25)
        assertEquals(Money.fromRupees(125.75), a + b)
        assertEquals(Money.fromRupees(75.25), a - b)
    }

    @Test fun `times by integer factor is exact`() {
        val m = Money.fromRupees(123.45)
        assertEquals(Money.fromRupees(370.35), m * 3)
    }

    @Test fun `times by double rounds half-even`() {
        val m = Money.fromRupees(100.00)
        // 100.00 * 0.18 = 18.00 exact
        assertEquals(Money.fromRupees(18.00), m * 0.18)
    }

    @Test fun `div by zero throws`() {
        assertFailsWith<IllegalArgumentException> {
            Money.fromRupees(100.0) / 0
        }
    }

    @Test fun `unary minus`() {
        assertEquals(Money.fromRupees(-50.0), -Money.fromRupees(50.0))
    }

    @Test fun `isZero and isNegative`() {
        assertTrue(Money.ZERO.isZero())
        assertFalse(Money.fromRupees(0.01).isZero())
        assertTrue(Money.fromRupees(-1.0).isNegative())
    }

    @Test fun `Indian grouping formats correctly`() {
        // ₹1,23,45,678.90
        val m = Money.fromRupees(12345678.90)
        assertEquals("₹1,23,45,678.90", m.formatIndian())
    }

    @Test fun `Indian grouping small amounts`() {
        assertEquals("₹999.00", Money.fromRupees(999.0).formatIndian())
        assertEquals("₹1,000.00", Money.fromRupees(1000.0).formatIndian())
        assertEquals("₹1,00,000.00", Money.fromRupees(100_000.0).formatIndian())
        assertEquals("₹10,00,000.00", Money.fromRupees(1_000_000.0).formatIndian())
        assertEquals("₹1,00,00,000.00", Money.fromRupees(10_000_000.0).formatIndian())
    }

    @Test fun `formatIndian negative numbers`() {
        assertEquals("-₹1,234.56", Money.fromRupees(-1234.56).formatIndian())
    }

    @Test fun `sumMoney over a list`() {
        val list = listOf(Money.fromRupees(10.0), Money.fromRupees(20.50), Money.fromRupees(0.5))
        assertEquals(Money.fromRupees(31.0), list.sumMoney())
    }

    @Test fun `toRupees recovers Double approximation`() {
        assertEquals(123.45, Money.fromRupees(123.45).toRupees(), 0.001)
    }
}
