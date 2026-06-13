package com.rate.sdk.ui.kit.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {

    @Test
    fun englishResolvesDirectly() {
        assertEquals("Proceed", Strings["common.proceed", AegisLocale.EN])
    }

    @Test
    fun hindiResolvesWhenPresent() {
        assertEquals("आगे बढ़ें", Strings["common.proceed", AegisLocale.HI])
    }

    @Test
    fun missingKeyFallsBackToKeyItself() {
        assertEquals("nope.not.here", Strings["nope.not.here", AegisLocale.EN])
    }

    @Test
    fun fromCodeNormalisesAndDefaultsToEn() {
        assertEquals(AegisLocale.HI, AegisLocale.fromCode("HI"))
        assertEquals(AegisLocale.EN, AegisLocale.fromCode(null))
        assertEquals(AegisLocale.EN, AegisLocale.fromCode("fr"))
    }
}
