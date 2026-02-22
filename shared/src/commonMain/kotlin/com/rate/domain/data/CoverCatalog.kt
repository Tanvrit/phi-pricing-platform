package com.rate.domain.data

/**
 * UI metadata for every cover and discount, derived from the Excel Rate_Calculator_v7.0.xlsm.
 * Used by the desktop calculator and buy-online journey to build the cover selection UI
 * without needing a server API call.
 */
data class ParamDef(val name: String, val options: List<String>)

data class CoverMeta(
    val id: String,
    val name: String,
    val description: String,
    val isDiscount: Boolean = false,
    val param1: ParamDef? = null,
    val param2: ParamDef? = null,
    /** All Excel display-name variants that map to this cover (used by ExcelImporter for Sheet1 parsing) */
    val xlsNames: List<String> = emptyList()
)

object CoverCatalog {

    val ALL: List<CoverMeta> = listOf(

        // ── Accumulating % covers (Excel rows 7-30) ───────────────────────

        CoverMeta("day1_instant", "Day 1 Instant Hospitalisation",
            "Covers pre-existing diseases from day 1 at a multiple of sum insured",
            param1 = ParamDef("Coverage Multiple", listOf("1.5X", "2X", "3X", "4X")),
            xlsNames = listOf("Day 1 Instant Increase of Cover (2X)", "Day 1 Instant Increase of Cover")
        ),
        CoverMeta("loyalty_bonus", "Loyalty Bonus",
            "Increases sum insured every year at no extra cost",
            param1 = ParamDef("Bonus Type", listOf("100% upto 200%", "100% upto 500%", "100% upto 1000%")),
            xlsNames = listOf("Loyalty Bonus")
        ),
        CoverMeta("double_cover_7yr", "Double Your Cover after 7 Years",
            "Sum insured doubled automatically after 7 continuous renewal years",
            xlsNames = listOf("Double your Cover after 7 Years")
        ),
        CoverMeta("chronic_instant", "Instant Hospitalisation for Chronic Conditions",
            "Covers hospitalisation for chronic conditions from day 1",
            param1 = ParamDef("Conditions", listOf("Single condition", "Two comorbid condition", "Three comorbid condition")),
            xlsNames = listOf("Instant Hospitalization Cover for Chronic Conditions", "Instant Hospitalisation Cover for Chronic Conditions")
        ),
        CoverMeta("consumables_list1", "Consumables Cover – List I",
            "Covers consumables from List I (gloves, syringes, etc.): +5%",
            xlsNames = listOf("Consumables Cover - List I")
        ),
        CoverMeta("ped_waiting", "Modification of PED Waiting Period",
            "Reduces pre-existing disease waiting period from 3 years",
            param1 = ParamDef("New Waiting Period", listOf("3 to 2 Years", "3 to 1 Year")),
            xlsNames = listOf("Modification of PED Waiting Period")
        ),
        CoverMeta("specific_illness_waiting", "Modification of Specific Illness Waiting Period",
            "Removes specific disease waiting period (rate varies by age band)",
            xlsNames = listOf("Modification of Specific Illness Waiting Period")
        ),
        CoverMeta("modern_treatment_plus", "Modern Treatment Plus",
            "Enhanced coverage for modern medical treatments",
            xlsNames = listOf("Modern Treatment Plus")
        ),
        CoverMeta("room_rent_mod", "Room Rent Modification",
            "Modify the default room rent entitlement",
            param1 = ParamDef("Room Type", listOf("General Room", "Shared Room", "Single Private AC", "Any Room")),
            xlsNames = listOf("Modification of Room Rent")
        ),
        CoverMeta("disease_sublimit", "Diseases Specific Sub-limit Plus",
            "Removes disease-specific sub-limits (PHI Basic only): +6.47%",
            xlsNames = listOf("Diseases Specific Sub-limit Plus")
        ),
        CoverMeta("pre_post_hosp", "Pre-Post Hospitalisation Lump Sum",
            "Lump sum for expenses before and after hospitalisation: +10%",
            xlsNames = listOf("Pre-Post Hospitalisation : Lump Sum Payout", "Pre-Post Hospitalisation: Lump Sum Payout")
        ),
        CoverMeta("consumable_plus", "Consumable Plus – List I to IV",
            "Covers consumables across all four lists: +9%",
            xlsNames = listOf("Consumable Plus Cover: List I to IV")
        ),
        CoverMeta("home_care", "Home Care Treatment",
            "Covers home care treatment: ₹167 flat per year",
            xlsNames = listOf("Home Care Treatment (Expert Health Specialist)")
        ),
        CoverMeta("infinite_claim", "Infinite Claim",
            "Unlimited claim restoration during the policy year",
            xlsNames = listOf("Infinite Claim")
        ),
        CoverMeta("restoration_plus", "Restoration Plus",
            "Enhanced restoration — restores sum insured for same illness",
            xlsNames = listOf("Restoration Plus")
        ),
        CoverMeta("donor_plus", "Donor Plus Cover",
            "Covers donor expenses: ₹417 per member per year",
            xlsNames = listOf("Donor Plus Cover")
        ),
        CoverMeta("spouse_protect", "Spouse Protect",
            "Extends specific benefits to spouse",
            xlsNames = listOf("Spouse Protect")
        ),
        CoverMeta("durable_medical", "Durable Medical Equipment",
            "Covers durable medical equipment costs",
            xlsNames = listOf("Durable Medical Equipment Cover")
        ),
        CoverMeta("tenure_wise", "Tenure Wise Optional Benefit",
            "Additional cover loading for 3+ year policies",
            xlsNames = listOf("Tenure Wise")
        ),
        CoverMeta("smart_select", "Smart Select Network Discount",
            "Discount for using Prudential's preferred provider network: -15%",
            isDiscount = true,
            xlsNames = listOf("Smart Select Network Discount (Pru Select Hospital)", "Smart Select Network Discount")
        ),
        CoverMeta("good_health", "Incentivise Good Health",
            "Loyalty reward for maintaining good health — 0% loading currently",
            isDiscount = true,
            xlsNames = listOf("Incentivize Good Health Discount")
        ),
        CoverMeta("per_claim_deductible", "Per Claim Deductible",
            "You bear a fixed amount per claim in exchange for lower premium",
            isDiscount = true,
            param1 = ParamDef("Deductible Amount", listOf("15000", "25000")),
            xlsNames = listOf("Per Claim Deductible")
        ),
        CoverMeta("aggregate_deductible", "Aggregate Deductible",
            "You bear a fixed total amount across all claims per year",
            isDiscount = true,
            param1 = ParamDef("Deductible Amount", listOf("25000", "50000", "100000")),
            xlsNames = listOf("Aggregate Deductible")
        ),
        CoverMeta("co_pay", "Co-Payment",
            "You pay a fixed percentage of each claim",
            isDiscount = true,
            param1 = ParamDef("Co-Pay %", listOf("0.05", "0.10", "0.15", "0.20", "0.25", "0.30", "0.40", "0.50", "0.60")),
            xlsNames = listOf("Co-Pay", "Co-Payment")
        ),

        // ── Flat / member-level covers (Excel rows 31-51) ─────────────────

        CoverMeta("child_protect", "Child Protect",
            "Additional protection for children in the family: ₹100 flat",
            xlsNames = listOf("Child Protect")
        ),
        CoverMeta("daily_hospital_cash", "Daily Hospital Cash",
            "Fixed daily cash benefit during hospitalisation",
            param1 = ParamDef("Cash Limit per Day (₹)", listOf("500", "1000", "1500", "2000", "2500", "3000", "4000", "5000", "7000", "8000", "9000", "10000")),
            xlsNames = listOf("Daily Hospital Cash")
        ),
        CoverMeta("convalescence", "Convalescence Benefit",
            "Lump sum payment for extended hospital stay",
            param1 = ParamDef("Benefit Amount (₹)", listOf("5000", "10000", "20000")),
            param2 = ParamDef("Trigger (Days)", listOf("More than 10 Days", "More than 5 Days", "More than 3 Days")),
            xlsNames = listOf("Convalescence Benefit")
        ),
        CoverMeta("compassionate", "Compassionate Benefit",
            "Lump sum for family travel during critical hospitalisation",
            param1 = ParamDef("Benefit Amount (₹)", listOf("25000", "50000")),
            xlsNames = listOf("Compassionate Benefit")
        ),
        CoverMeta("personal_accident", "Personal Accident Cover",
            "Personal accident cover for adult members",
            param1 = ParamDef("Coverage Amount (₹)", listOf("1000000", "2000000", "3000000", "5000000", "10000000")),
            xlsNames = listOf("Personal Accident")
        ),
        CoverMeta("air_ambulance", "Air Ambulance",
            "Covers air ambulance charges: ₹433 flat per year",
            xlsNames = listOf("Air Ambulance")
        ),
        CoverMeta("fitness_plus", "Fitness Plus",
            "Fitness benefit and gym membership reimbursement: ₹649 flat",
            xlsNames = listOf("Fitness Plus Benefit")
        ),
        CoverMeta("wellness_package", "Wellness Package",
            "Wellness services package (currently complimentary)",
            xlsNames = listOf("Wellness Package")
        ),
        CoverMeta("second_opinion", "Second E-Opinion",
            "International + domestic specialist second opinion: ₹67 flat",
            xlsNames = listOf("Second E-opinion (International + Domestic)", "Second Opinion")
        ),
        CoverMeta("maternity_newborn", "Maternity & New Born Expenses",
            "Covers maternity and newborn hospitalisation expenses",
            param1 = ParamDef("Cover Amount (₹)", listOf("50000", "100000", "200000")),
            param2 = ParamDef("Waiting Period", listOf("9 Months", "24 Months", "36 Months", "48 Months")),
            xlsNames = listOf("Maternity & New Born Expense", "Maternity & New Born Expenses")
        ),
        CoverMeta("post_delivery_care", "Post Delivery Care Guidance",
            "Post-delivery care and guidance service: ₹167 flat",
            xlsNames = listOf("Post Delivery Care")
        ),
        CoverMeta("infertility", "Infertility Cover",
            "Covers infertility treatment expenses",
            param1 = ParamDef("Cover Amount (₹)", listOf("100000", "200000")),
            param2 = ParamDef("Waiting Period", listOf("9 Months", "24 Months", "36 Months", "48 Months")),
            xlsNames = listOf("Infertility Cover")
        ),
        CoverMeta("surrogate_mother", "Surrogate Mother Hospitalisation",
            "Covers surrogate mother's hospitalisation expenses: ₹908 flat (3yr+ policy)",
            xlsNames = listOf(
                "In patient Hospitalisation for Surrogate Mother (Cover for 36 Months)",
                "In-patient Hospitalisation for Surrogate Mother"
            )
        ),
        CoverMeta("oocyte_donor", "Oocyte (Egg) Donor Cover",
            "Covers oocyte donor treatment expenses: ₹845.75 flat (12 months)",
            xlsNames = listOf(
                "In patient Hospitalisation for Oocyte (Egg) Donor (Cover for 12 months)",
                "In-patient Hospitalisation for Oocyte Donor"
            )
        ),
        CoverMeta("post_discharge_care", "Post Discharge Care Guidance",
            "Post-discharge care guidance service: ₹167 flat",
            xlsNames = listOf("Post Discharge Care Guidance with Pru Doctor")
        ),
        CoverMeta("adventure_sports", "Adventure Sports Cover",
            "Covers hospitalisation due to adventure sports injuries: ₹183 per adult",
            xlsNames = listOf("Adventure Sports Cover")
        ),
        CoverMeta("chronic_management", "Chronic Management Program",
            "Covers chronic disease management for enrolled members",
            param1 = ParamDef("Conditions", listOf("1", "2", "3")),
            xlsNames = listOf("Chronic Management")
        ),
        CoverMeta("female_vaccination", "Female Vaccination",
            "Covers vaccination for female members: ₹3,333 per female",
            xlsNames = listOf("Female Vaccination")
        ),
        CoverMeta("pru_health_specialist", "Pru Health Specialist Consultation",
            "Access to Prudential Health specialist network: ₹83 flat",
            xlsNames = listOf(
                "Pru Health Specialist (Dedicated Pru Health Specialist for entire Family)",
                "Pru Health Specialist"
            )
        ),
        CoverMeta("advance_health_checkup", "Advance Prudential Health Check-up",
            "Comprehensive health check-up for adult members",
            param1 = ParamDef("Tier", listOf("Advance", "Basic")),
            xlsNames = listOf("Advance Prudential Health Check-up")
        ),
        CoverMeta("cashless_opd", "Cashless OPD – Prudential Network",
            "Cashless outpatient treatment within Prudential network",
            param1 = ParamDef("OPD Limit (₹)", listOf("2500", "5000", "10000")),
            xlsNames = listOf("Cashless OPD within Prudential Network")
        ),

        // ── Pre-calculated covers ─────────────────────────────────────────

        CoverMeta("critical_illness", "Critical Illness Cover",
            "Lump sum payout on diagnosis of critical illness (per adult member)",
            param1 = ParamDef("Coverage Amount (₹)", listOf("500000", "1000000", "2000000", "3000000", "5000000")),
            xlsNames = listOf("Critical Illness Cover")
        ),
        CoverMeta("maternity_fixed", "Maternity Fixed Benefit",
            "Fixed benefit maternity cover (requires 3yr+ single premium policy)",
            param1 = ParamDef("Benefit Amount (₹)", listOf("50000", "100000")),
            param2 = ParamDef("Waiting Period", listOf("0 Month", "3 Months", "6 Months", "9 Months")),
            xlsNames = listOf("Maternity Cover Fixed Benefit", "Maternity Fixed Benefit")
        ),
        CoverMeta("cancer_booster", "Cancer Booster",
            "Enhanced cancer treatment coverage: +2.5% of accumulated base",
            xlsNames = listOf("Cancer Booster")
        ),
        CoverMeta("cancer_screening", "Annual Cancer Screening",
            "Annual cancer screening benefit for members",
            xlsNames = listOf(
                "Annual Screeninig Package For Cancer Diagnosed Patients",
                "Annual Screening Package For Cancer Diagnosed Patients"
            )
        ),

        // ── Post covers ───────────────────────────────────────────────────

        CoverMeta("prudential_healthy", "Prudential Healthy Discount",
            "Discount for maintaining a healthy lifestyle: +0.75% (offset by healthy behaviour)",
            xlsNames = listOf("Prudential Healthy Discount", "Prudential Healthy Loading")
        ),
        CoverMeta("premium_return", "Premium Return Benefit",
            "Returns a portion of premium on claim-free years",
            xlsNames = listOf("Premium Return")
        ),
        CoverMeta("enhanced_geo", "Enhanced Geographical Scope",
            "Extends coverage to international territories (Global plans only)",
            param1 = ParamDef("Coverage Geography", listOf(
                "Worldwide excl. USA & Canada",
                "Asia excluding India",
                "Europe",
                "Worldwide incl. USA & Canada"
            )),
            xlsNames = listOf(
                "Enhanced Geographical Scope for International Coverage",
                "Enhanced Geographical Scope"
            )
        )
    )

