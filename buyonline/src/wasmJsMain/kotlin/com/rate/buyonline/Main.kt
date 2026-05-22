package com.rate.buyonline

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * WASM-JS entrypoint for the BuyOnline customer journey. Mounts the same
 * commonMain `BuyOnlineApp()` into the host page's `<body>`.
 *
 * The Cloudflare Pages deployment serves `buyonline.js` + the `.wasm` blob; this
 * `main()` is what they invoke once the runtime is ready. Without this file the
 * WASM module loads but never renders — page stays on the loader spinner.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        BuyOnlineApp()
    }
}
