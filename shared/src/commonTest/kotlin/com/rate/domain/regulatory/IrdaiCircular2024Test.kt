package com.rate.domain.regulatory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IrdaiCircular2024Test {

    @Test fun `constants match the circular`() {
        assertEquals(15, IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE)
        assertEquals(30, IrdaiCircular2024.FREE_LOOK_DAYS_DISTANCE_MARKETING)
        assertEquals(30, IrdaiCircular2024.GRACE_PERIOD_INDIVIDUAL_DAYS)
        assertEquals(2,  IrdaiCircular2024.REVIVAL_WINDOW_YEARS)
        assertEquals(36, IrdaiCircular2024.PED_MAX_WAITING_MONTHS)
        assertEquals(24, IrdaiCircular2024.SPECIFIC_ILLNESS_MAX_WAITING_MONTHS)
        assertEquals(0.50, IrdaiCircular2024.MAX_NCB_PERCENT, 1e-9)
        assertEquals(0.05, IrdaiCircular2024.NCB_PER_YEAR_PERCENT, 1e-9)
        assertTrue(IrdaiCircular2024.MANDATORY_AYUSH_COVERAGE)
        assertTrue(IrdaiCircular2024.MANDATORY_MENTAL_HEALTH_PARITY)
        assertTrue(IrdaiCircular2024.MANDATORY_HIV_AIDS_COVERAGE)
    }

    @Test fun `modern treatments list has the canonical 12`() {
        assertEquals(12, IrdaiCircular2024.MODERN_TREATMENTS_REQUIRED.size,
            "Annexure II of the circular lists 12 modern treatments")
    }

    @Test fun `plan without AYUSH HIV mental-health is non-compliant`() {
        val check = IrdaiCircular2024.audit("PHI_BASIC", emptySet())
        assertFalse(check.isCompliant)
        assertTrue(check.violations.any { "AYUSH" in it })
        assertTrue(check.violations.any { "Mental" in it })
        assertTrue(check.violations.any { "HIV" in it })
    }

    @Test fun `plan with all three mandatory covers is compliant`() {
        val covers = setOf("cover_ayush", "cover_mental_health", "cover_hiv_aids")
        val check = IrdaiCircular2024.audit("PHI_BASIC", covers)
        assertTrue(check.isCompliant, "Got violations: ${check.violations}")
    }

    @Test fun `plan without modern_treatment_plus surfaces a warning, not a violation`() {
        val covers = setOf("cover_ayush", "cover_mental_health", "cover_hiv_aids")
        val check = IrdaiCircular2024.audit("PHI_BASIC", covers)
        assertTrue(check.isCompliant)
        assertTrue(check.warnings.any { "Modern Treatment" in it },
            "Missing modern_treatment_plus should warn. Got warnings: ${check.warnings}")
    }
}
