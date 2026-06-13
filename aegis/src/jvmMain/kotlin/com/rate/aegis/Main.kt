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
import com.rate.aegis.di.buildClient
import com.rate.aegis.settings.AegisSettingsStore
import kotlinx.coroutines.delay

/**
 * Aegis desktop entry — the operator/admin console distributed to PRUHealth staff.
 *
 * Role resolution order:
 *   1. `-Daegis.role=CUSTOMER|BUSINESS|ADMIN` system property (testing override);
 *   2. the operator-saved [com.rate.aegis.settings.AegisSettings.defaultRole];
 *   3. BUSINESS.
 * A corrupt/unknown value at either step silently degrades to BUSINESS rather
 * than crashing the binary on launch.
 *
 * The customer-facing buy-online journey is served from the WASM target via
 * Cloudflare Pages — not this JVM binary (though `-Daegis.role=CUSTOMER` will
 * render it for a desktop preview).
 */
fun main() {
    // Desktop ALWAYS defaults to OWNER (superuser — full operator + config-CRUD console).
    // Override with the AEGIS_ROLE env var (passes through the gradle run task's forked JVM)
    // or the -Daegis.role system property; the persisted defaultRole no longer downgrades it.
    val explicitRole = System.getProperty("aegis.role") ?: System.getenv("AEGIS_ROLE")
    val role = AegisRole.parse(explicitRole, AegisRole.OWNER)

    val baseTitle = when (role) {
        AegisRole.CUSTOMER -> "Aegis — Buy Online (preview)"
        AegisRole.BUSINESS -> "Aegis — Operator Console"
        AegisRole.ADMIN    -> "Aegis — Admin Console"
        AegisRole.OWNER    -> "Aegis — Owner Console"
    }

    application {
        // Window-title connectivity indicator. Driven by a tiny dedicated /health
        // poll on the shared client rather than the in-Compose state, because the
        // Window() title is read at application{} scope, outside the AegisRoot
        // composition. One HTTP hit every 15 s — well under any noise floor.
        var connectivity by remember { mutableStateOf("🟡 reconnecting…") }
        LaunchedEffect(Unit) {
            val client = buildClient()
            while (true) {
                runCatching { client.getText("/health") }
                    .onSuccess { connectivity = "🟢 connected" }
                    .onFailure { connectivity = "🔴 offline" }
                delay(15_000)
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "$baseTitle · $connectivity",
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
        ) {
            AegisRoot(role)
        }
    }
}