    val DISCOUNTS: List<CoverMeta> = listOf(
        CoverMeta("disc_employee", "Employee / Affiliate Discount",
            "10% discount for employees and affiliates",
            isDiscount = true
        ),
        CoverMeta("disc_nri", "NRI Discount",
            "15% discount for Non-Resident Indians",
            isDiscount = true
        ),
        CoverMeta("disc_auto_debit", "Auto-Debit Discount",
            "2.5% discount for auto-debit payment setup",
            isDiscount = true
        ),
        CoverMeta("disc_commission_lieu", "Discount in Lieu of Commission",
            "15% discount (cannot combine with Employee discount)",
            isDiscount = true
        ),
        CoverMeta("disc_gmc", "Corporate GMC Discount",
            "5% discount for group medical cover policyholders",
            isDiscount = true
        ),
        CoverMeta("disc_cibil", "CIBIL Score Discount",
            "Up to 15% discount based on CIBIL credit score",
            isDiscount = true,
            param1 = ParamDef("CIBIL Score Range", listOf("<=700", "701-750", "751-800", "801-849", ">=850"))
        ),
        CoverMeta("disc_tenure", "Multi-Year Discount",
            "Discount for multi-year single premium policy (7.5%-15%)",
            isDiscount = true
        ),
        CoverMeta("disc_multi_member", "Multi-Member Discount",
            "5% discount for 2-3 members, 10% for 4+ members",
            isDiscount = true,
            param1 = ParamDef("Member Count", listOf("2-3 members", "4+ members"))
        ),
    )

    fun findById(id: String): CoverMeta? = ALL.firstOrNull { it.id == id }
        ?: DISCOUNTS.firstOrNull { it.id == id }
}
