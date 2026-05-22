package com.rate.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorsTest {

    // ── Mobile ──────────────────────────────────────────────────────────────
    @Test fun `valid Indian mobile 10-digit starting 9 passes`() {
        assertTrue(Validators.mobile("9876543210").isValid)
    }
    @Test fun `mobile starting 5 fails`() {
        val r = Validators.mobile("5876543210")
        assertFalse(r.isValid)
        assertEquals("MOBILE_INVALID", (r as ValidationResult.Invalid).errorCode)
    }
    @Test fun `mobile with letters fails`() {
        assertFalse(Validators.mobile("987ABC3210").isValid)
    }
    @Test fun `9-digit mobile fails`() {
        assertFalse(Validators.mobile("987654321").isValid)
    }
    @Test fun `empty mobile fails with REQUIRED code`() {
        val r = Validators.mobile("") as ValidationResult.Invalid
        assertEquals("MOBILE_REQUIRED", r.errorCode)
    }

    // ── PAN ────────────────────────────────────────────────────────────────
    @Test fun `valid PAN ABCDE1234F passes`() {
        assertTrue(Validators.pan("ABCDE1234F").isValid)
    }
    @Test fun `lowercase PAN normalised and passes`() {
        assertTrue(Validators.pan("abcde1234f").isValid)
    }
    @Test fun `PAN with extra digit fails`() {
        assertFalse(Validators.pan("ABCDE12345F").isValid)
    }
    @Test fun `PAN starting with digit fails`() {
        assertFalse(Validators.pan("1BCDE1234F").isValid)
    }

    // ── IFSC ───────────────────────────────────────────────────────────────
    @Test fun `valid IFSC SBIN0001234 passes`() {
        assertTrue(Validators.ifsc("SBIN0001234").isValid)
    }
    @Test fun `IFSC without zero at position 5 fails`() {
        assertFalse(Validators.ifsc("SBIN1001234").isValid)
    }
    @Test fun `IFSC under 11 chars fails`() {
        assertFalse(Validators.ifsc("SBIN0001").isValid)
    }

    // ── Pincode ────────────────────────────────────────────────────────────
    @Test fun `valid pincode 560001 passes`() {
        assertTrue(Validators.pincode("560001").isValid)
    }
    @Test fun `pincode starting 0 fails`() {
        assertFalse(Validators.pincode("000001").isValid)
    }
    @Test fun `5-digit pincode fails`() {
        assertFalse(Validators.pincode("56000").isValid)
    }

    // ── Aadhaar (Verhoeff) ─────────────────────────────────────────────────
    // 234123412346 is a known Aadhaar-format Verhoeff-valid test number.
    // 234123412345 (last digit off-by-one) should fail.
    @Test fun `aadhaar with bad checksum fails`() {
        assertFalse(Validators.aadhaar("234123412345").isValid)
    }
    @Test fun `aadhaar starting 0 fails immediately`() {
        assertFalse(Validators.aadhaar("012345678912").isValid)
    }
    @Test fun `aadhaar starting 1 fails`() {
        assertFalse(Validators.aadhaar("112345678912").isValid)
    }
    @Test fun `aadhaar with letters fails`() {
        assertFalse(Validators.aadhaar("23412341234A").isValid)
    }
    @Test fun `aadhaar with 11 digits fails`() {
        assertFalse(Validators.aadhaar("23412341234").isValid)
    }

    // ── Account number ─────────────────────────────────────────────────────
    @Test fun `9-18 digit account number passes`() {
        assertTrue(Validators.accountNumber("123456789").isValid)
        assertTrue(Validators.accountNumber("123456789012345678").isValid)
    }
    @Test fun `8-digit account fails`() {
        assertFalse(Validators.accountNumber("12345678").isValid)
    }
    @Test fun `account with letters fails`() {
        assertFalse(Validators.accountNumber("1234A6789").isValid)
    }
}
