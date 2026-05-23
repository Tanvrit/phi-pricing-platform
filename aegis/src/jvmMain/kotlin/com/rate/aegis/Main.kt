package com.rate.aegis

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rate.aegis.settings.AegisSettingsStore

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

    val title = when (role) {
        AegisRole.CUSTOMER -> "Aegis — Buy Online (preview)"
        AegisRole.BUSINESS -> "Aegis — Operator Console"
        AegisRole.ADMIN    -> "Aegis — Admin Console"
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = title,
            state = rememberWindowState(width = 1440.dp, height = 900.dp)
        ) {
            AegisRoot(role)
        }
    }
}
