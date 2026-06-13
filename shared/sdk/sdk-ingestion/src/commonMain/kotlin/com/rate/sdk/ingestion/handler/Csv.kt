package com.rate.sdk.ingestion.handler

/**
 * A minimal, dependency-free, PURE-KMP RFC-4180-ish CSV reader.
 *
 * The /data exports are real-world CSV: fields are double-quoted when they contain commas,
 * newlines, or embedded quotes (escaped as `""`). A naive `split(",")` corrupts the
 * Product-Benefit-Table rows (many cells hold multi-line option lists). This reader handles:
 *  - quoted fields with embedded `,` and `\n` / `\r\n`
 *  - escaped quotes (`""` → `"`) inside quoted fields
 *  - mixed line endings
 *  - a trailing newline (no spurious empty final record)
 *
 * It is intentionally tolerant (never throws on malformed input) because ingestion treats an
 * unparseable row as "skip + warn", not "fail the whole file".
 */
object Csv {

    /** Parse [text] into a list of records, each a list of string cells (untrimmed). */
    fun parse(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        var sawAnyCharInRecord = false

        fun endField() {
            row.add(field.toString())
            field.clear()
        }
        fun endRecord() {
            endField()
            records.add(row)
            row = ArrayList()
            sawAnyCharInRecord = false
        }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                when (c) {
                    '"' -> {
                        if (i + 1 < n && text[i + 1] == '"') { // escaped quote
                            field.append('"'); i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> { inQuotes = true; sawAnyCharInRecord = true }
                    ',' -> { endField(); sawAnyCharInRecord = true }
                    '\r' -> {
                        // swallow; a following \n completes the record (CRLF), else treat CR as EOL
                        if (i + 1 < n && text[i + 1] == '\n') i++
                        endRecord()
                    }
                    '\n' -> endRecord()
                    else -> { field.append(c); sawAnyCharInRecord = true }
                }
            }
            i++
        }
        // Flush the final field/record if the file did not end with a newline.
        if (field.isNotEmpty() || row.isNotEmpty() || sawAnyCharInRecord) {
            endRecord()
        }
        return records
    }

    /** A record is "blank" when every cell is empty/whitespace (the CSVs are full of these). */
    fun isBlankRecord(record: List<String>): Boolean = record.all { it.isBlank() }

    /** Safe cell accessor: returns the trimmed cell at [idx], or "" if out of bounds. */
    fun cell(record: List<String>, idx: Int): String =
        record.getOrNull(idx)?.trim() ?: ""
}
