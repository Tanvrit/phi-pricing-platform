package com.rate.server

import com.rate.server.logging.PiiMaskingConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PiiMaskingTest {

    @Test
    fun `masks aadhaar mobile and pan`() {
        assertEquals("XXXX XXXX 9012", PiiMaskingConverter.mask("1234 5678 9012"))
        assertEquals("XXXXXX3210", PiiMaskingConverter.mask("9876543210"))
        assertTrue(PiiMaskingConverter.mask("PAN ABCDE1234F here").contains("XXXXX"))
    }

    @Test
    fun `leaves non-pii text untouched`() {
        assertEquals("quote planId=PHI_BASIC saved", PiiMaskingConverter.mask("quote planId=PHI_BASIC saved"))
    }
}
