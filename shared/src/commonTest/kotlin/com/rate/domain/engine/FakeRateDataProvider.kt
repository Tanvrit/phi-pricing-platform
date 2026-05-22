package com.rate.domain.engine

import com.rate.domain.model.*
import com.rate.domain.repository.RateDataProvider

/**
 * Deterministic synthetic rate data for unit testing.
 *
 * The goal is NOT to mirror the real Excel rate table — the goal is to pin the engine's
 * arithmetic transformation. Given identical synthetic rates in, identical numbers out:
 * any change to engine logic (accumulation base, discount cap, GST math, etc.) is caught.
 *
 * Conventions used by this fake:
 * - Base premium = age_band * 100 + sumInsured/100_000. Simple, no zone/family multipliers.
 * - Cover rates are constants per cover ID (no parameter sensitivity unless noted).
 * - Member-level rates: critical_illness uses 5 per mille per year of age above 18.
 * - Instalment counts: monthly=12, quarterly=4, half-yearly=2, single/annual=1.
 */
class FakeRateDataProvider(
    val plans: List<Plan> = listOf(TEST_PLAN_BASIC, TEST_PLAN_FLAGSHIP, TEST_PLAN_SENIOR),
    val baseRate: (planId: String, ft: String, zone: String, ageBandMin: Int, si: Long) -> Double = { _, _, _, ageBandMin, si ->
        ageBandMin * 100.0 + si / 100_000.0
    },
    val coverRates: Map<String, Double> = DEFAULT_COVER_RATES,
    val discountRates: Map<String, Double> = DEFAULT_DISCOUNT_RATES
) : RateDataProvider {

    override suspend fun getBasePremium(
        planId: String, familyType: String, zone: String, ageBandMinAge: Int, sumInsured: Long
    ): Double = baseRate(planId, familyType, zone, ageBandMinAge, sumInsured)

    override suspend fun getCoverRate(
        coverId: String, param1: String?, param2: String?,
        ageBandMinAge: Int?, sumInsured: Long?, planOrTenureKey: String?
    ): Double = coverRates[coverId] ?: 0.0

    override suspend fun getMemberLevelRate(
        coverId: String, memberAgeBandMin: Int, param1: String?, sumInsured: Long?
    ): Double = when (coverId) {
        // Critical Illness: 5 per mille per "year of age above 18", capped at age 70
        CoverIds.CRITICAL_ILLNESS -> {
            val effectiveAge = memberAgeBandMin.coerceIn(18, 70)
            (effectiveAge - 18) * 5.0
        }
        CoverIds.DAILY_HOSPITAL_CASH -> (param1?.toDoubleOrNull() ?: 0.0) * 0.10
        CoverIds.PERSONAL_ACCIDENT -> (param1?.toDoubleOrNull() ?: 0.0) * 0.001
        CoverIds.CHRONIC_MANAGEMENT -> (param1?.toIntOrNull() ?: 0) * 500.0
        else -> 0.0
    }

    override suspend fun getInstalmentCount(
        tenure: Tenure, paymentTenure: Tenure, paymentMode: PaymentMode
    ): Int = when (paymentMode) {
        PaymentMode.MONTHLY -> 12 * tenure.years
        PaymentMode.QUARTERLY -> 4 * tenure.years
        PaymentMode.HALF_YEARLY -> 2 * tenure.years
        PaymentMode.ANNUAL -> tenure.years
        PaymentMode.SINGLE_PREMIUM -> 1
    }

    override suspend fun getAllPlans(): List<Plan> = plans
    override suspend fun getPlan(planId: String): Plan? = plans.firstOrNull { it.id == planId }

    override suspend fun getDiscountRate(discountId: String, paramKey: String?): Double {
        val key = if (paramKey != null) "${discountId}_$paramKey" else discountId
        return discountRates[key] ?: discountRates[discountId] ?: 0.0
    }

    override suspend fun getCoverAvailability(planId: String): Set<String> = emptySet()

    override suspend fun rateTableVersion(): String = "fake-test-v1"
}

// ── Test plans ─────────────────────────────────────────────────────────────

val TEST_PLAN_BASIC = Plan(
    id = "TEST_BASIC",
    name = "Test Basic Plan",
    planType = PlanType.DOMESTIC,
    description = "Synthetic plan for engine tests",
    availableSumInsureds = listOf(500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L),
    availableZones = listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4"),
    availableFamilyTypes = listOf("1A", "2A", "2A1C", "2A2C", "1A1C", "1A2C", "multi"),
    maxDiscountCap = 0.30,
    minAge = 5,
    maxAge = 99,
    gstRate = 0.18
)

val TEST_PLAN_FLAGSHIP = Plan(
    id = "TEST_FLAGSHIP",
    name = "Test Flagship Plan",
    planType = PlanType.DOMESTIC_FLAGSHIP,
    description = "Synthetic flagship plan for engine tests",
    availableSumInsureds = listOf(2_500_000L, 5_000_000L, 10_000_000L, 50_000_000L),
    availableZones = listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4"),
    availableFamilyTypes = listOf("1A", "2A", "2A1C", "2A2C", "2A3C", "1A1C", "1A2C", "multi"),
    maxDiscountCap = 0.30,
    minAge = 18,
    maxAge = 75,
    gstRate = 0.18
)

