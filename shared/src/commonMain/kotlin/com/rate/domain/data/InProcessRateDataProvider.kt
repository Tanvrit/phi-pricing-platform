package com.rate.domain.data

import com.rate.domain.model.CoverIds
import com.rate.domain.model.PaymentMode
import com.rate.domain.model.Plan
import com.rate.domain.model.PlanType
import com.rate.domain.model.Tenure
import com.rate.domain.repository.RateDataProvider

/**
 * In-process rate provider that runs without a server. Used by the WASM customer
 * journey (no Postgres in the browser) and as a fallback for the desktop calculator
 * when offline.
 *
 * Numbers are plausibly-scaled — they're the same structure the engine consumes for
 * the real PostgreSQL-backed provider, but the actual rates here are illustrative,
 * not actuarial. The server's `RateDataProviderImpl` (Exposed/Postgres) is the source
 * of truth for production pricing. This provider exists so the *engine path* is
 * exercised consistently (no special-case "client-only fake pricing"), even when the
 * server isn't reachable.
 *
 * Lifecycle: replace this with a network-cached snapshot of the real rate table the
 * first time the customer's browser successfully talks to /api/plans.
 */
class InProcessRateDataProvider : RateDataProvider {

    override suspend fun getAllPlans(): List<Plan> = PLANS
    override suspend fun getPlan(planId: String): Plan? = PLANS.firstOrNull { it.id == planId }

    /**
     * Base premium curve, indexed by age-band minimum + sum insured + plan tier.
     * Same shape as Excel's `(age band, SI) → base premium` table; numbers are
     * roughly tier × age × SI-bucket scaled so the headline quote is in the right
     * order of magnitude (₹X,000 – ₹X,00,000/year for retail PHI).
     */
    override suspend fun getBasePremium(
        planId: String, familyType: String, zone: String, ageBandMinAge: Int, sumInsured: Long
    ): Double {
        val tier = TIER_MULTIPLIER[planId] ?: 1.0
        val zoneMul = ZONE_MULTIPLIER[zone] ?: 1.0
        val familyMul = FAMILY_TYPE_MULTIPLIER[familyType] ?: 1.0
        val ageMul = ageBandMultiplier(ageBandMinAge)
        val siMul = sumInsuredMultiplier(sumInsured)
        return BASE_PREMIUM_FLOOR * tier * zoneMul * familyMul * ageMul * siMul
    }

    override suspend fun getCoverRate(
        coverId: String, param1: String?, param2: String?,
        ageBandMinAge: Int?, sumInsured: Long?, planOrTenureKey: String?
    ): Double = COVER_RATES[coverId] ?: 0.0

    override suspend fun getMemberLevelRate(
        coverId: String, memberAgeBandMin: Int, param1: String?, sumInsured: Long?
    ): Double = when (coverId) {
        CoverIds.CRITICAL_ILLNESS -> {
            // 5 per mille per year of age above 18 (engine multiplies by SI/1000).
            val effectiveAge = memberAgeBandMin.coerceIn(18, 70)
            (effectiveAge - 18) * 5.0
        }
        CoverIds.DAILY_HOSPITAL_CASH -> (param1?.toDoubleOrNull() ?: 0.0) * 0.10
        CoverIds.PERSONAL_ACCIDENT  -> (param1?.toDoubleOrNull() ?: 0.0) * 0.001
        CoverIds.CHRONIC_MANAGEMENT -> (param1?.toIntOrNull() ?: 0) * 500.0
        else -> 0.0
    }

    override suspend fun getInstalmentCount(
        tenure: Tenure, paymentTenure: Tenure, paymentMode: PaymentMode
    ): Int = when (paymentMode) {
        PaymentMode.MONTHLY        -> 12 * tenure.years
        PaymentMode.QUARTERLY      ->  4 * tenure.years
        PaymentMode.HALF_YEARLY    ->  2 * tenure.years
        PaymentMode.ANNUAL         ->      tenure.years
        PaymentMode.SINGLE_PREMIUM ->      1
    }

    override suspend fun getDiscountRate(discountId: String, paramKey: String?): Double {
        val key = if (paramKey != null) "${discountId}_$paramKey" else discountId
        return DISCOUNT_RATES[key] ?: DISCOUNT_RATES[discountId] ?: 0.0
    }

    override suspend fun getCoverAvailability(planId: String): Set<String> = emptySet()

    override suspend fun rateTableVersion(): String = "in-process-v1"

