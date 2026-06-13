package com.rate.sdk.ingestion.handler

import com.rate.sdk.catalog.model.group.DeviceCategory
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.SublimitScope
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog
import com.rate.sdk.catalog.model.group.VaccinationItem

/**
 * PURE-KMP parser for the three list-shaped sections of the GROUP GHI annexure
 * (`Product Benefit Table EE GHI/Annexure.csv`) that S2 modelled as their own catalog entities:
 *  - the "Adult Vaccination List" / "ADULT VACCINATIONS" schedule   → [parseVaccinationCatalog]
 *  - the "List of Monitoring / Medical Devices" grouped list        → [parseMedicalDeviceCatalog]
 *  - the "Sublimits on Treatments…(Per Claim)" % -of-SI table       → [parseSurgicalSublimits]
 *
 * These three sections were previously folded into generic `Annexure` documents; the new typed
 * models give them stable lookup keys ([listCode]) and structured shapes (vaccination items,
 * device categories, %-of-SI caps) for quoting / UI.
 *
 * Parsing philosophy matches the rest of ingestion: tolerant — each method scans the WHOLE
 * annexure text, finds its own section by heading, and stops at the next section / blank run; a
 * row that can't be mapped is skipped, never thrown. Each method is self-contained so the seeder
 * can call exactly the ones it needs with the same annexure CSV text.
 *
 * NOTE on the two annexure variants: the canonical GROUP GHI annexure expresses surgical
 * sublimits as "10% of SI Opted" → "90% of SI Opted" (resolved here to 0.10 / 0.90 fractions).
 * The older top-level `Annexure.csv` instead lists ABSOLUTE rupee ranges ("<<10000 to 1,00,000>>")
 * under a `7,Sublimits` heading — those are NOT %-of-SI and are intentionally NOT parsed here
 * (they would need a different, SI-bearing model). [parseSurgicalSublimits] only emits rows whose
 * cap is expressed as a percentage of SI, so feeding it the old file simply yields an empty list.
 */
class AnnexureListParser {

