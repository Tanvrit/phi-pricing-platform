package com.rate.server.routes

import com.rate.domain.model.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Route.coverRoutes() {
    route("/api/covers") {
        get          { call.respond(coverDefinitions()) }
        get("/age-bands")    { call.respond(AGE_BANDS) }
        get("/family-types") {
            call.respond(FAMILY_TYPES.map { buildJsonObject {
                put("code",         it.code)
                put("adultCount",   it.adultCount)
                put("childCount",   it.childCount)
                put("totalMembers", it.totalMembers)
                put("isFloater",    it.isFloater)
            }})
        }
        get("/sum-insureds") {
            call.respond(buildJsonObject {
                put("domestic", JsonArray(DOMESTIC_SUM_INSUREDS.map { JsonPrimitive(it) }))
                put("global",   JsonArray(GLOBAL_SUM_INSUREDS.map   { JsonPrimitive(it) }))
            })
        }
    }
}

private fun coverDefinitions(): JsonArray = JsonArray(listOf(
    cover(CoverIds.DAY1_INSTANT,           "Day 1 Instant Increase of Cover",                     7,  p1 = "Coverage Multiple",  p1opts = listOf("1.5X","2X","3X","4X")),
    cover(CoverIds.LOYALTY_BONUS,          "Loyalty Bonus",                                        8,  p1 = "Bonus Option",       p1opts = listOf("100% upto 500%","100% upto 1000%","100% upto 200%")),
    cover(CoverIds.DOUBLE_COVER_7YR,       "Double Your Cover after 7 Years",                     9),
    cover(CoverIds.CHRONIC_INSTANT,        "Instant Hospitalisation for Chronic Conditions",      10, p1 = "Condition Count",    p1opts = listOf("Single condition","Two comorbid condition","Three comorbid condition")),
    cover(CoverIds.CONSUMABLES_LIST1,      "Consumables Cover - List I",                          11),
    cover(CoverIds.PED_WAITING,            "Modification of PED Waiting Period",                  12, p1 = "Reduction",          p1opts = listOf("3 to 2 Years","3 to 1 Year")),
    cover(CoverIds.SPECIFIC_ILLNESS_WAITING,"Modification of Specific Illness Waiting Period",    13),
    cover(CoverIds.MODERN_TREATMENT_PLUS,  "Modern Treatment Plus",                               14),
    cover(CoverIds.ROOM_RENT_MOD,          "Modification of Room Rent",                           15, p1 = "Room Type",          p1opts = listOf("General Room","Shared Room","Single Private AC Room","Any Room")),
    cover(CoverIds.DISEASE_SUBLIMIT,       "Diseases Specific Sub-limit Plus",                    16),
    cover(CoverIds.PRE_POST_HOSP,          "Pre-Post Hospitalisation: Lump Sum Payout",           17),
    cover(CoverIds.CONSUMABLE_PLUS,        "Consumable Plus Cover: List I to IV",                 18),
    cover(CoverIds.HOME_CARE,              "Home Care Treatment (Expert Health Specialist)",      19),
    cover(CoverIds.INFINITE_CLAIM,         "Infinite Claim",                                      20),
    cover(CoverIds.RESTORATION_PLUS,       "Restoration Plus",                                    21),
    cover(CoverIds.DONOR_PLUS,             "Donor Plus Cover",                                    22),
    cover(CoverIds.SPOUSE_PROTECT,         "Spouse Protect",                                      23),
    cover(CoverIds.DURABLE_MEDICAL,        "Durable Medical Equipment Cover",                     24),
    cover(CoverIds.TENURE_WISE,            "Tenure Wise",                                         25),
    cover(CoverIds.SMART_SELECT,           "Smart Select Network Discount (-15%)",                26, isDiscount = true),
    cover(CoverIds.GOOD_HEALTH,            "Incentivize Good Health Discount",                    27, isDiscount = true),
    cover(CoverIds.PER_CLAIM_DEDUCTIBLE,   "Per Claim Deductible",                                28, isDiscount = true, p1 = "Deductible",  p1opts = listOf("15000","25000")),
    cover(CoverIds.AGGREGATE_DEDUCTIBLE,   "Aggregate Deductible",                                29, isDiscount = true, p1 = "Deductible",  p1opts = listOf("25000","50000","100000")),
    cover(CoverIds.CO_PAY,                 "Co-Pay",                                              30, isDiscount = true, p1 = "Co-Pay Rate", p1opts = listOf("0.05","0.10","0.15","0.20","0.25","0.30","0.40","0.50","0.60")),
    cover(CoverIds.CHILD_PROTECT,          "Child Protect",                                       31),
    cover(CoverIds.DAILY_HOSPITAL_CASH,    "Daily Hospital Cash",                                 32, p1 = "Cash/Day (₹)",       p1opts = listOf("500","1000","1500","2000","2500","3000","4000","5000"), memberLevel = true),
    cover(CoverIds.CONVALESCENCE,          "Convalescence Benefit",                               33, p1 = "Amount",             p1opts = listOf("5000","10000","20000"), p2 = "Trigger", p2opts = listOf("More than 10 Days","More than 5 Days","More than 3 Days")),
    cover(CoverIds.COMPASSIONATE,          "Compassionate Benefit",                               34, p1 = "Amount",             p1opts = listOf("25000","50000")),
    cover(CoverIds.PERSONAL_ACCIDENT,      "Personal Accident",                                   35, p1 = "Coverage Amount",    p1opts = listOf("500000","1000000","2000000","3000000","5000000"), memberLevel = true, adultsOnly = true),
    cover(CoverIds.AIR_AMBULANCE,          "Air Ambulance",                                       36),
    cover(CoverIds.FITNESS_PLUS,           "Fitness Plus",                                        37),
    cover(CoverIds.WELLNESS_PACKAGE,       "Wellness Package",                                    38),
    cover(CoverIds.SECOND_OPINION,         "Second Opinion (International + Domestic)",           39),
    cover(CoverIds.MATERNITY_NEWBORN,      "Maternity & New Born Expense",                        40, p1 = "Benefit Limit",     p1opts = listOf("50000","100000","200000"), p2 = "Waiting Period", p2opts = listOf("9 Months","24 Months","36 Months","48 Months")),
    cover(CoverIds.POST_DELIVERY_CARE,     "Post Delivery Care",                                  41),
    cover(CoverIds.INFERTILITY,            "Infertility Cover",                                   42, p1 = "Benefit Limit",     p1opts = listOf("100000","200000"), p2 = "Waiting Period", p2opts = listOf("9 Months","24 Months","36 Months","48 Months")),
    cover(CoverIds.SURROGATE_MOTHER,       "In-patient Hospitalisation for Surrogate Mother",     43),
    cover(CoverIds.OOCYTE_DONOR,           "In-patient Hospitalisation for Oocyte Donor",         44),
    cover(CoverIds.POST_DISCHARGE_CARE,    "Post Discharge Care Guidance with Pru Doctor",        45),
    cover(CoverIds.ADVENTURE_SPORTS,       "Adventure Sports",                                    46, adultsOnly = true),
    cover(CoverIds.CHRONIC_MANAGEMENT,     "Chronic Management",                                  47, p1 = "Conditions",         p1opts = listOf("1","2","3"), memberLevel = true, adultsOnly = true),
    cover(CoverIds.FEMALE_VACCINATION,     "Female Vaccination",                                  48),
    cover(CoverIds.PRU_HEALTH_SPECIALIST,  "Pru Health Specialist",                               49),
    cover(CoverIds.ADVANCE_HEALTH_CHECKUP, "Advance Prudential Health Check-up",                  50, p1 = "Tier",               p1opts = listOf("Advance","Basic"), adultsOnly = true),
    cover(CoverIds.CASHLESS_OPD,           "Cashless OPD within Prudential Network",              51, p1 = "OPD Limit",          p1opts = listOf("2500","5000","10000")),
    cover(CoverIds.PRUDENTIAL_HEALTHY,     "Prudential Healthy Loading",                          52),
    cover(CoverIds.PREMIUM_RETURN,         "Premium Return",                                      53),
    cover(CoverIds.CRITICAL_ILLNESS,       "Critical Illness Cover",                              54, p1 = "Coverage Amount",    p1opts = listOf("500000","1000000","2000000","3000000"), memberLevel = true, adultsOnly = true),
    cover(CoverIds.MATERNITY_FIXED,        "Maternity Fixed Benefit",                             55, p1 = "Benefit Limit",     p1opts = listOf("50000","100000"), p2 = "Waiting Period", p2opts = listOf("0 Month","3 Months","6 Months","9 Months")),
    cover(CoverIds.ENHANCED_GEO,           "Enhanced Geographical Scope",                         56, p1 = "Geography",          p1opts = listOf("Worldwide excl. USA & CANADA","Asia excluding India","Europe")),
    cover(CoverIds.CANCER_BOOSTER,         "Cancer Booster",                                      57),
    cover(CoverIds.CANCER_SCREENING,       "Annual Screening for Cancer Diagnosed Patients",      58)
))

private fun cover(
    id: String, name: String, row: Int,
    isDiscount: Boolean = false, memberLevel: Boolean = false, adultsOnly: Boolean = false,
    p1: String? = null, p1opts: List<String> = emptyList(),
    p2: String? = null, p2opts: List<String> = emptyList()
): JsonObject = buildJsonObject {
    put("id", id); put("name", name); put("row", row)
    put("isDiscount", isDiscount); put("memberLevel", memberLevel); put("adultsOnly", adultsOnly)
    if (p1 != null) {
        put("param1Name", p1)
        put("param1Options", JsonArray(p1opts.map { JsonPrimitive(it) }))
    }
    if (p2 != null) {
        put("param2Name", p2)
        put("param2Options", JsonArray(p2opts.map { JsonPrimitive(it) }))
    }
}
