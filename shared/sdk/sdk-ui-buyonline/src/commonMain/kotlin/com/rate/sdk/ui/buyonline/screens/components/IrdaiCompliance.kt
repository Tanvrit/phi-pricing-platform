package com.rate.sdk.ui.buyonline.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.kit.brand.*

/**
 * IRDAI-compliance UI fragments that every PHI customer journey must surface.
 *
 * Pre-Foundation-Pack the buy-online surfaced none of this. IRDAI Health Insurance
 * Regulations 2016 + the Master Circular 2024 require:
 *
 *  - The insurer's IRDAI registration number on every customer-facing surface
 *  - A free-look period disclosure (15 days for online policies)
 *  - The IRDAI Ombudsman contact / grievance redressal mechanism
 *  - Real (auditable) claim-settlement-ratio disclosure with citation
 *  - Prospectus / CIS / Policy Wording downloads
 *
 * These composables are intentionally small and parameterised — the actual UIN,
 * IRDAI reg number, ombudsman URL, etc. live in Settings and get injected here
 * once Phase 7 / 9 lands. For now they hold the placeholder values that make the
 * UI honest and the gap visible.
 */

/** Hard-coded IRDAI registration metadata — replace with real values once F&U closes. */
object IrdaiRegistrationInfo {
    const val INSURER_NAME = "PRU Health Insurance Co. Ltd."
    /** IRDAI assigns this when the insurer first registers. Placeholder until known. */
    const val IRDAI_REG_NUMBER = "TBD-IRDAI-2026-XXX"
    /** Each approved product has its own UIN. Placeholder until F&U closes per product. */
    const val DEFAULT_UIN = "TBD-UIN-XXXX"
    const val OMBUDSMAN_URL = "https://www.cioins.co.in/"
    const val GRIEVANCE_EMAIL = "grievance@example.com"
    const val GRIEVANCE_PHONE = "1800-XXX-XXXX"
    /**
     * Per IRDAI Annual Report — must cite source + period. Until the insurer has
     * an actual settlement number to cite, this returns null and the UI hides the
     * line. We refuse to display unverified marketing claims.
     */
    val claimSettlementRatio: String? = null
    const val FREE_LOOK_DAYS = 15
}

/**
 * Compact 1-line trust strip — meant for the bottom of any customer screen.
 * Renders nothing for verified ratios that haven't been wired yet (no fake numbers).
 */
@Composable
fun IrdaiTrustStrip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(PruInfoBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            buildString {
                append("IRDAI Registration ${IrdaiRegistrationInfo.IRDAI_REG_NUMBER}  •  ")
                append("${IrdaiRegistrationInfo.FREE_LOOK_DAYS}-day free-look  •  ")
                append("Grievance: ${IrdaiRegistrationInfo.GRIEVANCE_EMAIL}")
            },
            fontSize = 11.sp, color = PruSubtext
        )
    }
}

/**
 * Full IRDAI-compliance footer — required at the bottom of Landing, Plan Summary,
 * Payment, and Application Complete screens.
 */
@Composable
fun IrdaiComplianceFooter(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F8), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            IrdaiRegistrationInfo.INSURER_NAME,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PruText
        )
        Text(
            "IRDAI Registration No. ${IrdaiRegistrationInfo.IRDAI_REG_NUMBER}",
            fontSize = 11.sp, color = PruSubtext
        )
        Text(
            "Health insurance attracts 18% GST (HSN 9971). " +
                    "Premiums paid may qualify for tax benefit under Section 80D of the " +
                    "Income Tax Act — consult your tax advisor.",
            fontSize = 11.sp, color = PruSubtext
        )
        Text(
            "Free-Look Period: ${IrdaiRegistrationInfo.FREE_LOOK_DAYS} days from receipt of policy " +
                    "(IRDAI Health Insurance Regulations 2016). " +
                    "You may cancel for a refund minus risk premium for days enjoyed + admin charges.",
            fontSize = 11.sp, color = PruSubtext
        )
        Text(
            "Grievance Redressal: ${IrdaiRegistrationInfo.GRIEVANCE_EMAIL} • " +
                    "${IrdaiRegistrationInfo.GRIEVANCE_PHONE}. " +
                    "Unresolved? Insurance Ombudsman: ${IrdaiRegistrationInfo.OMBUDSMAN_URL}",
            fontSize = 11.sp, color = PruSubtext
        )
        // Claim settlement ratio shown ONLY if it has been wired with a real source.
        // We refuse to invent a number.
        IrdaiRegistrationInfo.claimSettlementRatio?.let { ratio ->
            Text(
                "Claim Settlement Ratio: $ratio (Source: IRDAI Annual Report).",
                fontSize = 11.sp, color = PruSubtext
            )
        }
        Text(
            "Important: This summary is indicative. Full coverage, exclusions, waiting periods, " +
                    "and conditions are in the Policy Wording (UIN: ${IrdaiRegistrationInfo.DEFAULT_UIN}). " +
                    "Insurance is a subject matter of solicitation.",
            fontSize = 11.sp, color = PruSubtext
        )
    }
}
