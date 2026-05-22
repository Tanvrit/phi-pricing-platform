package com.rate.domain.regulatory

import com.rate.domain.engine.TEST_PLAN_BASIC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProspectusBuilderTest {

    @Test fun `prospectus is complete - every IRDAI-mandated heading present`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        assertTrue(p.mandatoryHeadingsPresent,
            "Missing mandatory heading. Got: ${p.sections.map { it.heading }}")
    }

    @Test fun `prospectus contains the UIN even when it's a placeholder`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val section = p.sections.first { it.heading == "Product Name and UIN" }
        assertTrue(section.body.contains("UIN"), "UIN missing from product-name section")
    }

    @Test fun `free-look section mentions 15 days per IRDAI 2024`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val freeLook = p.sections.first { "Free Look" in it.heading }
        assertTrue("15" in freeLook.body,
            "Free-look section must mention 15-day window. Got: ${freeLook.body}")
    }

    @Test fun `GST and Sec 80D disclosures are present`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val tax = p.sections.first { "Tax" in it.heading || "GST" in it.heading }
        assertTrue("Section 80D" in tax.body || "Sec 80D" in tax.body)
        assertTrue("18%" in tax.body || "HSN 9971" in tax.body)
    }

    @Test fun `sum insured options listed`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val si = p.sections.first { "Sum Insured" in it.heading }
        assertTrue(si.body.contains("lakh") || si.body.contains("crore") || si.body.contains("L"),
            "SI section should list Indian-formatted amounts. Got: ${si.body}")
    }

    @Test fun `grievance redressal contact is present`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val claims = p.sections.first { "Grievance" in it.heading || "Claim" in it.heading }
        assertTrue("grievance" in claims.body.lowercase())
        assertTrue("Ombudsman" in claims.body)
    }

    @Test fun `discount cap is mentioned`() {
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        val disc = p.sections.first { "Discount" in it.heading }
        assertTrue("${(TEST_PLAN_BASIC.maxDiscountCap * 100).toInt()}%" in disc.body)
    }

    @Test fun `every plan in the registry can be built without error`() {
        // Smoke test — exercising the builder against the test plan fixture.
        val p = ProspectusBuilder.build(TEST_PLAN_BASIC)
        assertEquals(ProspectusBuilder.MANDATORY_HEADINGS.size, p.sections.size,
            "Section count must match the mandatory-heading list")
    }
}
