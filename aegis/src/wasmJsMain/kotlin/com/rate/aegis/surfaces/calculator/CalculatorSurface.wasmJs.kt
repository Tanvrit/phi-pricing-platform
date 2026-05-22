package com.rate.aegis.surfaces.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing

/**
 * WASM CalculatorSurface — placeholder. The legacy desktop calculator UI uses
 * Ktor-CIO (JVM-only) and Compose Desktop window APIs. Reaching it from the
 * browser requires repackaging into commonMain + swapping in the Ktor JS client.
 * Tracked as the next iteration; for now we surface a clear "desktop only" notice.
 */
@Composable
actual fun CalculatorSurface() {
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text("Calculator", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Desktop only — for now",
            body = "The full operator calculator (with cover catalogue + configurator) " +
                    "currently runs on the Aegis desktop binary only. Migrating it into " +
                    "the web target is queued; until then, use the Mac/Windows operator " +
                    "build to author quotes."
        )
    }
}
