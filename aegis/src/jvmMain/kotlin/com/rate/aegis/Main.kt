package com.rate.aegis

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.i18n.detectHostLocale
import com.rate.aegis.settings.AegisSettingsStore
import kotlinx.coroutines.delay

/**
 * Aegis desktop entry. Defaults to BUSINESS role — the role can be overridden
 * with `-Daegis.role=CUSTOMER|BUSINESS|ADMIN` for testing.
 *
 * Customer-facing buyonline is served from the WASM target via Cloudflare Pages;
 * this JVM binary is the operator/admin app distributed to PRUHealth staff.
 */
fun main() {
    // Resolution order: -Daegis.role override → operator-saved defaultRole → BUSINESS.
    // A corrupt/unknown saved value (e.g. older build wrote "OPS") silently degrades
    // to BUSINESS rather than crashing the desktop binary on launch.
    val role = System.getProperty("aegis.role")?.let {
        runCatching { AegisRole.valueOf(it.uppercase()) }.getOrNull()
    } ?: run {
        val saved = AegisSettingsStore.load().defaultRole
        runCatching { AegisRole.valueOf(saved.uppercase()) }.getOrElse { AegisRole.BUSINESS }
    }

    // Buyonline save+resume: mirror the WASM `?session=` query support via a
    // JVM system property so operators can reproduce a customer's stuck state
    // by launching with `-Daegis.session=<hex>` for debugging.
    AegisLaunchContext.sessionId = System.getProperty("aegis.session")?.trim()?.takeIf { it.isNotEmpty() }
    // Shared-quote preview on desktop: `-Daegis.quote=<id>` mirrors the WASM
    // `?quote=` query so an operator can sanity-check the read-only summary
    // view from the JVM build before sending the link out.
    AegisLaunchContext.quoteId = System.getProperty("aegis.quote")?.trim()?.takeIf { it.isNotEmpty() }

    // Capture the host language tag once before Compose boots; AegisRoot uses
    // this to one-time-auto-seed `AegisSettings.locale = "hi"` on first launch
    // for Hindi-speaking customers. Null = no detection, no auto-seed.
    AegisLaunchContext.hostLocale = detectHostLocale()

    val baseTitle = when (role) {
        AegisRole.CUSTOMER -> "Aegis — Buy Online (preview)"
        AegisRole.BUSINESS -> "Aegis — Operator Console"
        AegisRole.ADMIN    -> "Aegis — Admin Console"
    }

    application {
        // Window title connectivity indicator. The indicator is driven by a
        // tiny dedicated /health poll rather than the in-Compose dashboard
        // state because the Window() title is read at the application{} scope,
        // outside the AegisRoot composition where rememberDashboardData lives.
        // Keeping the poll out here avoids touching AegisRoot's API and adds
        // exactly one HTTP hit every 15 s — well under any reasonable noise
        // floor. Emoji indicators render cleanly on macOS/modern Linux title
        // bars, which is the supported desktop matrix; Windows title rendering
        // is fine on Win10+ which is the only Windows the operator binaries
        // target.
        var connectivity by remember { mutableStateOf("🟡 reconnecting…") }
        LaunchedEffect(Unit) {
            val client = ApiClient(AegisSettingsStore.load().serverBaseUrl)
            while (true) {
                runCatching { client.health() }
                    .onSuccess { connectivity = "🟢 connected" }
                    .onFailure { connectivity = "🔴 offline" }
                delay(15_000)
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "$baseTitle · $connectivity",
            state = rememberWindowState(width = 1440.dp, height = 900.dp)
        ) {
            AegisRoot(role)
        }
    }
}
