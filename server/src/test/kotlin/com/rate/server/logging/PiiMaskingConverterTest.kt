package com.rate.server.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * Defence-in-depth tests for the log PII masking converter.
 * Application code should not log PII in the first place, but if it slips through
 * (third-party libs, exception messages, stack traces) this converter rewrites the
 * line. These tests pin the masking rules so a regression in the regex set is caught
 * before it lands in a release.
 */
class PiiMaskingConverterTest {

    @Test
    fun `aadhaar 12-digit pattern is masked to last 4`() {
        val masked = PiiMaskingConverter.mask("aadhaar=234123412346 ok")
        masked shouldContain "XXXX XXXX 2346"
        masked shouldNotContain "234123412346"
    }

    @Test
    fun `aadhaar with embedded spaces still gets masked`() {
        val masked = PiiMaskingConverter.mask("Aadhaar: 2341 2341 2346")
        masked shouldContain "2346"
        masked shouldNotContain "2341 2341 2346"
    }

    @Test
    fun `mobile 10-digit Indian numbers are masked to last 4`() {
        val masked = PiiMaskingConverter.mask("OTP sent to 9876543210")
        masked shouldContain "XXXXXX3210"
        masked shouldNotContain "9876543210"
    }

    @Test
    fun `landline-style 11-digit number does not match mobile mask`() {
        // 11 digits → not a mobile per the regex.
        val masked = PiiMaskingConverter.mask("Ref: 12345678901")
        masked shouldContain "12345678901"
    }

    @Test
    fun `PAN format is masked`() {
        val masked = PiiMaskingConverter.mask("PAN=ABCDE1234F provided")
        masked shouldNotContain "ABCDE1234F"
        // Last 5 chars of the original PAN should survive in the masked form.
        masked shouldContain "1234F"
    }

    @Test
    fun `multiple PII values in the same line all get masked`() {
        val raw = "user=9876543210 aadhaar=234123412346 pan=ABCDE1234F submitted"
        val masked = PiiMaskingConverter.mask(raw)
        masked shouldNotContain "9876543210"
        masked shouldNotContain "234123412346"
        masked shouldNotContain "ABCDE1234F"
    }

    @Test
    fun `quote IDs and proposal numbers are NOT masked (not PII)`() {
        val raw = "quote Q-A1B2C3 proposal PHI-2026-XYZ"
        val masked = PiiMaskingConverter.mask(raw)
        masked shouldBe raw
    }

    @Test
    fun `empty input is safe`() {
        PiiMaskingConverter.mask("") shouldBe ""
    }

    @Test
    fun `non-PII numerics are unaffected`() {
        // 13- and 14-digit numbers are not Aadhaar / not mobile — should be left alone.
        val masked = PiiMaskingConverter.mask("amount=12345 si=10000000")
        masked shouldBe "amount=12345 si=10000000"
    }

    @Test
    fun `mobile beginning with 5 is not masked (not a valid Indian mobile)`() {
        // Indian mobile numbers start 6-9. A 10-digit number starting 5 is some
        // other identifier; we leave it alone rather than over-mask.
        val masked = PiiMaskingConverter.mask("ref=5123456789")
        masked shouldContain "5123456789"
    }
}
