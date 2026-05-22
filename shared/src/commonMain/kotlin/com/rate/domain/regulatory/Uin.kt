package com.rate.domain.regulatory

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * IRDAI Unique Identification Number (UIN) — a 4-15 character regulatory ID assigned to
 * every approved insurance product. Format conventionally looks like `2026PHI001V01`
 * (year + product code + version). Each approved variant (Domestic / Flagship / Senior /
 * Global etc.) is its own product with its own UIN.
 *
 * This registry maps the engine's plan IDs (e.g. `PHI_BASIC`) to their regulatory UINs.
 * Until Phase 7 lands real values from F&U (File and Use), entries carry placeholder
 * UINs marked with the `TBD` prefix so audits surface the gap immediately.
 */
@Serializable
data class Uin(
    /** The UIN string itself. e.g. "2026PHI001V01" or "TBD-UIN-PHI_BASIC". */
    val value: String,
    /** Human-readable product name as it appears on the prospectus. */
    val productName: String,
    /** Engine plan ID this UIN corresponds to (e.g. "PHI_BASIC"). */
    val planId: String,
    /** When the UIN was approved by IRDAI. Null if still placeholder. */
    val approvedAt: Instant? = null,
    /** Retirement date (when the product is no longer sold). Null if active. */
    val retiresAt: Instant? = null,
    /** True if this is an IRDAI-standard product (Arogya Sanjeevani, Corona Kavach etc.). */
    val isStandardProduct: Boolean = false
) {
    val isPlaceholder: Boolean get() = value.startsWith("TBD")
    val isActive: Boolean get() = !isPlaceholder && approvedAt != null && retiresAt == null
}

/**
 * Singleton registry mapping engine plan IDs → UINs. Phase 7 wires this to a DB table
 * (`uin_registry`) editable from Aegis Settings. For now it lives in code with TBD
 * placeholders so the gap is visible at audit time.
 */
object UinRegistry {

    val ALL: List<Uin> = listOf(
        Uin("TBD-UIN-PHI_BASIC",         "PRU Health Basic",         "PHI_BASIC"),
        Uin("TBD-UIN-PHI_FLAGSHIP1",     "PRU Health Flagship 1",    "PHI_FLAGSHIP1"),
        Uin("TBD-UIN-PHI_FLAGSHIP2",     "PRU Health Flagship 2",    "PHI_FLAGSHIP2"),
        Uin("TBD-UIN-PHI_FLAGSHIP3",     "PRU Health Flagship 3",    "PHI_FLAGSHIP3"),
        Uin("TBD-UIN-PHI_SENIOR",        "PRU Health Senior",        "PHI_SENIOR"),
        Uin("TBD-UIN-PHI_SUBSTANDARD",   "PRU Health Sub-Standard",  "PHI_SUBSTANDARD"),
        Uin("TBD-UIN-PHI_POSP",          "PRU Health POSP",          "PHI_POSP"),
        Uin("TBD-UIN-PHI_GLOBAL1",       "PRU Health Global",        "PHI_GLOBAL1"),
        Uin("TBD-UIN-PHI_GLOBAL2",       "PRU Health Global Plus",   "PHI_GLOBAL2"),
        Uin("TBD-UIN-PHI_GLOBAL_PLUS1",  "PRU Health Global Plus 1", "PHI_GLOBAL_PLUS1")
    )

    fun forPlan(planId: String): Uin? = ALL.firstOrNull { it.planId == planId }

    /**
     * Returns the placeholder UINs — Phase 7 readiness check uses this to surface
     * "still TBD" entries on the Aegis Audit & Governance surface.
     */
    fun placeholders(): List<Uin> = ALL.filter { it.isPlaceholder }
}
