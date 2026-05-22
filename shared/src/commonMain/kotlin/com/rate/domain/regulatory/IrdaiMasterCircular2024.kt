package com.rate.domain.regulatory

import kotlinx.serialization.Serializable

/**
 * IRDAI Master Circular on Health Insurance Business — 2024 edition.
 *
 * The mandates here apply to every approved health-insurance product issued in India.
 * This object is the single source of truth for "what does the circular require us to
 * do"; product-side code references these flags so a future amendment (which IRDAI
 * publishes annually) lands in one place.
 *
 * Phase 7 of the audit roadmap requires the engine + Aegis to verify a plan is
 * Circular-compliant before allowing it to be published Live.
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

    /** Initial waiting period after first issue: 30 days (sickness; accidents are covered from day 1). */
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
        "Stem cell therapy (haematopoietic)"
    )

    /** Audit returns a list of compliance violations against a given plan + cover list. */
    @Serializable
    data class ComplianceCheck(
        val planId: String,
        val violations: List<String>,
        val warnings: List<String>
    ) {
        val isCompliant: Boolean get() = violations.isEmpty()
    }

    /**
     * Audit a plan's cover list against the 2024 Circular mandates.
     *
     * Returns a [ComplianceCheck] describing every violation (must fix before Live) and
     * warning (should fix). Used by Aegis Plan Configurator's pre-publish gate.
     */
    fun audit(planId: String, coverIds: Set<String>): ComplianceCheck {
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // AYUSH parity — we don't yet have a `cover_ayush` cover ID in CoverCatalog,
        // so any plan that ships without one is non-compliant.
        if ("cover_ayush" !in coverIds) {
            violations += "AYUSH coverage missing (IRDAI Master Circular 2024 §Annexure I). " +
                "Add a `cover_ayush` cover to the plan's allowedCoverIds."
        }

        if ("cover_mental_health" !in coverIds) {
            violations += "Mental-health parity missing (Mental Healthcare Act 2017 + IRDAI 2024). " +
                "Add a `cover_mental_health` cover."
        }

        if ("cover_hiv_aids" !in coverIds) {
            violations += "HIV/AIDS coverage missing (IRDAI 2024 mandate). " +
                "Add a `cover_hiv_aids` cover."
        }

        // Modern-treatment cover exists in CoverCatalog (`modern_treatment_plus`) but is
        // typically off-by-default on PHI Basic. Warn rather than block.
        if ("modern_treatment_plus" !in coverIds) {
            warnings += "Modern Treatment Plus is not enabled by default. " +
                "Per Annexure II of the Master Circular, the 12 listed modern treatments " +
                "must be covered (either via this cover or built into the base plan)."
        }

        return ComplianceCheck(planId, violations, warnings)
    }
}
