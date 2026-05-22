package com.rate.aegis

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Aegis desktop entry. Defaults to BUSINESS role — the role can be overridden
 * with `-Daegis.role=CUSTOMER|BUSINESS|ADMIN` for testing.
 *
 * Customer-facing buyonline is served from the WASM target via Cloudflare Pages;
 * this JVM binary is the operator/admin app distributed to PRUHealth staff.
 */
fun main() {
    val role = System.getProperty("aegis.role")?.let {
        runCatching { AegisRole.valueOf(it.uppercase()) }.getOrNull()
    } ?: AegisRole.BUSINESS

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
