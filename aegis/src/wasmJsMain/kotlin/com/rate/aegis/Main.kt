package com.rate.aegis

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.rate.aegis.settings.AegisSettingsStore
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
    if (search.isEmpty() || search == "?") return defaultRoleFromSettings()
    val params = search.removePrefix("?").split("&")
    val rolePair = params.firstOrNull { it.startsWith("role=", ignoreCase = true) }
        ?: return defaultRoleFromSettings()
    val value = rolePair.substringAfter('=').trim()
    return runCatching { AegisRole.valueOf(value.uppercase()) }
        .getOrElse { defaultRoleFromSettings() }
}

/**
 * Resolve the boot role from operator-saved settings. Any unparseable / corrupt
 * value (e.g. an older build wrote "GUEST", or the localStorage entry got
 * hand-edited) falls back to CUSTOMER — the safe default for the public web
 * entry, matching the prior hardcoded behaviour.
 */
private fun defaultRoleFromSettings(): AegisRole {
    val saved = AegisSettingsStore.load().defaultRole
    return runCatching { AegisRole.valueOf(saved.uppercase()) }
        .getOrElse { AegisRole.CUSTOMER }
}
