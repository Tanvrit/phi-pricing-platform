package com.rate.sdk.ui.buyonline

import com.rate.sdk.rating.model.CoverIds
import com.rate.sdk.ui.buyonline.model.BUYONLINE_ADDONS
import com.rate.sdk.ui.buyonline.model.BuyOnlineTier
import com.rate.sdk.ui.buyonline.model.PlanTier
import com.rate.sdk.ui.buyonline.model.coverSelectionsFor
import com.rate.sdk.ui.buyonline.model.planIdToTier
import com.rate.sdk.ui.buyonline.model.toBuyOnlineTier
import com.rate.sdk.ui.buyonline.model.toPlanId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure mapping tests — no Compose, no network. Locks down the tier ↔ plan id
 * bridge and the curated add-on → cover resolution the journey VM relies on to
 * build a `QuoteRequest`.
 */
class BuyOnlinePlanMappingTest {

    @Test
    fun tierToPlanIdRoundTrips() {
        BuyOnlineTier.entries.forEach { tier ->
            assertEquals(tier, planIdToTier(tier.toPlanId()), "round-trip for $tier")
        }
    }

    @Test
    fun unknownPlanIdFallsBackToPremier() {
        assertEquals(BuyOnlineTier.PREMIER, planIdToTier("NOT_A_PLAN"))
    }

    @Test
    fun uiTierBridgesToMappingTier() {
        assertEquals(BuyOnlineTier.PREMIER, PlanTier.PREMIER.toBuyOnlineTier())
        assertEquals(BuyOnlineTier.SIGNATURE, PlanTier.SIGNATURE.toBuyOnlineTier())
        assertEquals(BuyOnlineTier.GLOBAL, PlanTier.GLOBAL.toBuyOnlineTier())
    }

    @Test
    fun coverSelectionsResolveKnownAddOnIdsAndDropUnknown() {
        val selections = coverSelectionsFor(setOf("maternity", "air_ambulance", "not_a_real_addon"))
        val coverIds = selections.map { it.coverId }.toSet()
        assertEquals(2, selections.size, "unknown id must be dropped")
        assertTrue(CoverIds.MATERNITY_NEWBORN in coverIds)
        assertTrue(CoverIds.AIR_AMBULANCE in coverIds)
    }

    @Test
    fun maternityAddOnCarriesItsDefaultParam() {
        val maternity = BUYONLINE_ADDONS.first { it.id == "maternity" }
        val selection = coverSelectionsFor(setOf("maternity")).single()
        assertEquals(maternity.defaultParam, selection.params)
        assertEquals("50000", selection.params.param1)
    }

    @Test
    fun emptySelectionYieldsNoCovers() {
        assertTrue(coverSelectionsFor(emptySet()).isEmpty())
    }
}
