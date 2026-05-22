package com.rate.domain.regulatory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UinRegistryTest {

    @Test fun `registry exposes a UIN for every known plan id`() {
        // Spot-check a few — the goal is to surface "TBD" entries to Phase 7.
        val basic = UinRegistry.forPlan("PHI_BASIC")
        assertNotNull(basic)
        assertEquals("PHI_BASIC", basic.planId)
        assertEquals("PRU Health Basic", basic.productName)
    }

    @Test fun `unknown plan id returns null`() {
        assertNull(UinRegistry.forPlan("DOES_NOT_EXIST"))
    }

    @Test fun `placeholders surface the gap`() {
        val placeholders = UinRegistry.placeholders()
        // Every entry today is TBD; Phase 7 will collapse this list to zero as real UINs land.
        assertTrue(placeholders.isNotEmpty(),
            "Placeholders list should be non-empty until Phase 7 wires real UINs")
        placeholders.forEach { assertTrue(it.isPlaceholder) }
    }

    @Test fun `placeholder UIN is not isActive`() {
        val u = Uin("TBD-XYZ", "Test", "PHI_BASIC")
        assertTrue(u.isPlaceholder)
        assertFalse(u.isActive)
    }

    @Test fun `every UIN in ALL has a non-blank productName`() {
        UinRegistry.ALL.forEach {
            assertTrue(it.productName.isNotBlank(), "productName blank for ${it.planId}")
        }
    }
}
