package com.rate.sdk.quoting

import com.rate.core.base.time.Now
import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanType
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.RenewalIllustrationLine
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Tenure
import com.rate.core.rating.ports.model.YearBreakdown
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.BenefitScheduleLine
import com.rate.sdk.catalog.model.group.GroupGrade
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.quoting.handler.doc.CisBuilder
import com.rate.sdk.quoting.handler.doc.GroupBenefitScheduleBuilder
import com.rate.sdk.quoting.handler.doc.ProspectusBuilder
import com.rate.sdk.quoting.handler.doc.SalesIllustrationBuilder
import com.rate.sdk.quoting.model.GroupQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun plan() = Plan(
    id = "PHI_BASIC",
    name = "PRU Health Basic",
    planType = PlanType.DOMESTIC,
    availableSumInsureds = listOf(1_000_000L, 2_500_000L, 5_000_000L),
    availableFamilyTypes = listOf("1A", "2A"),
)

private fun request() = QuoteRequest(
    planId = "PHI_BASIC",
    primaryAge = 35,
    sumInsured = 1_000_000L,
    familyType = "1A",
    zone = "Zone 1",
    tenure = Tenure.THREE_YEARS,
    paymentMode = PaymentMode.ANNUAL,
    paymentTenure = Tenure.ONE_YEAR,
    members = listOf(Member(1, 35, "self")),
)

private fun result() = QuoteResult(
    requestId = "r1",
    planId = "PHI_BASIC",
    basePremiumTotal = 30_000.0,
    coverBreakdown = emptyList(),
    totalAddons = 3_000.0,
    uwLoadingAmount = 0.0,
    totalBeforeDiscount = 33_000.0,
    discountBreakdown = emptyList(),
    totalDiscountAmount = 3_300.0,
    totalAfterDiscount = 29_700.0,
    instalmentLoadingAmount = 0.0,
    instalmentPremium = 29_700.0,
    instalmentCount = 1,
    yearlyBreakdown = listOf(
        YearBreakdown(1, 35, "31 - 35", 10_000.0, emptyMap(), 9_900.0),
        YearBreakdown(2, 36, "36 - 40", 10_000.0, emptyMap(), 9_900.0),
        YearBreakdown(3, 37, "36 - 40", 10_000.0, emptyMap(), 9_900.0),
    ),
    gstRate = 0.18,
    gstAmount = 5_346.0,
    totalIncludingGst = 35_046.0,
    calculatedAt = Now.instant(),
)

class DocBuildersTest {

    @Test
    fun salesIllustration_has_one_year_per_breakdown_and_accumulates() {
        val ill = SalesIllustrationBuilder.build(request(), result())
        assertEquals(3, ill.years.size)
        assertEquals(3, ill.tenureYears)
        // cumulative is monotonic and the last equals total-over-tenure.
        assertTrue(ill.years[1].cumulativeTotal > ill.years[0].cumulativeTotal)
        assertEquals(ill.years.last().cumulativeTotal, ill.totalOverTenure, 0.001)
    }

    @Test
    fun salesIllustration_fromRenewal_reconstructs_preNcb_base() {
        val lines = listOf(
            RenewalIllustrationLine(year = 1, age = 36, projectedPremium = 9_000.0, ncbPercent = 0.05, ncbAmount = 500.0),
            RenewalIllustrationLine(year = 2, age = 37, projectedPremium = 9_500.0, ncbPercent = 0.10, ncbAmount = 1_000.0),
        )
        val ill = SalesIllustrationBuilder.fromRenewalIllustration("PHI_BASIC", lines)
        assertEquals(2, ill.years.size)
        // basePremium = net + ncbAmount (pre-NCB reconstruction).
        assertEquals(9_500.0, ill.years[0].basePremium, 0.001)
        assertEquals(10_500.0, ill.years[1].basePremium, 0.001)
        // age-band resolved from age.
        assertEquals("36 - 40", ill.years[0].ageBand)
    }

    @Test
    fun cis_renders_actual_si_family_zone() {
        val cis = CisBuilder.build(
            plan = plan(),
            request = request(),
            quoteResult = result(),
            proposalNumber = "P-100",
            customerName = "Asha",
            customerMobile = "9876543210",
        )
        val byLabel = cis.fields.associate { it.label to it.value }
        assertEquals("1A", byLabel["Family Type"])
        assertEquals("Zone 1", byLabel["Zone"])
        assertEquals("XXXXXX3210", byLabel["Customer Mobile"])
        assertEquals(15, cis.freeLookDays)
        assertTrue(byLabel["Sum Insured"]!!.contains("10,00,000"))
    }

    @Test
    fun prospectus_has_all_mandatory_headings() {
        val covers = listOf(
            Cover(code = "cover_ayush", name = "AYUSH", description = "Ayurveda etc."),
            Cover(code = "room_rent", name = "Room Rent", description = "Single private AC"),
        )
        val doc = ProspectusBuilder.build(plan(), covers)
        assertTrue(doc.mandatoryHeadingsPresent)
        assertEquals(ProspectusBuilder.MANDATORY_HEADINGS.size, doc.sections.size)
    }

    @Test
    fun groupBenefitSchedule_resolves_grade_lines_and_rollup() {
        val sched = BenefitSchedule(
            id = "SCH1",
            name = "Grade A Base",
            lines = listOf(
                BenefitScheduleLine("room_rent", "Room Rent", Money.fromRupees(5_000L)),
                BenefitScheduleLine("icu", "ICU", Money.fromRupees(10_000L)),
            ),
        )
        val grade = GroupGrade(
            id = "G1",
            grade = "Grade A",
            sumInsured = Money.fromRupees(500_000L),
            benefitScheduleRefs = listOf("SCH1"),
        )
        val config = GroupProductConfig(code = "GROUP_EE", name = "Employer-Employee GHI")
        val quote = GroupQuote(
            request = request(),
            result = result().copy(totalIncludingGst = 1_200_000.0),
            employerName = "Acme Corp",
            totalLives = 120,
            groupProductRef = "GP1",
        )
        val doc = GroupBenefitScheduleBuilder.build(quote, config, listOf(grade), listOf(sched))
        assertEquals(1, doc.grades.size)
        assertEquals(2, doc.grades.first().lines.size)
        assertEquals(120, doc.totalLives)
        assertEquals(Money.fromRupees(1_200_000L), doc.totalAnnualPremiumInclGst)
        assertEquals("Acme Corp", doc.employerName)
        assertEquals(ProductLine.GROUP, quote.productLine)
    }
}
