package com.rate.sdk.audit

import com.rate.sdk.audit.handler.AuditCanonicalizer
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditCanonicalizerTest {

    @Test
    fun sortsObjectKeysRecursively() {
        val raw = """{"b":1,"a":{"z":2,"y":3},"c":[3,2,1]}"""
        // keys sorted; array order preserved; integers without ".0".
        assertEquals(
            """{"a":{"y":3,"z":2},"b":1,"c":[3,2,1]}""",
            AuditCanonicalizer.canonicalizeJson(raw),
        )
    }

    @Test
    fun blankPayloadIsEmptyObject() {
        assertEquals("{}", AuditCanonicalizer.canonicalizeJson(null))
        assertEquals("{}", AuditCanonicalizer.canonicalizeJson("   "))
    }

    @Test
    fun integralNumbersHaveNoTrailingDotZero() {
        assertEquals("5", AuditCanonicalizer.canonicalNumber("5.0"))
        assertEquals("5", AuditCanonicalizer.canonicalNumber("5"))
        assertEquals("0", AuditCanonicalizer.canonicalNumber("0.000"))
        assertEquals("0", AuditCanonicalizer.canonicalNumber("-0"))
        assertEquals("0", AuditCanonicalizer.canonicalNumber("-0.0"))
        assertEquals("-12", AuditCanonicalizer.canonicalNumber("-12.00"))
    }

    @Test
    fun neverScientificNotation() {
        assertEquals("1000000", AuditCanonicalizer.canonicalNumber("1e6"))
        assertEquals("1000000", AuditCanonicalizer.canonicalNumber("1E6"))
        assertEquals("0.0000001", AuditCanonicalizer.canonicalNumber("1e-7"))
        assertEquals("12300", AuditCanonicalizer.canonicalNumber("1.23e4"))
        assertEquals("0.00123", AuditCanonicalizer.canonicalNumber("1.23e-3"))
        assertEquals("1500", AuditCanonicalizer.canonicalNumber("1.5e3"))
    }

    @Test
    fun fractionTrailingZerosTrimmed() {
        assertEquals("1.5", AuditCanonicalizer.canonicalNumber("1.500"))
        assertEquals("0.25", AuditCanonicalizer.canonicalNumber("0.2500"))
        assertEquals("123.456", AuditCanonicalizer.canonicalNumber("123.4560"))
    }

    @Test
    fun escapesControlCharactersAndQuotes() {
        val raw = """{"k":"a\"b\\c\nd\te"}"""
        assertEquals("""{"k":"a\"b\\c\nd\te"}""", AuditCanonicalizer.canonicalizeJson(raw))
    }

    @Test
    fun stableAcrossInputKeyOrder() {
        val a = AuditCanonicalizer.canonicalizeJson("""{"x":1,"y":2,"z":3}""")
        val b = AuditCanonicalizer.canonicalizeJson("""{"z":3,"x":1,"y":2}""")
        assertEquals(a, b)
        assertEquals("""{"x":1,"y":2,"z":3}""", a)
    }
}
