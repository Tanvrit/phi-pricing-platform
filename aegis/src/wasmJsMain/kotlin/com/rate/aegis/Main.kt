package com.rate.aegis

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Aegis WASM entry — the public browser build.
 *
 * Role comes from the `?role=` URL query and defaults to CUSTOMER (the buy-online
 * journey is the audience for the Cloudflare Pages deploy, `phi-buyonline.pages.dev`).
 * Operators can preview the operator/admin shells with an explicit `?role=business`
 * / `?role=admin`.
 *
 *   https://phi-buyonline.pages.dev/                → CUSTOMER
 *   https://phi-buyonline.pages.dev/?role=customer  → CUSTOMER
 *   https://phi-buyonline.pages.dev/?role=business  → BUSINESS
 *   https://phi-buyonline.pages.dev/?role=admin     → ADMIN
 *
 * The `?session=` / `?quote=` launch context is owned by the buy-online module's
 * own platform seam (it reads `window.location` directly), so this entry only
 * needs to resolve the role. We deliberately do NOT seed the implicit role from
 * the persisted `defaultRole` (that defaults to BUSINESS, which would leak the
 * operator console onto the bare public URL) — the bare URL is ALWAYS CUSTOMER.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val role = roleFromQueryString(window.location.search)
    ComposeViewport(document.body!!) {
        AegisRoot(role)
    }
}

private fun roleFromQueryString(search: String): AegisRole {
    if (search.isEmpty() || search == "?") return AegisRole.CUSTOMER
    val rolePair = search.removePrefix("?").split("&")
        .firstOrNull { it.startsWith("role=", ignoreCase = true) }
        ?: return AegisRole.CUSTOMER
    val value = rolePair.substringAfter('=').trim()
    // Unknown / corrupt `?role=` value falls back to the public default.
    return AegisRole.parse(value, AegisRole.CUSTOMER)
}
