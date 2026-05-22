package com.rate.aegis

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Aegis WASM entry. Reads the `role` query parameter from the URL — defaults to
 * CUSTOMER (the buyonline journey, the audience for the public Cloudflare Pages
 * deployment). Operators previewing the BUSINESS shell can append `?role=business`
 * to the same URL.
 *
 *   https://phi-buyonline.pages.dev/                    → CUSTOMER
 *   https://phi-buyonline.pages.dev/?role=customer      → CUSTOMER
 *   https://phi-buyonline.pages.dev/?role=business      → BUSINESS (Home, Quotes, Calculator…)
 *   https://phi-buyonline.pages.dev/?role=admin         → ADMIN
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
    val params = search.removePrefix("?").split("&")
    val rolePair = params.firstOrNull { it.startsWith("role=", ignoreCase = true) }
        ?: return AegisRole.CUSTOMER
    val value = rolePair.substringAfter('=').trim()
    return runCatching { AegisRole.valueOf(value.uppercase()) }
        .getOrElse { AegisRole.CUSTOMER }
}
