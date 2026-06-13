package com.rate.sdk.quoting.handler.doc

import kotlinx.serialization.Serializable

/**
 * IRDAI Master Circular on Health Insurance Business — 2024 edition.
 *
 * RELOCATED from the monolith (`com.rate.domain.regulatory.IrdaiMasterCircular2024`). The
 * mandates here apply to every approved health-insurance product issued in India. This object
 * is the single source of truth for "what does the circular require us to do"; the IRDAI doc
 * builders in this package reference these constants so a future amendment (IRDAI publishes one
 * annually) lands in one place.
 *
 * Numeric defaults that admins can also tune live in [com.rate.core.regulatory.Irdai]; this
 * object carries the broader, doc-builder-facing circular text + compliance audit.
 */
object IrdaiCircular2024 {

    /** AYUSH (Ayurveda / Yoga / Unani / Siddha / Homeopathy) treatment must be covered at par. */
    const val MANDATORY_AYUSH_COVERAGE: Boolean = true

    /** Mental-illness treatment must be covered at par with physical illness (MHCA 2017). */
    const val MANDATORY_MENTAL_HEALTH_PARITY: Boolean = true

    /** HIV/AIDS must be covered (no blanket exclusion permitted). */
    const val MANDATORY_HIV_AIDS_COVERAGE: Boolean = true

    /** Free-look period: 15 days for online policies (30 days when sold via distance marketing). */
    const val FREE_LOOK_DAYS_ONLINE: Int = 15
    const val FREE_LOOK_DAYS_DISTANCE_MARKETING: Int = 30

    /** Grace period for renewal: 30 days for individual health, 15 days for group. */
    const val GRACE_PERIOD_INDIVIDUAL_DAYS: Int = 30
    const val GRACE_PERIOD_GROUP_DAYS: Int = 15

    /** Maximum revival window after lapse: 2 years (90 days for standard products). */
    const val REVIVAL_WINDOW_YEARS: Int = 2

    /** PED waiting period: maximum 36 months (3 years). May be reduced via paid rider. */
    const val PED_MAX_WAITING_MONTHS: Int = 36

    /** Specific-illness waiting: maximum 24 months (2 years). */
    const val SPECIFIC_ILLNESS_MAX_WAITING_MONTHS: Int = 24

    /** Initial waiting period after first issue: 30 days (sickness; accidents covered from day 1). */
    const val INITIAL_WAITING_DAYS_SICKNESS: Int = 30

    /** Cumulative NCB cap. Industry standard 50%; spec confirmed by Chief Actuary D-11. */
    const val MAX_NCB_PERCENT: Double = 0.50

    /** Cumulative NCB per claim-free year. */
    const val NCB_PER_YEAR_PERCENT: Double = 0.05

    /** Modern treatment list — must be covered per Annexure II of the circular. */
    val MODERN_TREATMENTS_REQUIRED: List<String> = listOf(
        "Uterine Artery Embolization and HIFU",
        "Balloon Sinuplasty",
        "Deep Brain stimulation",
        "Oral chemotherapy",
        "Immunotherapy - Monoclonal Antibody",
        "Intra vitreal injections",
        "Robotic surgeries",
        "Stereotactic radio surgeries",
        "Bronchial Thermoplasty",
        "Vaporisation of the prostrate (Green laser / Holmium laser)",
        "IONM (Intra Operative Neuro Monitoring)",
        "Stem cell therapy (haematopoietic)",
    )

    /** Audit result: compliance violations + warnings against a given plan + cover list. */
    @Serializable
    data class ComplianceCheck(
        val planId: String,
        val violations: List<String>,
        val warnings: List<String>,
    ) {
        val isCompliant: Boolean get() = violations.isEmpty()
    }

    /**
     * Audit a plan's cover list against the 2024 Circular mandates.
     *
     * Returns a [ComplianceCheck] describing every violation (must fix before Live) and warning
     * (should fix). Used by the operator Plan Configurator's pre-publish gate. [coverIds] are the
     * catalog Cover `code`s the plan ships.
     */
    fun audit(planId: String, coverIds: Set<String>): ComplianceCheck {
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if ("cover_ayush" !in coverIds) {
            violations += "AYUSH coverage missing (IRDAI Master Circular 2024 §Annexure I). " +
                "Add a `cover_ayush` cover to the plan's covers."
        }
        if ("cover_mental_health" !in coverIds) {
            violations += "Mental-health parity missing (Mental Healthcare Act 2017 + IRDAI 2024). " +
                "Add a `cover_mental_health` cover."
        }
        if ("cover_hiv_aids" !in coverIds) {
            violations += "HIV/AIDS coverage missing (IRDAI 2024 mandate). Add a `cover_hiv_aids` cover."
        }
        if ("modern_treatment_plus" !in coverIds) {
            warnings += "Modern Treatment Plus is not enabled by default. Per Annexure II of the " +
                "Master Circular, the 12 listed modern treatments must be covered (either via this " +
                "cover or built into the base plan)."
        }
        return ComplianceCheck(planId, violations, warnings)
    }
}

/**
 * Maps engine/business plan ids → IRDAI Unique Identification Numbers (UINs). Until F&U (File
 * and Use) values land, entries carry `TBD-` placeholders so audits surface the gap immediately.
 * The persisted, admin-managed copies are [com.rate.core.regulatory.Uin] rows in the catalog;
 * this in-code registry is the doc-builder fallback when a Plan carries no UIN yet.
 *
 * RELOCATED from the monolith's `com.rate.domain.regulatory.UinRegistry`.
 */
object UinRegistry {

    private val PLAN_UINS: Map<String, String> = mapOf(
        "PHI_BASIC" to "TBD-UIN-PHI_BASIC",
        "PHI_FLAGSHIP1" to "TBD-UIN-PHI_FLAGSHIP1",
        "PHI_FLAGSHIP2" to "TBD-UIN-PHI_FLAGSHIP2",
        "PHI_FLAGSHIP3" to "TBD-UIN-PHI_FLAGSHIP3",
        "PHI_SENIOR" to "TBD-UIN-PHI_SENIOR",
        "PHI_SUBSTANDARD" to "TBD-UIN-PHI_SUBSTANDARD",
        "PHI_POSP" to "TBD-UIN-PHI_POSP",
        "PHI_GLOBAL1" to "TBD-UIN-PHI_GLOBAL1",
        "PHI_GLOBAL2" to "TBD-UIN-PHI_GLOBAL2",
        "PHI_GLOBAL_PLUS1" to "TBD-UIN-PHI_GLOBAL_PLUS1",
    )

    /** UIN string for a plan id, or a `TBD-UIN-<planId>` placeholder when unknown. */
    fun forPlan(planId: String): String = PLAN_UINS[planId] ?: "TBD-UIN-$planId"

    /** True if the resolved UIN is still a placeholder (drives the audit "still TBD" surface). */
    fun isPlaceholder(planId: String): Boolean = forPlan(planId).startsWith("TBD")
}
