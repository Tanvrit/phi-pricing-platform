package com.rate.aegis

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Aegis WASM entry. Defaults to CUSTOMER (buyonline journey) — that's the
 * audience for the public Cloudflare Pages deployment at phi-buyonline.pages.dev.
 *
 * Operator-facing surfaces (BUSINESS/ADMIN) ship in the JVM binary; we don't
 * currently expose them over the web.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        AegisRoot(AegisRole.CUSTOMER)
    }
}
