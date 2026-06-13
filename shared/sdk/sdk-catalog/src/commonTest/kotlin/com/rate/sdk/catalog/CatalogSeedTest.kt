package com.rate.sdk.catalog

import com.rate.core.regulatory.Zone
import com.rate.sdk.catalog.model.RateKind
import com.rate.sdk.catalog.repository.DefaultPincodeZoneResolver
import com.rate.sdk.catalog.seed.CatalogSeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogSeedTest {

    private val resolver = DefaultPincodeZoneResolver()

    @Test
    fun delhiPincodeResolvesToZone1() = runTest {
        assertEquals(Zone.ZONE_1, resolver.resolve("110001").zone)
        assertEquals("Delhi", resolver.resolve("110001").cityHint)
    }

    @Test
    fun mumbaiResolvesToZone1() = runTest {
        assertEquals(Zone.ZONE_1, resolver.resolve("400001").zone)
    }

    @Test
    fun jaipurResolvesToZone2() = runTest {
        assertEquals(Zone.ZONE_2, resolver.resolve("302001").zone)
    }

    @Test
    fun longestPrefixWins_threeDigitBeatsTwoDigit() = runTest {
        // "30" → Zone 3 (range2 30..31), but "302" (Jaipur) → Zone 2. 3-digit must win.
        assertEquals(Zone.ZONE_2, resolver.resolve("302010").zone)
        assertEquals(Zone.ZONE_3, resolver.resolve("305001").zone) // Ajmer: 305 has no 3-digit row → falls to "30"
    }

    @Test
    fun unknownAndInvalidPincodesFallToZone4() = runTest {
        assertEquals(Zone.ZONE_4, resolver.resolve("999999").zone)
        assertEquals(Zone.ZONE_4, resolver.resolve("abc").zone)
        assertEquals(Zone.ZONE_4, resolver.resolve("12345").zone) // not 6 digits
    }

    @Test
    fun coverSeedHasStableCodesAndAccumBases() {
        val covers = CatalogSeed.covers()
        // No duplicate codes.
        val codes = covers.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "duplicate cover codes in seed")

        // Every accumulating-style cover declares a non-empty accumBase.
        val accumKinds = setOf(RateKind.PERCENT_MULTIPLIER, RateKind.UW_LOADING, RateKind.POST)
        covers.filter { it.rateKind in accumKinds && it.accumBase.isEmpty() }
            .let { offenders ->
                // double_cover_7yr's base is just [base] — still non-empty. Confirm none missing.
                assertTrue(offenders.isEmpty(), "accum covers missing accumBase: ${offenders.map { it.code }}")
            }

        // Discount covers carry isDiscount.
        assertTrue(covers.first { it.code == "co_pay" }.isDiscount)
        assertFalse(covers.first { it.code == "day1_instant" }.isDiscount)

        // personal_accident is member-level (relocated classification).
        assertEquals(RateKind.MEMBER_LEVEL, covers.first { it.code == "personal_accident" }.rateKind)
    }

    @Test
    fun addOnSeedMapsTiersToPlans() {
        val addOns = CatalogSeed.addOns()
        assertEquals("PHI_BASIC", addOns.first { it.code == "premier" }.planRef)
        assertEquals("PHI_FLAGSHIP1", addOns.first { it.code == "signature" }.planRef)
        assertEquals("PHI_GLOBAL1", addOns.first { it.code == "global" }.planRef)
        // Premier ships empty; Signature/Global pre-selected.
        assertTrue(addOns.first { it.code == "premier" }.items.isEmpty())
        assertFalse(addOns.first { it.code == "premier" }.preSelected)
        assertTrue(addOns.first { it.code == "global" }.preSelected)
    }

    @Test
    fun tenureSeedHasRelocatedDiscountSteps() {
        val byYears = CatalogSeed.tenures().associateBy { it.years }
        assertEquals(0.0, byYears.getValue(1).multiTenureDiscount)
        assertEquals(0.075, byYears.getValue(2).multiTenureDiscount)
        assertEquals(0.15, byYears.getValue(5).multiTenureDiscount)
    }
}
