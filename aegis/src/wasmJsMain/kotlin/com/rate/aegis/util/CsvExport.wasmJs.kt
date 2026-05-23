package com.rate.aegis.util

/**
 * WASM actual — wrap the CSV string in a `Blob`, mint a blob URL via
 * `URL.createObjectURL`, attach a hidden `<a download>` to the DOM and
 * programmatically click it. We deliberately avoid `window.open(blobUrl)`
 * because Chromium-family browsers occasionally treat blob-URL `window.open`
 * as a popup when the gesture chain is "long" (e.g. nested coroutines),
 * blocking the download. The anchor-click pattern is the standard JS recipe
 * and works under the same user-gesture rules without popup heuristics.
 *
 * Implementation note
 * -------------------
 * Compose-WASM doesn't expose typed bindings for `Blob`, `URL`, or the DOM
 * `HTMLAnchorElement` ergonomically, so we drop into a `js(...)` block.
 * Kotlin/Wasm requires the `js(...)` body to live in a private helper at
 * function-top-level scope — we therefore factor [triggerCsvDownload] out.
 * The `filename` and `csv` Kotlin parameters are bound by name into the JS
 * scope by the Kotlin interop layer, so there's no string interpolation
 * happening and no injection surface.
 */
actual fun saveCsv(filename: String, csv: String) {
    runCatching { triggerCsvDownload(filename, csv) }
        .onFailure { println("aegis: CSV download failed: ${it.message}") }
}

private fun triggerCsvDownload(filename: String, csv: String): Unit =
    js(
        "{ " +
            "var blob = new Blob([csv], { type: 'text/csv' }); " +
            "var url = URL.createObjectURL(blob); " +
            "var a = document.createElement('a'); " +
            "a.href = url; a.download = filename; " +
            "document.body.appendChild(a); a.click(); document.body.removeChild(a); " +
            "URL.revokeObjectURL(url); " +
        "}"
    )
