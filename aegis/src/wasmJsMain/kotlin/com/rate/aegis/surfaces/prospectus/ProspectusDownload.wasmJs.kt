package com.rate.aegis.surfaces.prospectus

import kotlinx.browser.window

/**
 * WASM actual — wrap the HTML in a `Blob` of type `text/html`, mint a blob URL
 * via `URL.createObjectURL`, and open it in a new browser tab. The user can
 * then File → Print → Save as PDF from that tab.
 *
 * Implementation note
 * -------------------
 * Compose-WASM's classpath does not currently ship typed bindings for
 * `org.w3c.files.Blob` and `org.w3c.dom.url.URL` (those types live in the
 * legacy `kotlinx-html`/Kotlin-JS stdlib and are not re-exported for the
 * `wasm-js` target as of Kotlin 2.1.0). We therefore inline a one-liner
 * `js(...)` block that constructs the Blob + URL directly. The planId is
 * untrusted but only used for the filename — we don't string-interpolate it
 * into the JS literal; the `html` body is bound by name into the JS scope by
 * the Kotlin/JS interop so it cannot be injected.
 */
actual fun openOrSaveProspectus(planId: String, html: String) {
    try {
        val url = blobUrlFor(html)
        window.open(url, "_blank")
    } catch (t: Throwable) {
        println("aegis: prospectus open failed: ${t.message}")
    }
}

/**
 * Build a `text/html` Blob URL for the given content. Returns the
 * `blob:https://…` URL ready to hand to `window.open` / an `<a download>`.
 *
 * Uses a `js(...)` block because Compose-WASM lacks typed `Blob`/`URL`
 * bindings — see the file-level note.
 */
private fun blobUrlFor(html: String): String =
    js("URL.createObjectURL(new Blob([html], { type: 'text/html' }))")