    // ── Adult Vaccination List ──────────────────────────────────────────────
    /**
     * The "ADULT VACCINATIONS" schedule: an optional "Adult Vaccination List:" title row, then a
     * `Sl no,ADULT VACCINATIONS` header, then numbered `N,<name>` rows. Emits a single
     * [VaccinationCatalog] keyed [listCode] (default "ADULT_VACCINATION").
     */
    fun parseVaccinationCatalog(
        csv: String,
        listCode: String = "ADULT_VACCINATION",
        name: String = "Adult Vaccination List",
    ): VaccinationCatalog {
        val rows = Csv.parse(csv)
        val items = ArrayList<VaccinationItem>()
        var started = false
        var sl = 0
        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            if (!started) {
                // The header that opens the block: `Sl no, ADULT VACCINATIONS`.
                if (a.equals("Sl no", true) && b.contains("VACCINATION", true)) started = true
                continue
            }
            if (Csv.isBlankRecord(r)) break // a blank run ends the section
            // A numbered "N,<name>" row is a vaccination; anything else ends the block.
            val n = a.toIntOrNull()
            if (n == null || b.isBlank()) break
            sl++
            items += VaccinationItem(slNo = sl, name = b.normaliseWs())
        }
        return VaccinationCatalog(
            listCode = listCode,
            name = name,
            items = items.distinctBy { it.name.lowercase() }
                .mapIndexed { i, it -> it.copy(slNo = i + 1) },
        )
    }

    // ── Monitoring / Medical Devices ────────────────────────────────────────
    /**
     * The "List of Monitoring / Medical Devices" block: a bare title row in col 0, then numbered
     * category headings `N,<Category>` (col 0 = number, col 1 = category name) each followed by
     * blank-col-0 device rows whose name sits in col 1. Emits a single [MedicalDeviceCatalog]
     * whose [categories] preserve the source order.
     */
    fun parseMedicalDeviceCatalog(
        csv: String,
        listCode: String = "MEDICAL_DEVICES",
        name: String = "Monitoring / Medical Devices",
    ): MedicalDeviceCatalog {
        val rows = Csv.parse(csv)
        val categories = ArrayList<DeviceCategory>()
        var started = false
        var currentName: String? = null
        var devices = ArrayList<String>()

        fun flush() {
            val cn = currentName
            if (cn != null && devices.isNotEmpty()) {
                categories += DeviceCategory(name = cn, devices = devices.toList())
            }
            devices = ArrayList()
        }

        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            if (!started) {
                if (a.startsWith("List of", true) && a.contains("Devices", true)) started = true
                continue
            }
            if (Csv.isBlankRecord(r)) break // a blank run ends the section
            val n = a.toIntOrNull()
            when {
                // Numbered category heading: "1,Cardio-Respiratory Monitoring Devices".
                n != null && b.isNotBlank() -> { flush(); currentName = b.normaliseWs() }
                // Device row: blank col 0, device name in col 1.
                a.isBlank() && b.isNotBlank() && currentName != null -> devices.add(b.normaliseWs())
                // Another "List of …" title would start a new (unexpected) block → stop.
                a.startsWith("List of", true) -> break
                else -> {} // tolerate stray blank/partial cells
            }
        }
        flush()
        return MedicalDeviceCatalog(listCode = listCode, name = name, categories = categories)
    }

    // ── Surgical Sublimits (% of SI) ─────────────────────────────────────────
    /**
     * The "Sublimits on Treatments/Illness/Disease/Surgery/Medical Condition (Per Claim)" table of
     * the GROUP GHI annexure: a title row, a "Sub - Limit (Amount in Rs)" sub-title, a
     * `Sl no,Surgeries,Min,Max` header, then rows like
     * `1,Cataract (Per eye),10% of SI Opted,90% of SI Opted`. Min/Max are parsed as fractions of
     * the opted SI (0.10 / 0.90). The scope is inferred from the surgery name ("Per eye" →
     * PER_EYE, "Per family" → PER_FAMILY, else PER_PERSON). The trailing "*This is an indicative
     * list…" footnote and blank runs end the table.
     *
     * Rows whose Min/Max are NOT expressed as a percentage of SI (e.g. the older file's absolute
     * "<<10000 to 1,00,000>>" rupee ranges) are skipped, so this method only ever produces
     * SI-agnostic [SurgicalSublimit]s.
     */
    fun parseSurgicalSublimits(csv: String): List<SurgicalSublimit> {
        val rows = Csv.parse(csv)
        val out = ArrayList<SurgicalSublimit>()
        var started = false
        var sl = 0
        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            if (!started) {
                if (a.startsWith("Sublimits on", true) ||
                    (a.contains("Sublimit", true) && a.contains("Per Claim", true))
                ) {
                    started = true
                }
                continue
            }
            // Header / sub-title rows inside the block.
            if (a.startsWith("Sub - Limit", true) || a.startsWith("Sub-Limit", true)) continue
            if (a.equals("Sl no", true) || b.equals("Surgeries", true)) continue
            // Footnote / blank run ends the table.
            if (a.startsWith("*", true)) break
            if (Csv.isBlankRecord(r)) {
                // A single blank line may precede the footnote; only stop once we have rows.
                if (out.isNotEmpty()) break else continue
            }
            val n = a.toIntOrNull()
            if (n == null || b.isBlank()) {
                if (out.isNotEmpty()) break else continue
            }
            val minPct = parsePercentOfSi(Csv.cell(r, 2))
            val maxPct = parsePercentOfSi(Csv.cell(r, 3))
            // Only %-of-SI rows belong to this model; absolute rupee rows are not ours.
            if (minPct == null && maxPct == null) continue
            sl++
            val surgery = b.normaliseWs()
            out += SurgicalSublimit(
                slNo = sl,
                surgeryName = surgery,
                minPercentOfSI = minPct,
                maxPercentOfSI = maxPct,
                scope = scopeFor(surgery),
                displayOrder = sl - 1,
            )
        }
        return out
    }

    /** Infer the cap scope from the surgery name's parenthetical hint. */
    private fun scopeFor(surgery: String): SublimitScope = when {
        surgery.contains("per eye", true) -> SublimitScope.PER_EYE
        surgery.contains("per family", true) -> SublimitScope.PER_FAMILY
        else -> SublimitScope.PER_PERSON
    }

    /**
     * Parse a "10% of SI Opted" / "90% of SI Opted" / "10%" cell into a 0..1 fraction (0.10 / 0.90).
     * Returns null when the cell carries no percent (blank, "NA", or an absolute rupee figure) so
     * the caller can drop non-%-of-SI rows.
     */
    private fun parsePercentOfSi(raw: String): Double? {
        val s = raw.trim()
        if (s.isBlank()) return null
        if (!s.contains("%")) return null
        val num = Regex("""(\d+(?:\.\d+)?)\s*%""").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        return num / 100.0
    }

    /** Collapse runs of internal whitespace (incl. embedded newlines) into single spaces; trim. */
    private fun String.normaliseWs(): String =
        Regex("""\s+""").replace(this, " ").trim()
}
