package com.rate.aegis.util

import kotlinx.datetime.Clock

/**
 * CSV export plumbing for Aegis surfaces.
 *
 * `saveCsv` is an expect/actual: JVM writes to `~/Downloads/<filename>`
 * (and tries to reveal that directory via [java.awt.Desktop]); WASM-JS wraps
 * the CSV string in a `Blob`, mints a `URL.createObjectURL(...)` blob URL,
 * and triggers a download via a programmatic anchor click. Anchor-click is
 * preferred over `window.open(blobUrl)` because some browsers block the
 * latter as a popup for blob URLs that don't originate from a user gesture
 * on the same origin.
 *
 * The CSV helpers (`csvCell`, `buildCsv`) are pure commonMain — no platform
 * dependency — so they remain unit-testable.
 */

/** Hand a CSV blob to the host platform. JVM saves to ~/Downloads; WASM opens a blob URL. */
expect fun saveCsv(filename: String, csv: String)

/** Quote-and-comma-escape a single cell for RFC 4180 CSV. */
fun csvCell(value: Any?): String {
    val s = value?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"" + s.replace("\"", "\"\"") + "\""
    else s
}

/** Build a CSV from headers + rows. Rows can carry mixed types; csvCell coerces. */
fun buildCsv(headers: List<String>, rows: List<List<Any?>>): String = buildString {
    append(headers.joinToString(","))
    append('\n')
    rows.forEach { row ->
        append(row.joinToString(",") { csvCell(it) })
        append('\n')
    }
}

/** Today's date as `YYYY-MM-DD`, derived from the instant prefix (cheap, no formatter). */
fun todayIsoDate(): String = Clock.System.now().toString().substringBefore('T')
