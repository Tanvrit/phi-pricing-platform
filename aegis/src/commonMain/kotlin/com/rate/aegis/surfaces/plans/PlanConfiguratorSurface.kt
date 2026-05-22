package com.rate.aegis.surfaces.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.business.calculator.ui.configurator.ConfiguratorBody
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing

/**
 * Plan Configurator surface for the Aegis BUSINESS shell. Wraps the existing
 * [ConfiguratorBody] (real `client.getPlans()` + `client.savePlan()` underneath)
 * with the Aegis chrome — no nested bottom-nav, no second top bar.
 *
 * Saved edits round-trip through the server so they're visible to the dashboard
 * within one auto-refresh tick (≤ 30s).
 */
@Composable
fun PlanConfiguratorSurface() {
    val client = remember { ApiClient() }
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text("Plan Configurator", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AegisColors.textBody)
        Text(
            "Edits are sent to the server via `POST /api/plans` — they show up on the Home dashboard within one auto-refresh tick (~30s).",
            fontSize = 13.sp, color = AegisColors.textSecondary
        )
        ConfiguratorBody(client)
    }
}