    private companion object {
        // ── Plans (the three customer-facing tiers) ─────────────────────────
        val PLANS: List<Plan> = listOf(
            Plan(
                id = "PHI_BASIC", name = "PRU Premier",
                planType = PlanType.DOMESTIC,
                description = "Entry-tier comprehensive PHI",
                availableSumInsureds = listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L),
                availableZones = listOf("Zone 1", "Zone 2", "Zone 3", "Pan India"),
                availableFamilyTypes = listOf(
                    "1A", "2A", "2A1C", "2A2C", "2A3C", "1A1C", "1A2C", "1A3C"
                ),
                maxDiscountCap = 0.30, minAge = 0, maxAge = 99, gstRate = 0.18
            ),
            Plan(
                id = "PHI_FLAGSHIP1", name = "PRU Signature",
                planType = PlanType.DOMESTIC_FLAGSHIP,
                description = "Flagship tier with richer covers + room category",
                availableSumInsureds = listOf(2_500_000L, 5_000_000L, 10_000_000L),
                availableZones = listOf("Zone 1", "Zone 2", "Zone 3", "Pan India"),
                availableFamilyTypes = listOf(
                    "1A", "2A", "2A1C", "2A2C", "2A3C", "1A1C", "1A2C", "1A3C"
                ),
                maxDiscountCap = 0.30, minAge = 0, maxAge = 99, gstRate = 0.18
            ),
            Plan(
                id = "PHI_GLOBAL1", name = "PRU Global",
                planType = PlanType.GLOBAL,
                description = "Global geography + premium cover bundle",
                availableSumInsureds = listOf(5_000_000L, 10_000_000L),
                availableZones = listOf("Zone 1", "Pan India"),
                availableFamilyTypes = listOf("1A", "2A", "2A1C", "2A2C", "2A3C"),
                maxDiscountCap = 0.30, minAge = 18, maxAge = 75, gstRate = 0.18
            ),
        )

        const val BASE_PREMIUM_FLOOR: Double = 3_000.0

        val TIER_MULTIPLIER: Map<String, Double> = mapOf(
            "PHI_BASIC" to 1.00,
            "PHI_FLAGSHIP1" to 1.50,
            "PHI_GLOBAL1" to 2.40,
        )

        val ZONE_MULTIPLIER: Map<String, Double> = mapOf(
            "Zone 1"    to 1.00,
            "Zone 2"    to 0.85,
            "Zone 3"    to 0.75,
            "Zone 4"    to 0.65,
            "Pan India" to 1.05,
        )

        val FAMILY_TYPE_MULTIPLIER: Map<String, Double> = mapOf(
            "1A"   to 1.00,
            "2A"   to 1.85,
            "2A1C" to 2.30,
            "2A2C" to 2.65,
            "2A3C" to 2.95,
            "1A1C" to 1.45,
            "1A2C" to 1.80,
            "1A3C" to 2.10,
        )

        /** Approx age-band escalation: 5-year bands, accelerating after 50. */
        fun ageBandMultiplier(ageBandMin: Int): Double = when {
            ageBandMin < 18  -> 0.55
            ageBandMin < 26  -> 0.80
            ageBandMin < 36  -> 1.00
            ageBandMin < 46  -> 1.30
            ageBandMin < 56  -> 1.80
            ageBandMin < 66  -> 2.55
            ageBandMin < 76  -> 3.45
            else             -> 4.40
        }

        /** SI scaling: ~sqrt-ish so doubling SI doesn't double premium. */
        fun sumInsuredMultiplier(si: Long): Double = when {
            si <=  1_000_000L -> 1.00
            si <=  2_500_000L -> 1.45
            si <=  5_000_000L -> 1.85
            si <= 10_000_000L -> 2.35
            si <= 50_000_000L -> 3.10
            else              -> 3.80
        }

        // ── Cover rates (copied shape from the engine test fixture) ──────────
        val COVER_RATES: Map<String, Double> = mapOf(
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
            CoverIds.HOME_CARE to 167.0,
            CoverIds.INFINITE_CLAIM to 0.04,
            CoverIds.RESTORATION_PLUS to 0.06,
            CoverIds.DONOR_PLUS to 417.0,
            CoverIds.SPOUSE_PROTECT to 0.05,
            CoverIds.DURABLE_MEDICAL to 0.04,
            CoverIds.TENURE_WISE to 0.025,
            CoverIds.SMART_SELECT to -0.15,
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
            CoverIds.ENHANCED_GEO to 0.1667,
        )

        val DISCOUNT_RATES: Map<String, Double> = mapOf(
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
            "${CoverIds.DISC_MULTI_MEMBER}_4+ members" to 0.10,
        )
    }
}