val TEST_PLAN_SENIOR = Plan(
    id = "TEST_SENIOR",
    name = "Test Senior Plan",
    planType = PlanType.DOMESTIC_SENIOR,
    description = "Synthetic senior plan for engine tests",
    availableSumInsureds = listOf(500_000L, 1_000_000L, 2_500_000L),
    availableZones = listOf("Pan India"),
    availableFamilyTypes = listOf("1A", "2A"),
    maxDiscountCap = 0.20,
    minAge = 46,
    maxAge = 80,
    gstRate = 0.18
)

// ── Synthetic rate constants ───────────────────────────────────────────────

val DEFAULT_COVER_RATES: Map<String, Double> = mapOf(
    // Accumulating % covers — small, predictable, non-overlapping rates so test deltas are easy to reason about
    CoverIds.DAY1_INSTANT to 0.10,
    CoverIds.LOYALTY_BONUS to 0.05,
    CoverIds.DOUBLE_COVER_7YR to 0.0325,
    CoverIds.CHRONIC_INSTANT to 0.08,
    CoverIds.CONSUMABLES_LIST1 to 0.05,
    CoverIds.PED_WAITING to 0.12,
    CoverIds.SPECIFIC_ILLNESS_WAITING to 0.06,
    CoverIds.MODERN_TREATMENT_PLUS to 0.0289,
    CoverIds.ROOM_RENT_MOD to 0.07,
    CoverIds.DISEASE_SUBLIMIT to 0.0647,
    CoverIds.PRE_POST_HOSP to 0.10,
    CoverIds.CONSUMABLE_PLUS to 0.09,
    CoverIds.HOME_CARE to 167.0,                  // flat INR
    CoverIds.INFINITE_CLAIM to 0.04,
    CoverIds.RESTORATION_PLUS to 0.06,
    CoverIds.DONOR_PLUS to 417.0,                 // flat INR × members
    CoverIds.SPOUSE_PROTECT to 0.05,
    CoverIds.DURABLE_MEDICAL to 0.04,
    CoverIds.TENURE_WISE to 0.025,
    CoverIds.SMART_SELECT to -0.15,               // discount (cover-pass)
    CoverIds.GOOD_HEALTH to 0.0,
    CoverIds.PER_CLAIM_DEDUCTIBLE to -0.10,
    CoverIds.AGGREGATE_DEDUCTIBLE to -0.15,
    CoverIds.CO_PAY to -0.05,
    CoverIds.CHILD_PROTECT to 100.0,
    CoverIds.AIR_AMBULANCE to 433.0,
    CoverIds.FITNESS_PLUS to 649.0,
    CoverIds.WELLNESS_PACKAGE to 0.0,
    CoverIds.SECOND_OPINION to 67.0,
    CoverIds.POST_DELIVERY_CARE to 167.0,
    CoverIds.SURROGATE_MOTHER to 908.0,
    CoverIds.OOCYTE_DONOR to 845.75,
    CoverIds.POST_DISCHARGE_CARE to 167.0,
    CoverIds.ADVENTURE_SPORTS to 183.0,
    CoverIds.FEMALE_VACCINATION to 3333.0,
    CoverIds.PRU_HEALTH_SPECIALIST to 83.0,
    CoverIds.ADVANCE_HEALTH_CHECKUP to 975.0,
    CoverIds.CASHLESS_OPD to 1500.0,
    CoverIds.MATERNITY_NEWBORN to 5000.0,
    CoverIds.INFERTILITY to 4000.0,
    CoverIds.CONVALESCENCE to 800.0,
    CoverIds.COMPASSIONATE to 600.0,
    CoverIds.MATERNITY_FIXED to 7500.0,
    CoverIds.CANCER_BOOSTER to 0.025,
    CoverIds.CANCER_SCREENING to 1200.0,
    CoverIds.PRUDENTIAL_HEALTHY to 0.0075,
    CoverIds.PREMIUM_RETURN to 0.15,
    CoverIds.ENHANCED_GEO to 0.1667
)

val DEFAULT_DISCOUNT_RATES: Map<String, Double> = mapOf(
    CoverIds.DISC_EMPLOYEE to 0.10,
    CoverIds.DISC_NRI to 0.15,
    CoverIds.DISC_AUTO_DEBIT to 0.025,
    CoverIds.DISC_COMMISSION_LIEU to 0.15,
    CoverIds.DISC_GMC to 0.05,
    "${CoverIds.DISC_CIBIL}_701-750" to 0.025,
    "${CoverIds.DISC_CIBIL}_751-800" to 0.05,
    "${CoverIds.DISC_CIBIL}_801-849" to 0.10,
    "${CoverIds.DISC_CIBIL}_>=850" to 0.15,
    "${CoverIds.DISC_MULTI_MEMBER}_2-3 members" to 0.05,
    "${CoverIds.DISC_MULTI_MEMBER}_4+ members" to 0.10
)
