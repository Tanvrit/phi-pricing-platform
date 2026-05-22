package com.rate.domain.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the actuary-agreed No-Claim Bonus structure: 5% per claim-free year, capped at 50%.
 * Any future change to the formula has to update this test deliberately — preventing
 * silent drift of the bonus a customer expects to receive.
 */
class NcbTest {

    @Test fun `zero claim-free years yields zero bonus`() {
        assertEquals(0.0, Ncb.rate(0), 1e-9)
    }

    @Test fun `one claim-free year yields five percent`() {
        assertEquals(0.05, Ncb.rate(1), 1e-9)
    }

    @Test fun `five claim-free years yields 25 percent`() {
        assertEquals(0.25, Ncb.rate(5), 1e-9)
    }

    @Test fun `ten claim-free years hits the 50 percent cap`() {
        assertEquals(0.50, Ncb.rate(10), 1e-9)
    }

    @Test fun `fifteen claim-free years still capped at 50 percent`() {
        assertEquals(0.50, Ncb.rate(15), 1e-9)
        assertEquals(0.50, Ncb.rate(99), 1e-9)
    }

    @Test fun `discount amount is rate times base premium`() {
        // base = ₹50,000; 5 claim-free years => 25% NCB => ₹12,500 discount.
        assertEquals(12_500.0, Ncb.discount(5, 50_000.0), 0.01)
    }

    @Test fun `discount amount at cap`() {
        assertEquals(25_000.0, Ncb.discount(20, 50_000.0), 0.01)
    }

    @Test fun `negative claim-free years rejected`() {
        assertFailsWith<IllegalArgumentException> { Ncb.rate(-1) }
    }
}
