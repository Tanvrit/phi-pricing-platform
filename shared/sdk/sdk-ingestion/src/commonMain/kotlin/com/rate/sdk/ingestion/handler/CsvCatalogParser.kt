package com.rate.sdk.ingestion.handler

import com.rate.core.money.Money
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.regulatory.CoverageLogic
import com.rate.core.regulatory.PayoutType
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.AnnexureCategory
import com.rate.sdk.catalog.model.AnnexureCiTierRow
import com.rate.sdk.catalog.model.AnnexureSublimitRow
import com.rate.sdk.catalog.model.AnnexureType
import com.rate.sdk.catalog.model.CIItem
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CoverOption
import com.rate.sdk.catalog.model.CoverOptionGroup
import com.rate.sdk.catalog.model.CoverParamType
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.RateKind
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Sublimit
import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.BenefitScheduleLine
import com.rate.sdk.catalog.model.group.CheckupPackageType
import com.rate.sdk.catalog.model.group.ChronicOpdGrid
import com.rate.sdk.catalog.model.group.ConsumablesList
import com.rate.sdk.catalog.model.group.ConsumablesListType
import com.rate.sdk.catalog.model.group.DayCareItem
import com.rate.sdk.catalog.model.group.DayCareProcedure
import com.rate.sdk.catalog.model.group.DisabilityTableType
import com.rate.sdk.catalog.model.group.EligibilityCriteria
import com.rate.sdk.catalog.model.group.GroupPolicyType
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.catalog.model.group.HealthCheckupPackage
import com.rate.sdk.catalog.model.group.PpdPtdRow
import com.rate.sdk.catalog.model.group.PpdPtdSourceGroup
import com.rate.sdk.catalog.model.group.PpdPtdTable
import com.rate.sdk.catalog.model.group.RiskClass
import com.rate.sdk.catalog.model.group.WaitingPeriod

/**
 * PURE-KMP parser for the /data CSV exports → sdk-catalog [com.rate.core.base.model.ConfigEntity]
 * value objects. Relocated from the monolith's hardcoded importers, but data-driven: each method
 * takes the raw CSV text of ONE file shape and returns typed entities (no IO, no Mongo, no POI).
 *
 * Parsing philosophy (matching the ingestion contract): tolerant — a row that can't be mapped is
 * dropped and surfaced as a warning by the caller, never an exception. Each returned entity gets a
 * fresh ULID id by default; the caller's [CatalogSeeder] upserts them. Cross-references (e.g.
 * `groupProductRef`, `coverCode`) are filled by [CatalogSeeder] after parsing, since ids are minted
 * at write time.
 *
 * Shapes handled (one method each), from the real files under /data:
 *  - PBT Index.csv                     → [parseSections]
 *  - Indemnity- NEW ADDITION.csv /
 *    Employer Employee.csv             → [parseGroupProductConfig] + [parseBaseBenefitSchedule]
 *  - List of CI 101 and 92.csv         → [parseFlatCriticalIllnessList]
 *  - PBT .../CI List.csv (Plan 1..5)   → [parseTieredCriticalIllnessLists]
 *  - Waiting Periods.csv               → [parseWaitingPeriods]
 *  - Eligibility.csv                   → [parseEligibility]
 *  - PPD PTD Tables.csv                → [parsePpdPtdTables]
 *  - Day Care List.csv                 → [parseDayCare]
 *  - Consumables List.csv              → [parseConsumables]
 *  - Health Check Up Packages.csv      → [parseHealthCheckupPackages]
 *  - Annexure.csv (Chronic OPD Grid)   → [parseChronicOpdGrid]
 *  - Annexure.csv (OPD minor proc …)   → [parseAnnexures]
 */
class CsvCatalogParser {

    // ── PBT Index → Sections ───────────────────────────────────────────────
    /**
     * `PBT Index.csv`: `Section #,Section name,Sub-Section,Day 1,Day 2,New`. A blank Section #
     * means a continuation sub-section row under the previous section. "Day 1"/"Day 2" markers
     * (non-blank, not "-") set the day1/day2 flags.
     */
    fun parseSections(csv: String, productLine: ProductLine = ProductLine.GROUP): List<Section> {
        val rows = Csv.parse(csv)
        val out = ArrayList<Section>()
        var order = 0
        var currentNumber: String? = null
        var currentName: String? = null
        for (r in rows) {
            if (Csv.isBlankRecord(r)) continue
            val num = Csv.cell(r, 0)
            val name = Csv.cell(r, 1)
            val sub = Csv.cell(r, 2).ifBlank { null }
            // Skip header / title rows.
            if (num.equals("Section #", true) || (num.isBlank() && name.isBlank() && sub == null)) continue
            if (num.isNotBlank()) { currentNumber = num; currentName = name }
            val sectionNumber = (currentNumber ?: continue)
            val sectionName = (if (name.isNotBlank()) name else currentName).orEmpty()
            if (sectionName.isBlank() && sub == null) continue
            val day1 = isDayMarker(Csv.cell(r, 3))
            val day2 = isDayMarker(Csv.cell(r, 4))
            out += Section(
                sectionNumber = if (sub != null) extractSubNumber(sub) ?: sectionNumber else sectionNumber,
                name = if (sub != null) sub else sectionName,
                subSection = sub,
                productLine = productLine,
                day1 = day1,
                day2 = day2,
                displayOrder = order++,
            )
        }
        return out
    }

    private fun isDayMarker(v: String): Boolean =
        v.isNotBlank() && v != "-" && v.contains("Day", ignoreCase = true)

    /** "1.2 Accidental Hospitalisation" → "1.2". */
    private fun extractSubNumber(sub: String): String? =
        Regex("""^\s*([0-9]+(?:\.[0-9]+)?)""").find(sub)?.groupValues?.get(1)

    // ── Group product config (Features / Boundary Conditions block) ─────────
    /**
     * The top "Features / Boundary Conditions" block of `Indemnity- NEW ADDITION.csv` /
     * `Employer Employee.csv`. Reads keyed rows (col A = feature label, col C = value) until the
     * "PART 1- BASE COVERS" marker.
     */
    fun parseGroupProductConfig(
        csv: String,
        code: String = "GROUP_EE",
        name: String = "Group Product - Employer Employee",
    ): GroupProductConfig {
        val rows = Csv.parse(csv)
        var policyType = GroupPolicyType.FLOATER
        var relations = emptyList<String>()
        var constructs = emptyList<String>()
        var geography = "India Only"
        var tenures = listOf(1, 2, 3, 4, 5)
        var minSi = Money.ZERO
        var maxSi = Money.ZERO
        var paymentTerms = emptyList<Int>()
        var paymentModes = emptyList<String>()
        var entryAge = ""
        var exitAge = ""
        // Free-text boundary-condition notes accumulated across rows (col 3 secondary detail +
        // col 7/8 "Comments"), so special conditions like "LGBTQ (Under General/Special condition)"
        // and per-cover age limits are preserved.
        val specialConditions = LinkedHashSet<String>()
        for (r in rows) {
            val key = Csv.cell(r, 0)
            if (key.startsWith("PART 1", true) || key.equals("Sl No", true)) break
            val value = Csv.cell(r, 2)
            // The secondary detail column (col 3) and the trailing "Comments" column (col 7) carry
            // special/boundary conditions that aren't a primary value.
            val detail = Csv.cell(r, 3)
            val comment = Csv.cell(r, 7).ifBlank { Csv.cell(r, 8) }
            when {
                key.equals("Policy Type", true) ->
                    policyType = parseGroupPolicyType(value, policyType)
                key.equals("Relations Covered", true) -> {
                    relations = splitList(value)
                    // Relations row's secondary cell holds the special-condition note ("LGBTQ …").
                    detail.normaliseWs().takeIf { it.isNotBlank() }?.let { specialConditions.add(it) }
                }
                key.startsWith("Family Floater", true) ->
                    constructs = splitList(detail).ifEmpty { listOf(value) }
                key.equals("Geography", true) && value.isNotBlank() ->
                    geography = value
                key.equals("Tenure", true) ->
                    parseTenureYears(value).takeIf { it.isNotEmpty() }?.let { tenures = it }
                key.startsWith("Entry and Maximum Age", true) || key.startsWith("Entry Age", true) -> {
                    entryAge = extractBoundary(value, "Entry Age").ifBlank { entryAge }
                    exitAge = extractBoundary(value, "Exit Age").ifBlank { exitAge }
                    // Any free-text age qualifier ("specific limits for certain covers").
                    detail.normaliseWs().takeIf { it.isNotBlank() }?.let { specialConditions.add(it) }
                }
                key.startsWith("Premium Payment Terms", true) ->
                    parseTenureYears(value).takeIf { it.isNotEmpty() }?.let { paymentTerms = it }
                key.startsWith("Premium Payment Mode", true) && value.isNotBlank() ->
                    paymentModes = value.split('/', ',').map { it.trim() }.filter { it.isNotEmpty() }
                key.equals("Sum Insured", true) && value.isNotBlank() -> {
                    val (lo, hi) = parseSumInsuredRange(value)
                    minSi = lo; maxSi = hi
                }
            }
            // Capture trailing comment cells from any boundary-condition row.
            comment.normaliseWs().takeIf { it.isNotBlank() && !it.equals("Comments", true) }
                ?.let { specialConditions.add(it) }
        }
        return GroupProductConfig(
            code = code,
            name = name,
            policyType = policyType,
            relationsCovered = relations,
            familyConstructs = constructs,
            geography = geography,
            availableTenures = tenures,
            minSumInsured = minSi,
            maxSumInsured = maxSi,
            premiumPaymentTerms = paymentTerms,
            premiumPaymentModes = paymentModes,
            entryAge = entryAge,
            exitAge = exitAge,
            specialConditionsText = specialConditions.joinToString("\n").take(2000),
        )
    }

    /** "Entry Age: 0 Years\nExit Age: Lifelong" + label → the value after that label's colon. */
    private fun extractBoundary(value: String, label: String): String {
        for (line in value.split('\n')) {
            val l = line.trim()
            if (l.startsWith(label, ignoreCase = true)) return l.substringAfter(':', "").trim()
        }
        return ""
    }

    /** "Rs 10,000 to Rs 300 Lacs" → (Money 10000, Money 300 Lacs). Order-tolerant; min is smaller. */
    private fun parseSumInsuredRange(value: String): Pair<Money, Money> {
        val parts = Regex("""(?i)\s+to\s+""").split(value)
        val monies = if (parts.size >= 2) parts.map { parseMoneyLimit(it) } else listOf(parseMoneyLimit(value))
        val nonZero = monies.filter { it != Money.ZERO }
        if (nonZero.isEmpty()) return Money.ZERO to Money.ZERO
        val lo = nonZero.minOrNull() ?: Money.ZERO
        val hi = nonZero.maxOrNull() ?: Money.ZERO
        return lo to hi
    }

    /**
     * "Policy Type" boundary cell → [GroupPolicyType].
     *
     * This was `if (value.contains("Non", true)) NON_FLOATER else FLOATER`, which
     * cannot tell "Non-Floater" (non-floater only) from "Floater / Non-Floater"
     * (both offered) — the substring "Non" is present either way. Both group PBT
     * files in data/ carry exactly the latter:
     *
     *   Policy Type,Boundary Conditions,Floater / Non-Floater,,,,,
     *
     * so every real ingestion of an Employer-Employee product was labelled
     * NON_FLOATER. There is no BOTH member on the enum, and FLOATER is the
     * declared default for the field, so a cell that offers floater at all
     * resolves to FLOATER.
     *
     * Method: delete any "Non-Floater" / "Non Floater" / "NonFloater" spellings,
     * then ask whether a standalone "Floater" survives.
     */
    private fun parseGroupPolicyType(value: String, current: GroupPolicyType): GroupPolicyType {
        if (value.isBlank()) return current
        val withoutNonFloater = Regex("""(?i)non[\s\-_/]*floater""").replace(value, "")
        return when {
            withoutNonFloater.contains("floater", true) -> GroupPolicyType.FLOATER
            value.contains("floater", true) -> GroupPolicyType.NON_FLOATER
            else -> current
        }
    }

    /** "Tenure :1/2/3/4/5 years" → [1,2,3,4,5]. */
    private fun parseTenureYears(v: String): List<Int> =
        Regex("""\d+""").findAll(v).map { it.value.toInt() }.filter { it in 1..5 }.distinct().toList()

    // ── Base benefit schedule (PART 1 / PART 2 cover rows) ──────────────────
    /**
     * The "PART 1- BASE COVERS" / "PART 2" tables of the group PBT files:
     * `Sl No,Cover,Cover Description,Minimum SI/Limits,Maximum SI/Limits,Additional Details,Logic`.
     * Each cover row becomes one [BenefitScheduleLine]; the whole table is one [BenefitSchedule].
     */
    fun parseBaseBenefitSchedule(
        csv: String,
        scheduleName: String = "Base Covers",
    ): BenefitSchedule {
        val rows = Csv.parse(csv)
        val lines = ArrayList<BenefitScheduleLine>()
        var inTable = false
        // PART 1 / PART 2 = base covers (included); PART 1.1 = optional covers (included=false).
        // The PBT files restart Sl No (1..) under each PART, so a part boundary doesn't end the
        // table — it just flips the inclusion + which column holds the "Logic" classifier.
        var optional = false
        for (r in rows) {
            val first = Csv.cell(r, 0)
            if (first.startsWith("PART", true)) {
                inTable = true
                // "PART 1.1" (and any "… OPTIONAL …" part) marks the optional-covers table.
                optional = Regex("""PART\s+1\.1""", RegexOption.IGNORE_CASE).containsMatchIn(first) ||
                    first.contains("OPTIONAL", true)
                continue
            }
            if (first.equals("Sl No", true) || first.equals("Sl no", true)) { inTable = true; continue }
            if (!inTable) continue
            if (Csv.isBlankRecord(r)) continue
            // A data row starts with an integer Sl No; otherwise it's a continuation/section heading.
            first.toIntOrNull() ?: continue
            val coverName = Csv.cell(r, 1)
            if (coverName.isBlank()) continue
            val description = Csv.cell(r, 2)
            val maxLimit = Csv.cell(r, 4)
            // The "Logic" classifier (Base SI / Part Of Base SI / Over & Above) sits in col 6 on
            // base rows and on the optional table; fall back to col 5 if col 6 is blank.
            val logicType = Csv.cell(r, 6).ifBlank { Csv.cell(r, 5) }
                .let { if (isBenefitLogicValue(it)) it.normaliseWs() else "" }
            lines += BenefitScheduleLine(
                coverCode = toCoverCode(coverName),
                coverName = coverName,
                limit = parseMoneyLimit(maxLimit),
                // No dedicated logicType field on the line schema — the regulatory "Logic" classifier
                // is the line's selected handling, so it rides in selectedOption.
                selectedOption = logicType.ifBlank { null },
                subLimitText = description.take(500),
                included = !optional,
            )
        }
        return BenefitSchedule(name = scheduleName, lines = lines)
    }

    /** A "Logic" cell value (the regulatory coverage-logic classifier), not free-text noise. */
    private fun isBenefitLogicValue(v: String): Boolean {
        val s = v.trim()
        if (s.isBlank()) return false
        return s.contains("Base SI", true) || s.contains("Part Of Base", true) ||
            s.contains("Over & Above", true) || s.contains("Over and Above", true) ||
            s.contains("Part Of Maternity", true)
    }

    // ── Critical-illness lists ──────────────────────────────────────────────
    /**
     * `List of CI 101 and 92.csv`: a title row ("101 list of CI"), then `Sl no,Name of CI`, then
     * the numbered list. [listCode] should reflect the variant (e.g. "CI_101").
     */
    fun parseFlatCriticalIllnessList(
        csv: String,
        listCode: String,
        name: String = "",
    ): CriticalIllnessList {
        val rows = Csv.parse(csv)
        val items = ArrayList<CIItem>()
        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            val sl = a.toIntOrNull() ?: continue
            if (b.isBlank()) continue
            items += CIItem(slNo = sl, name = b)
        }
        // Dedup by name preserving first sl-no (the source has accidental duplicates).
        val deduped = items.distinctBy { it.name.trim().lowercase() }
        return CriticalIllnessList(
            listCode = listCode,
            name = name.ifBlank { listCode },
            size = deduped.size,
            items = deduped.mapIndexed { i, it -> it.copy(slNo = i + 1) },
        )
    }

    /**
     * The EE `CI List.csv` tiered form: a "Plan 1..Plan 5" header, a "N CI(Unique)" count row,
     * then parallel columns where each column is one cumulative tier. We emit one
     * [CriticalIllnessList] per plan column; tier N is cumulative of columns 0..N (the PBT models
     * each higher plan as a superset).
     */
    fun parseTieredCriticalIllnessLists(csv: String): List<CriticalIllnessList> {
        val allRows = Csv.parse(csv)
        // The EE CI List file stacks TWO tables: the first "List of CI [Flexible]" Plan-per-column
        // table, then a second "Sl no / CI List (Incremental)" matrix (a different shape with ✓/×
        // membership flags + a Women-Specific column). We only parse the FIRST table here, so HARD
        // STOP at the row that introduces the incremental table.
        val stopIdx = allRows.indexOfFirst { r ->
            val a = Csv.cell(r, 0)
            (a.equals("Sl no", true) || a.equals("Sl No", true)) &&
                Csv.cell(r, 1).contains("Incremental", true)
        }
        val rows = if (stopIdx >= 0) allRows.subList(0, stopIdx) else allRows

        // Find the "Plan x" header row.
        val planRowIdx = rows.indexOfFirst { r ->
            r.any { it.trim().startsWith("Plan", true) && it.contains(Regex("""\d""")) }
        }
        if (planRowIdx < 0) return emptyList()
        val planHeader = rows[planRowIdx]
        // Column indices that name a plan.
        val planCols = planHeader.mapIndexedNotNull { idx, cell ->
            if (cell.trim().startsWith("Plan", true)) idx to cell.trim() else null
        }
        if (planCols.isEmpty()) return emptyList()
        // The "N CI(Unique)" count row usually follows the plan header.
        val countRow = rows.getOrNull(planRowIdx + 1)
        val dataStart = planRowIdx + 2

        // Collect per-column unique CI names (this column's own additions). Structural tokens —
        // pure numbers ("1", "0"), repeated "Plan x" labels, symbol-only cells (✓/×/-) and the
        // "N CI(Unique)" count copy — are NOT real CI names and are dropped.
        val perColumn = LinkedHashMap<Int, MutableList<String>>()
        planCols.forEach { (col, _) -> perColumn[col] = ArrayList() }
        for (r in dataStart until rows.size) {
            val rec = rows[r]
            for ((col, _) in planCols) {
                val name = Csv.cell(rec, col)
                if (isCiName(name)) perColumn[col]!!.add(name.normaliseWs())
            }
        }

        // Emit cumulative tiers: Plan N = columns up to and including N.
        val out = ArrayList<CriticalIllnessList>()
        val cumulative = LinkedHashSet<String>()
        var tierIndex = 0
        for ((col, planLabel) in planCols) {
            perColumn[col]!!.forEach { cumulative.add(it) }
            tierIndex++
            val declaredCount = countRow?.let { Regex("""\d+""").find(Csv.cell(it, col))?.value?.toIntOrNull() }
            val items = cumulative.toList().mapIndexed { i, n -> CIItem(slNo = i + 1, name = n) }
            // Validate the cumulative size against the declared "N CI(Unique)" count (expected
            // 1/4/16/32/48) — a soft check surfaced via reviewNotes-style copy in the name, never
            // by trimming/padding the real parsed items (the spreadsheet counts can drift).
            val countNote = declaredCount?.let { dc ->
                if (items.size == dc) " ($dc CI)" else " (declared $dc, parsed ${items.size})"
            }.orEmpty()
            // A women-specific tier is one whose plan label / declared count copy says so.
            val womenSpecific = planLabel.contains("Women", true) ||
                (countRow?.let { Csv.cell(it, col) }?.contains("Women", true) == true)
            out += CriticalIllnessList(
                listCode = "CI_TIER$tierIndex",
                name = planLabel + countNote,
                size = items.size,
                items = items,
                isWomenSpecific = womenSpecific,
            )
        }
        return out
    }

    /**
     * A real CI name in the tiered list: non-blank, not a structural token. Structural tokens are
     * pure numbers ("0"/"1"), symbol-only cells (✓ ✗ × - –), repeated "Plan N" / "N CI(Unique)"
     * header copy, and "Yes"/"No" membership flags.
     */
    private fun isCiName(raw: String): Boolean {
        val s = raw.trim()
        if (s.isBlank()) return false
        // Pure number / symbol-only / single-letter membership cell.
        if (Regex("""^[\d.]+$""").matches(s)) return false
        if (Regex("""^[✓✗×x✔–\-•·*]+$""", RegexOption.IGNORE_CASE).matches(s)) return false
        if (s.equals("Yes", true) || s.equals("No", true)) return false
        if (s.startsWith("Plan", true) && s.contains(Regex("""\d"""))) return false
        if (Regex("""(?i)\bCI\s*\(?Unique\)?""").containsMatchIn(s) && s.any { it.isDigit() }) return false
        // Must contain at least one letter to be a name.
        return s.any { it.isLetter() }
    }

    // ── Waiting periods ─────────────────────────────────────────────────────
    /** `Sl no,Clause,Waiting Period,Options to modify` (after a title + header row). */
    fun parseWaitingPeriods(csv: String): List<WaitingPeriod> {
        val rows = Csv.parse(csv)
        val out = ArrayList<WaitingPeriod>()
        for (r in rows) {
            val sl = Csv.cell(r, 0).toIntOrNull() ?: continue
            val clause = Csv.cell(r, 1)
            if (clause.isBlank()) continue
            out += WaitingPeriod(
                slNo = sl,
                clause = clause,
                period = Csv.cell(r, 2),
                optionsToModify = Csv.cell(r, 3),
            )
        }
        return out
    }

    // ── Eligibility ─────────────────────────────────────────────────────────
    /**
     * `Eligibility.csv`: keyed rows (col A = label, col B = value). The top block holds the entry/
     * renewal ages, proposer & cover/policy type; the "Risk Categories" block below lists
     * "Risk Class I/II/III" rows whose col B is a comma-separated occupation list → [RiskClass]es.
     */
    fun parseEligibility(csv: String): EligibilityCriteria {
        val rows = Csv.parse(csv)
        var minEntry = "Day1"; var maxEntry = "Lifelong"; var renewal = "Lifelong"
        var proposer = ""; var coverType = ""; var policyType = ""
        val riskClasses = ArrayList<RiskClass>()
        // Once we cross the "Risk Categories" header we switch from key/value boundary rows to
        // parsing the Risk Class occupation lists (instead of stopping).
        var inRiskCategories = false
        for (r in rows) {
            val key = Csv.cell(r, 0)
            val value = Csv.cell(r, 1)
            if (!inRiskCategories && key.startsWith("Risk Categories", true)) { inRiskCategories = true; continue }
            if (inRiskCategories) {
                // "Risk Class I","Risk Class II","Risk Class III" rows → occupation lists.
                if (Regex("""(?i)^Risk\s+Class\b""").containsMatchIn(key)) {
                    riskClasses += RiskClass(
                        name = key.normaliseWs(),
                        occupations = splitList(value).map { it.normaliseWs() }.filter { it.isNotBlank() },
                    )
                }
                continue
            }
            when {
                key.equals("Minimum Entry Age", true) && value.isNotBlank() -> minEntry = value
                key.equals("Maximum Entry Age", true) && value.isNotBlank() -> maxEntry = value
                key.equals("Maximum Renewal Age", true) && value.isNotBlank() -> renewal = value
                key.startsWith("Age of Proposer", true) -> proposer = value
                key.equals("Cover Type", true) -> coverType = value
                key.equals("Policy Type", true) -> policyType = value
            }
        }
        return EligibilityCriteria(
            minEntryAge = minEntry,
            maxEntryAge = maxEntry,
            renewalAge = renewal,
            minEntryAgeYears = numericYears(minEntry),
            maxEntryAgeYears = numericYears(maxEntry),
            proposerType = proposer,
            coverType = coverType,
            policyType = policyType,
            riskClasses = riskClasses,
        )
    }

    private fun numericYears(v: String): Int? = Regex("""\d+""").find(v)?.value?.toIntOrNull()

    // ── PPD / PTD tables ────────────────────────────────────────────────────
    /**
     * `PPD PTD Tables.csv` holds multiple side-by-side AND stacked tables. The real file lays out
     * TWO independent column groups next to each other: a LEFT group in columns 0–2 and a RIGHT
     * group in columns 6–8 (`PTD Table,,,,,,PTD Table,,`). Each group stacks several tables
     * vertically, each introduced by a header token in the group's first column
     * ("PTD Table" / "PPD Table" / "FRACTURES" / "BURNS").
     *
     * We detect each group's start column by where the header tokens appear, mark the leftmost group
     * [PpdPtdSourceGroup.LEFT] and the rest [PpdPtdSourceGroup.RIGHT] (FRACTURES/BURNS tables carry
     * their own source group), then parse each group as its own vertical state machine (slNo /
     * event / percent at group-relative offsets +0/+1/+2). FRACTURES/BURNS become their OWN tables
     * (not folded), with Roman-numeral / A-K letter category headers captured as
     * [PpdPtdRow.category] / [PpdPtdRow.parentSlNo] rather than read as a 0% percent.
     */
    fun parsePpdPtdTables(csv: String): List<PpdPtdTable> {
        val rows = Csv.parse(csv)
        // Discover which columns START a table group (a "...Table"/FRACTURES/BURNS token sits there).
        val headerCols = LinkedHashSet<Int>()
        for (r in rows) {
            r.forEachIndexed { idx, cell ->
                if (isPpdPtdHeaderToken(cell.trim())) headerCols.add(idx)
            }
        }
        // Fallback: if no header tokens found, assume the single left-hand group at column 0.
        val groupCols = headerCols.toList().sorted().ifEmpty { listOf(0) }
        val leftmost = groupCols.minOrNull() ?: 0
        val out = ArrayList<PpdPtdTable>()
        var nameCount = 0
        for (groupCol in groupCols) {
            val colSide = if (groupCol == leftmost) PpdPtdSourceGroup.LEFT else PpdPtdSourceGroup.RIGHT
            out += parsePpdPtdGroup(rows, groupCol, colSide) { nameCount++; nameCount }
        }
        return out
    }

    /** A header token that opens a disability-schedule table within its column group. */
    private fun isPpdPtdHeaderToken(cell: String): Boolean =
        cell.startsWith("PTD Table", true) || cell.startsWith("PPD Table", true) ||
            cell.equals("FRACTURES", true) || cell.equals("BURNS", true)

    /**
     * Parse one vertical column group starting at [groupCol] (slNo=col, event=col+1, percent=col+2).
     * [colSide] is the LEFT/RIGHT position of this column group; FRACTURES/BURNS tables override it
     * with their own source group. PTD/PPD tables keep [colSide]. Roman-numeral / single-letter
     * category headings (which carry NO percent and introduce sub-rows) set the current category and
     * parent sl-no for the rows beneath them instead of being read as 0% rows.
     */
    private fun parsePpdPtdGroup(
        rows: List<List<String>>,
        groupCol: Int,
        colSide: PpdPtdSourceGroup,
        nextNameCount: () -> Int,
    ): List<PpdPtdTable> {
        val out = ArrayList<PpdPtdTable>()
        var current: DisabilityTableType? = null
        var sourceGroup: PpdPtdSourceGroup = colSide
        var name = ""
        var buffer = ArrayList<PpdPtdRow>()
        // Tracks the current category header (Roman/letter) so its sub-rows can reference it.
        var category: String? = null
        var parentSlNo: String? = null

        fun flush() {
            if (current != null && buffer.isNotEmpty()) {
                out += PpdPtdTable(
                    tableType = current!!,
                    name = name,
                    rows = buffer.toList(),
                    sourceGroup = sourceGroup,
                )
            }
            buffer = ArrayList()
            category = null
            parentSlNo = null
        }

        for (r in rows) {
            val a = Csv.cell(r, groupCol)
            val event = Csv.cell(r, groupCol + 1)
            val pctCell = Csv.cell(r, groupCol + 2)
            when {
                a.startsWith("PTD Table", true) -> {
                    flush(); current = DisabilityTableType.PTD; sourceGroup = colSide
                    name = "${colSide.name} PTD Table ${nextNameCount()}"
                }
                a.startsWith("PPD Table", true) -> {
                    flush(); current = DisabilityTableType.PPD; sourceGroup = colSide
                    name = "${colSide.name} PPD Table ${nextNameCount()}"
                }
                // FRACTURES / BURNS now open their OWN table (PPD-style benefit schedule), tagged
                // with the matching source group rather than folded into the surrounding table.
                a.equals("FRACTURES", true) -> {
                    flush(); current = DisabilityTableType.PPD; sourceGroup = PpdPtdSourceGroup.FRACTURES
                    name = "${colSide.name} Fractures ${nextNameCount()}"
                }
                a.equals("BURNS", true) -> {
                    flush(); current = DisabilityTableType.PPD; sourceGroup = PpdPtdSourceGroup.BURNS
                    name = "${colSide.name} Burns ${nextNameCount()}"
                }
                // Column-group header row (Sr No / Sl no / "Type of ...").
                a.startsWith("Sr", true) || a.startsWith("Sl", true) ||
                    (a.isBlank() && event.startsWith("Type", true)) -> { /* header row */ }
                // A reviewer/QA note line ("Note: …") inside the group — not a data row.
                a.startsWith("Note", true) -> { /* skip */ }
                else -> {
                    if (current == null) continue
                    val ev = event.ifBlank { a }
                    if (ev.isBlank() && a.isBlank()) continue
                    // A category-header row: a Roman-numeral / single-letter slNo whose percent cell
                    // is blank AND that introduces a sub-listing ("I,Total loss of hearing in:-," then
                    // "a) Both ears,0.75"; "A,Hip or Pelvis," then "1,Open Fracture,1"). Record the
                    // category; do NOT emit it as a 0% row.
                    if (isCategoryMarker(a) && pctCell.isBlank()) {
                        category = ev.ifBlank { a }
                        parentSlNo = a
                        // Skip a bare category header (no event text adds nothing as a row).
                        continue
                    }
                    // A Roman-numeral row that carries its OWN percent is a top-level item, not a
                    // sub-row — clear any active category so it isn't wrongly nested.
                    if (isCategoryMarker(a) && pctCell.isNotBlank()) {
                        category = null
                        parentSlNo = null
                    }
                    val pct = parsePercentCell(pctCell)
                    // Sub-row = blank slNo (the value sits under a preceding category) OR an a)/b)
                    // sub-letter event beneath a Roman/letter category.
                    val isSub = a.isBlank() || (category != null && Regex("""^[a-z][.)]""", RegexOption.IGNORE_CASE).containsMatchIn(ev))
                    buffer += PpdPtdRow(
                        slNo = a.ifBlank { "-" },
                        insuredEvent = ev,
                        percentOfSI = pct,
                        category = category,
                        parentSlNo = if (isSub) parentSlNo else null,
                        isSubRow = isSub && category != null,
                    )
                }
            }
        }
        flush()
        return out
    }

    /**
     * A category-header marker in the PPD/PTD slNo column: a Roman numeral ("I".."XII") or a single
     * uppercase letter ("A".."K") used to GROUP the rows beneath it (Fractures A-K, PPD Roman list).
     * Pure-integer sl-nos ("1".."42") are real data rows, not category markers.
     */
    private fun isCategoryMarker(slNo: String): Boolean {
        val s = slNo.trim()
        if (s.isEmpty()) return false
        if (Regex("""^[IVXLCDM]+$""").matches(s)) return true // Roman numeral
        if (Regex("""^[A-K]$""").matches(s)) return true // Fractures letter category
        return false
    }

    /** A PPD/PTD percent cell: "0.5"→0.5, "100%"→1.0, assessed-text/blank → 0.0. */
    private fun parsePercentCell(raw: String): Double {
        val s = raw.trim()
        if (s.isBlank()) return 0.0
        val n = s.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: return 0.0
        // Cells already expressed as fractions (0..1) stay as-is; "%" cells are normalised.
        return if (s.contains("%") && n > 1.0) n / 100.0 else n
    }

    // ── Day-care procedures ─────────────────────────────────────────────────
    /**
     * `Day Care List.csv`: a title, then category headings (lines WITHOUT a leading "N.") and
     * numbered items ("1. Stapedotomy …"). Items group under the preceding heading.
     */
    fun parseDayCare(csv: String): List<DayCareProcedure> {
        val rows = Csv.parse(csv)
        val out = ArrayList<DayCareProcedure>()
        var category: String? = null
        var items = ArrayList<DayCareItem>()
        var order = 0
        val numbered = Regex("""^\s*(\d+)[.)]\s*(.+)$""")

        fun flush() {
            if (category != null && items.isNotEmpty()) {
                out += DayCareProcedure(category = category!!, items = items.toList(), displayOrder = order++)
            }
            items = ArrayList()
        }

        for ((i, r) in rows.withIndex()) {
            val line = Csv.cell(r, 0)
            if (line.isBlank()) continue
            if (i == 0 && line.contains("Annexure", true)) continue // title
            val m = numbered.find(line)
            if (m != null) {
                items += DayCareItem(number = m.groupValues[1].toInt(), name = m.groupValues[2].trim())
            } else {
                flush()
                category = line
            }
        }
        flush()
        return out
    }

    // ── Consumables (4 parallel columns) ────────────────────────────────────
    /**
     * `Consumables List.csv`: `Sr No.,List I,Sr No.,List II …,Sr No.,List III …,Sr No.,List IV …`.
     * The four value columns are at indices 1,3,5,7. Emits four [ConsumablesList] entities.
     */
    fun parseConsumables(csv: String): List<ConsumablesList> {
        val rows = Csv.parse(csv)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first()
        val cols = listOf(
            ConsumablesListType.LIST_I to 1,
            ConsumablesListType.LIST_II to 3,
            ConsumablesListType.LIST_III to 5,
            ConsumablesListType.LIST_IV to 7,
        )
        return cols.map { (type, idx) ->
            val title = Csv.cell(header, idx)
            val items = rows.drop(1).mapNotNull { Csv.cell(it, idx).ifBlank { null } }
            ConsumablesList(listType = type, title = title, items = items)
        }.filter { it.items.isNotEmpty() }
    }

    // ── Health check-up packages ────────────────────────────────────────────
    /** `Package Name,Test Details,Package Type`; tests are comma-split from the details cell. */
    fun parseHealthCheckupPackages(csv: String): List<HealthCheckupPackage> {
        val rows = Csv.parse(csv)
        val out = ArrayList<HealthCheckupPackage>()
        var order = 0
        for ((i, r) in rows.withIndex()) {
            val nameCell = Csv.cell(r, 0)
            if (i == 0 && nameCell.equals("Package Name", true)) continue
            if (nameCell.isBlank()) continue
            val tests = splitList(Csv.cell(r, 1))
            val type = if (Csv.cell(r, 2).contains("Center", true)) CheckupPackageType.CENTER_VISIT else CheckupPackageType.HOME_VISIT
            out += HealthCheckupPackage(name = nameCell, tests = tests, packageType = type, displayOrder = order++)
        }
        return out
    }

    // ── Chronic-management OPD grid ─────────────────────────────────────────
    /**
     * The "Chronic Management OPD Grid" inside `Annexure.csv`. Heuristic, column-driven:
     * condition (col A/B), category, GP/IM consult counts, super-specialist, key tests,
     * test frequency and health-coaching. Tolerant — partial rows still produce an entity.
     */
    fun parseChronicOpdGrid(csv: String): List<ChronicOpdGrid> {
        val rows = Csv.parse(csv)
        val out = ArrayList<ChronicOpdGrid>()
        var started = false
        var sawDataRow = false
        var order = 0
        for (r in rows) {
            val a = Csv.cell(r, 0)
            if (!started) {
                if (a.contains("Chronic", true) && a.contains("OPD", true)) started = true
                continue
            }
            // Hard-terminate: once inside the grid, the FIRST blank record or a new section header
            // (the "Sublimits on …" / "Adult Vaccination …" / "List of … Devices" tables that
            // follow the grid) ends it — don't bleed those rows into ChronicOpdGrid entities.
            if (sawDataRow && (Csv.isBlankRecord(r) || isPostGridSectionHeader(r))) break
            if (Csv.isBlankRecord(r)) continue
            // Skip the column-header row ("Condition(s),Category,GP Consults …") and any "Sl no" header.
            if (a.startsWith("Condition", true) || a.startsWith("Sl", true)) continue
            val condition = a.ifBlank { Csv.cell(r, 1) }
            if (condition.isBlank()) continue
            sawDataRow = true
            out += ChronicOpdGrid(
                condition = condition,
                category = Csv.cell(r, 1).ifBlank { "Single" },
                gpConsults = firstInt(r, 2) ?: 0,
                imConsults = firstInt(r, 3) ?: 0,
                superSpecialist = Csv.cell(r, 4),
                keyTests = splitList(Csv.cell(r, 5)),
                testFrequency = Csv.cell(r, 6).ifBlank { "Annual" },
                healthCoaching = splitList(Csv.cell(r, 7)),
                displayOrder = order++,
            )
        }
        return out
    }

    /**
     * A section-header row that introduces a table AFTER the Chronic-OPD grid in the GHI Annexure
     * (the Sublimits / Adult-Vaccination / Medical-Device tables). Used to hard-terminate the grid.
     */
    private fun isPostGridSectionHeader(r: List<String>): Boolean {
        val a = Csv.cell(r, 0)
        if (a.isBlank()) return false
        return a.startsWith("Sublimits on", true) ||
            a.startsWith("Sub - Limit", true) ||
            a.startsWith("Adult Vaccination", true) ||
            a.startsWith("List of Monitoring", true) ||
            a.startsWith("List of ", true) ||
            (a.equals("Sl no", true) && !Csv.cell(r, 1).startsWith("Condition", true))
    }

    // ── Generic + typed annexure lists ──────────────────────────────────────
    /**
     * A buffered annexure section while parsing: a title, the FULL heading row it came from (so a
     * matrix's tier labels — which sit in the heading row's trailing columns — can be recovered) and
     * its data rows.
     */
    private class AnnexureBlock(val title: String, val headerRow: List<String> = emptyList()) {
        val rows = ArrayList<List<String>>()
        var flatNumbering: Boolean = false

        /** True when this block is one device CATEGORY beneath a "List of … Devices" parent. */
        var isDeviceCategory: Boolean = false
    }

    /**
     * `Annexure.csv` holds up to a DOZEN differently-shaped tables stacked vertically (the EE PA/CI
     * `Annexure.csv` carries: OPD-minor / Dental / Psychiatric procedure lists, "List of major
     * illnesses", "List of maternity complications", a "List of CI" tier matrix, a "Sublimits" table,
     * Heart-Cover / Heart-Indemnity / Specific-disease lists, an ADULT-VACCINATIONS schedule and a
     * "List of … Medical Devices" grouping; the GHI `Annexure.csv` adds a per-claim surgical-sublimit
     * table). Each becomes one [Annexure] document; we ALWAYS populate the generic [Annexure.items]
     * string list (back-compat), and additionally route the recognised shapes into typed payloads via
     * [Annexure.type]:
     *
     *  - Sublimit tables (`Sl no,Surgeries,Min,Max` or `S. No.,List of Ailments…,Category,Sub Limit
     *    options`)                                  → [AnnexureType.SUBLIMIT_TABLE] + [Annexure.sublimitRows]
     *  - The "List of CI" a/r tier matrix           → [AnnexureType.CI_TIER_MATRIX] + [Annexure.ciTierMatrix]
     *  - The `Sl no,ADULT VACCINATIONS` schedule    → [AnnexureType.VACCINATION_LIST]
     *  - The "List of … Devices" category grouping  → [AnnexureType.DEVICE_CATEGORIES]
     *  - OPD minor procedures                       → [AnnexureType.OPD_MINOR_PROC]
     *  - everything else                            → [AnnexureType.GENERIC]
     *
     * Because col-0 numbers RESTART across tables, codes are minted from a single global counter
     * ("ANX_1".."ANX_N") so later tables never overwrite earlier ones.
     */
    fun parseAnnexures(csv: String): List<Annexure> {
        val rows = Csv.parse(csv)
        val blocks = ArrayList<AnnexureBlock>()
        var current: AnnexureBlock? = null
        // Set once we pass a "List of … Devices" parent title; the numbered children that follow are
        // device CATEGORIES (each its own block) rather than plain illness lists.
        var devicesMode = false

        for (r in rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            // A numbered col-0 row is a HEADING when col 1 is a non-numeric title (section headers
            // like "6,List of CI,…" and "1,Cardio-Respiratory Monitoring Devices"), and an ITEM when
            // col 1 is itself a sl-no number (CI / sublimit rows "1,Cancer …") OR when we're inside a
            // flat-numbered block (the vaccinations list "1,Influenza").
            val numberedNonNumericB = a.toIntOrNull() != null && b.isNotBlank() && b.toIntOrNull() == null
            val flat = current?.flatNumbering == true
            when {
                // "Sl no, ADULT VACCINATIONS" → starts a flat-numbered (col-0 = item sl-no) block.
                a.equals("Sl no", true) && b.isNotBlank() -> {
                    devicesMode = false
                    current = AnnexureBlock(b, r).also { it.flatNumbering = true; blocks.add(it) }
                }
                // Inside a flat block, numbered col-0 rows are this block's items, not new headings.
                flat && numberedNonNumericB -> current?.rows?.add(r)
                // A numbered heading with a non-numeric title in col 1 → new titled block. The full
                // heading row is kept so a tier matrix can read its tier labels (cols 3+). Under a
                // devices parent, the numbered heading is a device category.
                numberedNonNumericB -> {
                    current = AnnexureBlock(b, r).also { it.isDeviceCategory = devicesMode; blocks.add(it) }
                }
                // A bare "List of … Devices" title alone in col 0 → it is the devices PARENT title;
                // its numbered children below are the actual device-category blocks.
                a.startsWith("List of", true) && b.isBlank() -> {
                    devicesMode = a.contains("Device", true) || a.contains("Monitoring", true)
                    current = AnnexureBlock(a, r).also { blocks.add(it) }
                }
                // Any other non-blank row belongs to the current block as a data row.
                !Csv.isBlankRecord(r) -> current?.rows?.add(r)
            }
        }

        var order = 0
        val out = ArrayList<Annexure>()
        for (block in blocks) {
            val anx = buildAnnexure(block, order + 1) ?: continue
            order++
            out += anx.copy(annexureCode = "ANX_$order", displayOrder = order - 1)
        }
        return out
    }

    /** Classify a buffered [AnnexureBlock] into a typed [Annexure], or null when it has no items. */
    private fun buildAnnexure(block: AnnexureBlock, ordinal: Int): Annexure? {
        val title = block.title
        // Sublimit tables: rows whose tail carries a Min/Max or a "<<lo to hi>>" range.
        val sublimitRows = parseAnnexureSublimitRows(block.rows)
        if (sublimitRows.isNotEmpty()) {
            return Annexure(
                annexureCode = "ANX_$ordinal",
                title = title,
                category = categoryFor(title),
                type = AnnexureType.SUBLIMIT_TABLE,
                items = sublimitRows.map {
                    listOf(it.label, it.min, it.max).filter { c -> c.isNotBlank() }.joinToString(" : ")
                },
                sublimitRows = sublimitRows,
            )
        }
        // CI tier matrix: rows with a/r (or ✓/×) tier flags across the columns after the name.
        val ciMatrix = parseAnnexureCiTierMatrix(block.headerRow, block.rows)
        if (ciMatrix.isNotEmpty()) {
            return Annexure(
                annexureCode = "ANX_$ordinal",
                title = title,
                category = categoryFor(title),
                type = AnnexureType.CI_TIER_MATRIX,
                items = ciMatrix.map { it.condition },
                ciTierMatrix = ciMatrix,
            )
        }
        // Otherwise a plain list (OPD minor / vaccinations / illness lists / one device category).
        // Items come from col 1 (and col 2 when col 1 is a sl-no), skipping sub-headers/notes/labels.
        val items = ArrayList<String>()
        val instructions = ArrayList<String>()
        for (r in block.rows) {
            val a = Csv.cell(r, 0)
            val b = Csv.cell(r, 1)
            val c = Csv.cell(r, 2)
            // "1,Influenza" flat rows (col 0 = sl-no) → item is col 1.
            val text = when {
                a.toIntOrNull() != null && b.isNotBlank() && b.toIntOrNull() == null -> b
                // "…,1,Cancer …" rows (col 1 = sl-no, col 2 = name) → item is col 2.
                b.toIntOrNull() != null && c.isNotBlank() -> c
                else -> listOf(b, c).filter { it.isNotBlank() }.joinToString(" ")
            }
            val v = text.normaliseWs()
            if (v.isBlank()) continue
            // Capture "Note:" / instruction lines into instructions, not items.
            if (v.startsWith("Note:", true) || v.startsWith("Note :", true)) { instructions += v; continue }
            if (isAnnexureNoise(v)) continue
            items.add(v)
        }
        if (items.isEmpty()) return null
        val type = when {
            block.isDeviceCategory || (title.contains("Device", true) && title.contains("Monitoring", true)) ->
                AnnexureType.DEVICE_CATEGORIES
            title.contains("VACCINATION", true) -> AnnexureType.VACCINATION_LIST
            title.contains("OPD", true) || title.contains("Minor Procedure", true) -> AnnexureType.OPD_MINOR_PROC
            else -> AnnexureType.GENERIC
        }
        return Annexure(
            annexureCode = "ANX_$ordinal",
            title = title,
            category = categoryFor(title),
            type = type,
            items = items,
            instructions = instructions,
        )
    }

    /**
     * Parse a block's rows as [AnnexureSublimitRow]s when they carry sublimit bounds — either the GHI
     * "Sl no,Surgeries,Min,Max" shape (Min/Max in cols 2/3) or the EE "S. No.,…,Category,Sub Limit
     * options" shape ("<<10000 to 1,00,000>>" range in col 3/4). Returns empty when not a sublimit
     * table.
     */
    private fun parseAnnexureSublimitRows(rows: List<List<String>>): List<AnnexureSublimitRow> {
        val out = ArrayList<AnnexureSublimitRow>()
        for (r in rows) {
            // Sl-no can be col 0 (GHI) or col 1 (EE, where col 0 is the section-blank); the surgery
            // name is the cell right after it, and the bound(s) follow.
            val (slCol, slNo) = when {
                Csv.cell(r, 0).toIntOrNull() != null -> 0 to Csv.cell(r, 0).toInt()
                Csv.cell(r, 1).toIntOrNull() != null -> 1 to Csv.cell(r, 1).toInt()
                else -> continue
            }
            val name = Csv.cell(r, slCol + 1)
            if (name.isBlank() || isAnnexureNoise(name)) continue
            // Collect candidate bound cells after the name. A bound LOOKS like a sublimit value: a
            // money/percent/range cell ("10% of SI Opted", "<<10000 to 1,00,000>>", "1,00,000"), NOT
            // a single tier-flag letter ("a"/"r") or membership word — those belong to a CI matrix.
            val tail = ((slCol + 2) until r.size).map { Csv.cell(r, it) }
                .filter { it.isNotBlank() && looksLikeSublimitBound(it) }
            if (tail.isEmpty()) continue
            // "<<10000 to 1,00,000>>" / "10% of SI Opted" packed range in a single cell.
            val packed = tail.firstOrNull { it.contains("<<") || it.contains(" to ", true) }
            val (min, max) = when {
                packed != null -> splitRangeText(packed)
                tail.size >= 2 -> tail[tail.size - 2] to tail.last()
                else -> "" to tail[0]
            }
            if (min.isBlank() && max.isBlank()) continue
            out += AnnexureSublimitRow(slNo = slNo, label = name.normaliseWs(), min = min, max = max)
        }
        // Only treat as a sublimit table if MOST rows carried bounds (avoid mis-classifying a plain
        // "1,Influenza" list whose col 2 happens to be blank — those produce no rows here anyway).
        return if (out.size >= 2) out else emptyList()
    }

    /** Split "10% of SI Opted" left + right, or "<<10000 to 1,00,000>>" → ("10000","1,00,000"). */
    private fun splitRangeText(raw: String): Pair<String, String> {
        val s = raw.trim().removePrefix("<<").removeSuffix(">>").trim()
        val parts = Regex("""(?i)\s+to\s+""").split(s)
        return if (parts.size >= 2) parts[0].trim() to parts.drop(1).joinToString(" to ").trim() else s to ""
    }

    /**
     * A cell that reads as a sublimit bound (money / percent / SI range), distinguishing a real
     * sublimit table from a CI tier matrix whose trailing cells are single flag letters ("a"/"r").
     */
    private fun looksLikeSublimitBound(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        return s.contains("%") || s.contains("<<") || s.contains("SI", true) ||
            (s.any { it.isDigit() } && s.length >= 2)
    }

    /**
     * Parse a block's rows as a CI tier matrix when col 1 is a sl-no, col 2 is the CI name, and the
     * remaining cells are per-tier membership flags (a/r or ✓/×). Tier labels come from the section
     * heading row ("…,List of CI,Critical Illness,9 Cls,11 Cls,…") — which the parser preserved as
     * [headerRow] — falling back to a header-shaped data row. Each row's `tiers` lists its labels.
     */
    private fun parseAnnexureCiTierMatrix(headerRow: List<String>, rows: List<List<String>>): List<AnnexureCiTierRow> {
        // Header row of the matrix: col 2 ≈ "Critical Illness", cols 3+ are tier labels ("9 Cls"…).
        fun isMatrixHeader(r: List<String>): Boolean =
            Csv.cell(r, 2).contains("Critical Illness", true) ||
                ((3 until r.size).count { Csv.cell(r, it).contains(Regex("""(?i)\d+\s*C[il]s""")) } >= 2)
        val header = headerRow.takeIf { isMatrixHeader(it) } ?: rows.firstOrNull { isMatrixHeader(it) }
        ?: return emptyList()
        val tierLabels = (3 until header.size).map { Csv.cell(header, it) }
        if (tierLabels.all { it.isBlank() }) return emptyList()
        val out = ArrayList<AnnexureCiTierRow>()
        for (r in rows) {
            if (r === header) continue
            val slNo = Csv.cell(r, 1).toIntOrNull() ?: continue
            val name = Csv.cell(r, 2)
            if (name.isBlank()) continue
            // A tier flag of "a" (available) / "✓" / "Yes" means the CI is in that tier.
            val tiers = ArrayList<String>()
            for (i in tierLabels.indices) {
                val flag = Csv.cell(r, 3 + i).trim()
                val label = tierLabels[i]
                if (label.isBlank()) continue
                if (flag.equals("a", true) || flag == "✓" || flag.equals("Yes", true) || flag.equals("Found", true)) {
                    tiers.add(label)
                }
            }
            out += AnnexureCiTierRow(condition = "$slNo. ${name.normaliseWs()}", tiers = tiers)
        }
        return if (out.size >= 2) out else emptyList()
    }

    /** True for in-block noise rows: column labels, sub-headers, the "List of …" line. */
    private fun isAnnexureNoise(raw: String): Boolean {
        val v = raw.normaliseWs()
        if (v.isBlank()) return true
        if (v.startsWith("List of", true)) return true
        if (Regex("""^(S\.?\s*No\.?|Sr\s*No|Sl\s*no)\b""", RegexOption.IGNORE_CASE).containsMatchIn(v)) return true
        if (v.equals("Surgeries", true) || v.equals("Category", true) || v.equals("Min", true) ||
            v.equals("Max", true) || v.equals("Sub Limit options", true)
        ) {
            return true
        }
        return false
    }

    private fun categoryFor(title: String): AnnexureCategory = when {
        title.contains("OPD", true) || title.contains("Minor Procedure", true) -> AnnexureCategory.OPD_MINOR_PROCEDURES
        title.contains("Exclusion", true) -> AnnexureCategory.EXCLUSIONS
        title.contains("Consumable", true) -> AnnexureCategory.CONSUMABLES
        title.contains("Day Care", true) -> AnnexureCategory.DAY_CARE_PROCEDURES
        title.contains("Modern", true) -> AnnexureCategory.MODERN_TREATMENTS
        title.contains("Definition", true) -> AnnexureCategory.DEFINITIONS
        else -> AnnexureCategory.OTHER
    }

    // ── Covers from a section CSV (D1-*/D2-* / PBT-PA PHI benefit files) ─────
    /**
     * Resolved column layout for one benefit file. Each benefit CSV (`D1-PA`, `D1-CI`, `D1-HC`,
     * `D1-Loan EMI`, `D2-OPD`, `PBT PA`) has its own header; we resolve the indices by label so
     * one parser handles them all. [coverageCols] are the text columns scanned for inline options
     * / survival-period / deductible / max-duration extraction.
     */
    private data class CoverColumns(
        val slNoCol: Int = 0,
        val nameCol: Int = 1,
        val triggerCol: Int = -1,
        val coverageCol: Int = -1,
        val minCol: Int = -1,
        val maxCol: Int = -1,
        val sublimitCol: Int = -1,
        val payoutCol: Int = -1,
        val percentCol: Int = -1,
        val additionalCol: Int = -1,
        val changeCol: Int = -1,
    ) {
        /** Text columns (in priority order) scanned for inline options & embedded structures. */
        val coverageCols: List<Int>
            get() = listOf(coverageCol, additionalCol, sublimitCol, minCol, maxCol).filter { it >= 0 }
    }

    /**
     * Parse the named benefit files into [Cover]s — robust to EACH file's real columns. Resolves
     * the header row by label so D1-PA (Trigger/Details/Min/Max/Payout/Change), D1-CI
     * (Coverage + survival/CI-count sub-rows), D1-HC (Options: deductible / max-payable-duration),
     * D1-Loan EMI (Trigger/Coverage/Sublimit-Options) and D2-OPD (Cover Description / Additional
     * Details Option 1/2/3) / PBT-PA (Percentage of SI + Additional Details) all map cleanly.
     *
     * Populates trigger, coverageText, min/max SI, payoutType, rateKind, percentOfSI, changeNote,
     * options[] (inline `a)/Option N:` AND blank-S.No continuation sub-rows), survivalPeriod[],
     * sublimits[], deductibleOptions[] and maxPayableDuration[]. Blank-S.No rows after a data row
     * are folded into the preceding cover as extra options/sub-fields (they are option/detail
     * continuations, never standalone covers).
     */
    fun parseCoversFromSection(
        csv: String,
        productLine: ProductLine = ProductLine.GROUP,
        sectionNumber: String = "",
    ): List<Cover> {
        val rows = Csv.parse(csv)
        val out = ArrayList<Cover>()
        var order = 0
        var cols: CoverColumns? = null
        for (r in rows) {
            val a = Csv.cell(r, 0)
            // Title rows ("Section 1 : Personal Accident", "1.1 Personal Accident",
            // "Product cover Table", "1.PERSONAL ACCIDENT") sit before/between the real header(s).
            if (a.startsWith("Section", true) || a.startsWith("Product cover Table", true)) continue
            if (cols == null) {
                if (isCoverHeaderRow(r)) cols = resolveCoverColumns(r)
                continue
            }
            val c = cols
            // A repeated header row (PBT-PA has one per sub-table) re-resolves the columns.
            if (isCoverHeaderRow(r)) { cols = resolveCoverColumns(r); continue }
            if (Csv.isBlankRecord(r)) continue
            val slNo = Csv.cell(r, c.slNoCol)
            val nameCell = Csv.cell(r, c.nameCol)
            // PBT-PA sub-table headings sit in the Sl-No / name column with no real Sl No
            // ("A.Base Covers", "B. Optional Covers", "1.PERSONAL ACCIDENT") — skip them.
            if (isCoverSubTableHeading(slNo, nameCell)) continue
            // A valid cover row has an integer Sl No or an alpha-suffixed one ("1a","1b","1c").
            val isCoverRow = slNo.toIntOrNull() != null ||
                Regex("""^\d+[a-z]$""", RegexOption.IGNORE_CASE).matches(slNo)
            if (!isCoverRow) {
                // Blank Sl No with content → option/detail continuation of the previous cover.
                if (nameCell.isNotBlank() && out.isNotEmpty()) {
                    out[out.lastIndex] = foldContinuationRow(out.last(), r, c)
                }
                continue
            }
            val coverName = nameCell.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (coverName.isBlank()) continue

            val trigger = if (c.triggerCol >= 0) Csv.cell(r, c.triggerCol).normaliseWs() else ""
            val coverageText = if (c.coverageCol >= 0) Csv.cell(r, c.coverageCol) else ""
            val additional = if (c.additionalCol >= 0) Csv.cell(r, c.additionalCol) else ""
            val sublimitText = if (c.sublimitCol >= 0) Csv.cell(r, c.sublimitCol) else ""
            val payout = if (c.payoutCol >= 0) Csv.cell(r, c.payoutCol) else ""
            val minText = if (c.minCol >= 0) Csv.cell(r, c.minCol) else ""
            val maxText = if (c.maxCol >= 0) Csv.cell(r, c.maxCol) else ""
            val changeNote = if (c.changeCol >= 0) Csv.cell(r, c.changeCol).normaliseWs() else ""
            val percentOfSI = if (c.percentCol >= 0) parsePercentOfSI(Csv.cell(r, c.percentCol)) else 0.0

            // Options come from every coverage/detail/sublimit/min/max text column on this row.
            val options = c.coverageCols
                .flatMap { parseInlineOptions(Csv.cell(r, it)) }
                .distinctBy { it.label }
                .take(20)
            // All text scanned for embedded labelled-day lists (survival / deductible / max-payable).
            val allText = (c.coverageCols.map { Csv.cell(r, it) } +
                listOf(trigger, payout)).filter { it.isNotBlank() }.joinToString("\n")

            val isFixed = payout.contains("Fixed", true)
            val isService = payout.contains("Service", true)
            val isPercent = percentOfSI > 0.0 || payout.contains("%") || payout.contains("Percent", true)

            // Pre-compute the typed option sets so they can also seed the categorised optionGroups.
            val initialWaiting = extractLabelledList(allText, "Initial Waiting", "Initial waiting")
            val specificWaiting = extractLabelledList(allText, "Specific Disease waiting", "Specific Disease Waiting", "Specific waiting")
            val pedWaiting = extractLabelledList(allText, "Pre- Existing Diseases Waiting", "Pre-Existing Disease", "Waiting period on PED", "PED Waiting", "Waiting Period on PED")
            val maternityWaiting = if (coverName.contains("Maternity", true) || coverName.contains("New Born", true)) {
                extractLabelledList(allText, "Waiting Period", "Waiting period")
            } else {
                emptyList()
            }
            val icuMultiplier = extractLabelledList(allText, "ICU Limit as a multiple", "ICU Limit", "Multiplier")
            val optionGroups = buildList {
                if (initialWaiting.isNotEmpty()) add(coverOptionGroup("Initial Waiting Period", initialWaiting))
                if (specificWaiting.isNotEmpty()) add(coverOptionGroup("Specific Disease Waiting Period", specificWaiting))
                if (pedWaiting.isNotEmpty()) add(coverOptionGroup("PED Waiting Period", pedWaiting))
                if (maternityWaiting.isNotEmpty()) add(coverOptionGroup("Maternity Waiting Period", maternityWaiting))
                if (icuMultiplier.isNotEmpty()) add(coverOptionGroup("ICU Multiplier", icuMultiplier))
            }
            out += Cover(
                code = toCoverCode(coverName),
                // Placeholder = the PBT section NUMBER; CatalogSeeder resolves it to the Section id.
                sectionRef = sectionNumber,
                productLine = productLine,
                name = coverName,
                description = (coverageText.ifBlank { additional }.ifBlank { nameCell }).normaliseWs().take(500),
                trigger = trigger,
                coverageText = listOf(coverageText, additional, sublimitText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .normaliseWs()
                    .take(2000),
                minSumInsured = parseMoneyLimit(minText),
                maxSumInsured = parseMoneyLimit(maxText),
                payoutType = when {
                    isFixed -> PayoutType.FIXED
                    isService -> PayoutType.SERVICE
                    isPercent -> PayoutType.PERCENT
                    else -> PayoutType.INDEMNITY
                },
                rateKind = when {
                    isFixed -> RateKind.FLAT
                    percentOfSI > 0.0 -> RateKind.PERCENT_MULTIPLIER
                    else -> RateKind.PERCENT_MULTIPLIER
                },
                options = options,
                percentOfSI = percentOfSI,
                changeNote = changeNote,
                survivalPeriod = extractLabelledDays(allText, "survival"),
                sublimits = (parseSublimits(sublimitText) + parseSublimits(coverageText))
                    .distinctBy { it.label }
                    .take(12),
                deductibleOptions = extractLabelledDays(allText, "deductible"),
                maxPayableDuration = (
                    extractLabelledDays(allText, "max payable") +
                        extractLabelledList(allText, "Limit for no. of days", "Days per Policy Year", "Days per Policy")
                    ).distinct().take(20),
                // Typed waiting-period option sets (HC / CI / per-cover modify lists).
                initialWaitingOptions = initialWaiting,
                specificWaitingOptions = specificWaiting,
                pedWaitingOptions = pedWaiting,
                maternityWaitingOptions = maternityWaiting,
                icuMultiplierOptions = icuMultiplier,
                franchiseOptions = extractLabelledList(allText, "Franchise"),
                optionGroups = optionGroups,
                emiPaymentOptions = extractLabelledList(allText, "No. of EMI", "EMI options", "Payment Option", "EMI per event"),
                payoutLinkageBases = extractPayoutLinkageBases(allText),
                coveredMemberCategories = extractMemberCategories(allText),
                // Hospital-Daily-Cash limit options (Section 3 "Daily Cash cover" 500→50,000 range).
                dailyHospitalizationLimitOptions = if (coverName.contains("Daily Cash", true) || coverName.contains("Hospital Cash", true)) {
                    extractLabelledList(allText, "Per Day", "Per day", "Daily limit", "Daily Cash")
                } else {
                    emptyList()
                },
                // Link disability covers to the PPD/PTD payout table they reference.
                ppdPtdTableRef = ppdPtdRefFor(coverName, allText),
                displayOrder = order++,
                importAliases = listOf(coverName),
            )
        }
        return out
    }

    /**
     * A non-resolving placeholder ref for the PPD/PTD payout table a disability cover uses, when the
     * cover names / text references one ("As per PTD Table", "As per PPD Table", PTD/PPD covers). The
     * [CatalogSeeder] resolves this placeholder to a real [PpdPtdTable] id (or leaves it as a hint).
     */
    private fun ppdPtdRefFor(coverName: String, text: String): String? {
        val n = "$coverName\n$text"
        return when {
            Regex("""(?i)\bPTD\s+Table\b|Permanent\s+Total\s+Disab""").containsMatchIn(n) -> "PTD"
            Regex("""(?i)\bPPD\s+Table\b|Permanent\s+Partial\s+Disab""").containsMatchIn(n) -> "PPD"
            else -> null
        }
    }

    /** Is this the column-header row of a benefit file (starts with an S.No / Sl No label)? */
    private fun isCoverHeaderRow(r: List<String>): Boolean {
        val a = Csv.cell(r, 0).lowercase().replace(".", "").replace(" ", "")
        return a == "sno" || a == "slno"
    }

    /** Resolve the per-file column indices from the header row by label. */
    private fun resolveCoverColumns(header: List<String>): CoverColumns {
        var name = 1; var trigger = -1; var coverage = -1; var min = -1; var max = -1
        var sublimit = -1; var payout = -1; var percent = -1; var additional = -1; var change = -1
        header.forEachIndexed { idx, h ->
            val hh = h.trim().lowercase()
            when {
                hh.isBlank() -> {}
                hh.contains("name of benefit") || hh == "cover" || hh == "covers" ||
                    (hh.contains("benefit") && name == 1 && idx > 0) -> name = idx
                hh == "trigger" -> trigger = idx
                hh.contains("details of coverage") || hh == "coverage" ||
                    hh.contains("cover description") -> if (coverage < 0) coverage = idx
                hh.startsWith("min") || hh == "minimum" -> if (min < 0) min = idx
                hh.startsWith("max") || hh == "maximum" -> if (max < 0) max = idx
                hh.startsWith("sublimit") || hh.startsWith("sub limit") || hh.startsWith("sub-limit") -> sublimit = idx
                hh == "payout" -> payout = idx
                hh.contains("percentage of si") || hh.contains("percent of si") -> percent = idx
                hh.contains("additional details") -> additional = idx
                hh.startsWith("change") -> change = idx
            }
        }
        return CoverColumns(
            slNoCol = 0, nameCol = name, triggerCol = trigger, coverageCol = coverage,
            minCol = min, maxCol = max, sublimitCol = sublimit, payoutCol = payout,
            percentCol = percent, additionalCol = additional, changeCol = change,
        )
    }

    /**
     * Fold a blank-Sl-No continuation row into [cover]. These rows (D1-CI "No of CI Covered",
     * "Survival Period", "Initial Waiting period"…) carry a labelled detail in [c.nameCol] whose
     * value sits in [c.coverageCol]; we record them as cover options (and survival → survivalPeriod).
     */
    private fun foldContinuationRow(cover: Cover, r: List<String>, c: CoverColumns): Cover {
        val label = Csv.cell(r, c.nameCol).normaliseWs()
        // The value of a labelled sub-row sits in the coverage column (D1-CI), falling back to the
        // first non-blank cell after the name column for files where it lands elsewhere.
        val value = (if (c.coverageCol >= 0) Csv.cell(r, c.coverageCol).normaliseWs() else "")
            .ifBlank { firstNonBlankAfter(r, c.nameCol).normaliseWs() }
        if (label.isBlank() && value.isBlank()) return cover
        val combined = listOf(label, value).filter { it.isNotBlank() }.joinToString(" : ")
        val labelled = "$label : $value"
        val survival = if (label.contains("survival", true)) extractLabelledDays(labelled, "survival")
            .ifEmpty { splitList(value) } else emptyList()
        val deductible = if (label.contains("deductible", true)) splitList(value) else emptyList()
        val maxPayable = if (label.contains("max payable", true) || label.contains("payable duration", true))
            splitList(value) else emptyList()
        // Route the D1-CI / labelled waiting sub-rows into the typed waiting-period option fields.
        val initialWaiting = if (label.contains("Initial Waiting", true) || label.contains("Initial waiting", true)) {
            splitList(value).flatMap { it.split('/').map { p -> p.trim() } }.filter { it.any(Char::isDigit) }
        } else {
            emptyList()
        }
        val pedWaiting = if (label.contains("PED", true) || label.contains("Pre- Existing", true) ||
            label.contains("Pre-Existing", true)
        ) {
            splitList(value).flatMap { it.split('/').map { p -> p.trim() } }.filter { it.any(Char::isDigit) }
        } else {
            emptyList()
        }
        val specificWaiting = if (label.contains("Specific Disease", true) || label.contains("Specific waiting", true)) {
            splitList(value).flatMap { it.split('/').map { p -> p.trim() } }.filter { it.any(Char::isDigit) }
        } else {
            emptyList()
        }
        return cover.copy(
            options = (cover.options + toCoverOption(combined.take(160)))
                .distinctBy { it.label }.take(24),
            survivalPeriod = (cover.survivalPeriod + survival).distinct(),
            deductibleOptions = (cover.deductibleOptions + deductible).distinct(),
            maxPayableDuration = (cover.maxPayableDuration + maxPayable).distinct(),
            initialWaitingOptions = (cover.initialWaitingOptions + initialWaiting).distinct(),
            pedWaitingOptions = (cover.pedWaitingOptions + pedWaiting).distinct(),
            specificWaitingOptions = (cover.specificWaitingOptions + specificWaiting).distinct(),
            optionsParam2 = if (survival.isNotEmpty()) {
                (cover.optionsParam2 + survival.map { CoverOption(label = it) }).distinctBy { it.label }
            } else {
                cover.optionsParam2
            },
        )
    }

    /** First non-blank cell strictly after column [idx] (for continuation-value fallback). */
    private fun firstNonBlankAfter(record: List<String>, idx: Int): String {
        for (i in (idx + 1) until record.size) {
            val v = Csv.cell(record, i)
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    /** Split a comma/slash/newline-separated cell into a clean list of non-blank tokens. */
    private fun splitList(value: String): List<String> =
        value.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Stable engine code from a display name: lowercase, alnum→underscore, collapse repeats. */
    fun toCoverCode(name: String): String =
        name.trim().lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .trim('_')
            .ifBlank { "cover" }

    /**
     * Parse a rupee/limit cell into [Money]. Handles "INR 25,00,00,000", "300 Lacs", "10000",
     * "₹1,00,000", "Covered"/"Up to …" (→ ZERO). Lacs are multiplied by 1e5.
     */
    fun parseMoneyLimit(raw: String): Money {
        val s = raw.trim()
        if (s.isBlank()) return Money.ZERO
        // Require a unit token at a word boundary so incidental "cr"/"lac" inside words
        // ("Critical", "place") don't inflate a sublimit's parsed limit.
        val lacs = Regex("""(?i)\b(lac|lacs|lakh|lakhs|l)\b""").containsMatchIn(s)
        val crore = Regex("""(?i)\b(cr|crore|crores)\b""").containsMatchIn(s)
        val digits = s.replace(Regex("""[^0-9.]"""), "")
        val num = digits.toDoubleOrNull() ?: return Money.ZERO
        val rupees = when {
            crore -> num * 1_00_00_000.0
            lacs -> num * 1_00_000.0
            else -> num
        }
        return Money.fromRupees(rupees)
    }

    private fun firstInt(record: List<String>, idx: Int): Int? =
        Csv.cell(record, idx).replace(Regex("""[^0-9]"""), "").toIntOrNull()

    /**
     * Pull option labels out of a benefit-detail cell. Handles the three real shapes:
     *  - line-led lists: "a. Deductible : …", "a) …", "1. …", "Option 1: …", "Option 2: …"
     *  - inline "a. x  b. y" runs split onto one line
     *  - slash lists: "0/1/2/3/5 days", "9 / 11/ 15/ 25" (kept whole as one option label)
     * Each becomes one [CoverOption]; we cap to 12 to keep documents bounded.
     */
    private fun parseInlineOptions(cell: String): List<CoverOption> {
        if (cell.isBlank()) return emptyList()
        // First split inline "a. x  b. y" / "Option 1: x Option 2: y" runs onto their own lines.
        val expanded = cell
            .replace(Regex("""(?<!^)\s+(?=(?:Option\s*\d|[a-z][.)])\s)""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""\s+(?=Option\s*\d)""", RegexOption.IGNORE_CASE), "\n")
        val lines = expanded.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val optionMarker = Regex("""^(?:[a-z][.)]|Option\s*\d+\s*:?|\d+[.)])""", RegexOption.IGNORE_CASE)
        return lines
            .filter { optionMarker.containsMatchIn(it) }
            .map { toCoverOption(it.take(160)) }
            .distinctBy { it.label }
            .take(12)
    }

    /**
     * Build a typed [CoverOption] from a raw option line — classifying its [CoverParamType]
     * (PERCENT_OF_SI / FIXED_AMOUNT / COUNT) and capturing rupee bounds ([minLimit]/[maxLimit]) or a
     * free-text [rangeText] ("Rs 5,000 to 50,000", "1% to 100%", "0/1/2/3 days").
     */
    private fun toCoverOption(label: String): CoverOption {
        val s = label.trim()
        val hasPercentOfSI = Regex("""(?i)%\s*(of)?\s*SI|of\s+SI|of\s+Sum\s+Insured|%""").containsMatchIn(s) &&
            (s.contains("SI", true) || s.contains("Sum Insured", true) || s.contains("%"))
        // Rupee amounts present → FIXED_AMOUNT with parsed min/max bounds.
        val monies = Regex("""(?i)(?:INR|Rs\.?|₹)?\s*[\d,]+\s*(?:Lac|Lacs|Lakh|Lakhs|Cr|Crore|Crores)?""")
            .findAll(s)
            .map { it.value }
            .filter { it.any { ch -> ch.isDigit() } }
            .map { parseMoneyLimit(it) }
            .filter { it != Money.ZERO }
            .toList()
        // A slash/"to"/comma value range becomes the option's rangeText.
        val rangeText = when {
            s.contains(" to ", true) -> s.substringAfter(':', s).trim().ifBlank { null }
            Regex("""\d+\s*/\s*\d+""").containsMatchIn(s) -> s.substringAfter(':', s).trim().ifBlank { null }
            else -> null
        }
        val paramType = when {
            s.contains("%") || hasPercentOfSI -> CoverParamType.PERCENT_OF_SI
            monies.isNotEmpty() -> CoverParamType.FIXED_AMOUNT
            // Plain integer-count options ("Days", "EMI", "X times", session counts).
            Regex("""(?i)\b(day|days|month|months|EMI|times|session|sessions|visit|visits|week|weeks|X)\b""")
                .containsMatchIn(s) -> CoverParamType.COUNT
            else -> CoverParamType.FIXED_AMOUNT
        }
        return CoverOption(
            label = s,
            logic = CoverageLogic.PART_OF_BASE_SI,
            paramType = paramType,
            minLimit = monies.minOrNull(),
            maxLimit = monies.maxOrNull().takeIf { monies.size > 1 },
            rangeText = rangeText?.take(160),
        )
    }

    /** Build an exclusive [CoverOptionGroup] from a category label + its value tokens. */
    private fun coverOptionGroup(category: String, values: List<String>): CoverOptionGroup =
        CoverOptionGroup(
            category = category,
            exclusive = true,
            options = values.distinct().take(20).map { toCoverOption(it) },
        )

    /** Collapse runs of internal whitespace (incl. embedded newlines) into single spaces; trim. */
    private fun String.normaliseWs(): String =
        Regex("""\s+""").replace(this, " ").trim()

    /**
     * Parse a "Percentage of SI" cell into a fraction-of-1 percent value. "0.5%"→0.5, "x%"→0.0
     * (variable), "1% to 100%"→100.0 (the max), "NA"/blank/non-% copy→0.0.
     */
    private fun parsePercentOfSI(raw: String): Double {
        val s = raw.trim()
        if (s.isBlank() || s.equals("NA", true) || s.equals("N/A", true)) return 0.0
        if (!s.contains("%")) return 0.0
        // Take the LAST numeric percent in the cell (handles "1% to 100%" → 100).
        val nums = Regex("""(\d+(?:\.\d+)?)\s*%""").findAll(s).map { it.groupValues[1].toDouble() }.toList()
        return nums.maxOrNull() ?: 0.0
    }

    /**
     * Extract a slash/comma list of days/months that follows a labelled prefix anywhere in [text].
     * e.g. label="deductible" → from "a. Deductible : 0/1/2/3/5 days" yields ["0","1","2","3","5"];
     * label="max payable" → from "Max payable Duration Per Year : 5/10/30/60/90/180 days" yields
     * ["5","10","30","60","90","180"]; label="survival" → "7 days/ 15 days/ … / Waived".
     */
    private fun extractLabelledDays(text: String, label: String): List<String> {
        if (text.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (!line.contains(label, ignoreCase = true)) continue
            // Take the segment after the first ':' (or after the label) — the value list.
            val afterColon = line.substringAfter(':', line.substringAfter(label, "")).trim()
            val value = afterColon.ifBlank { line }
            // Split on slashes/commas; keep numeric / "Waived"-style tokens.
            value.split('/', ',')
                .map { tok -> tok.replace(Regex("""(?i)\b(days?|months?|Per Year|Per day|upto|up to)\b"""), "").trim() }
                .map { it.trim('.', ' ') }
                .filter { it.isNotEmpty() && (it.any { ch -> ch.isDigit() } || it.equals("Waived", true)) }
                .forEach { out.add(it) }
        }
        return out.toList().take(20)
    }

    /**
     * Extract the slash/comma value list that follows ANY of the given label synonyms on a line
     * (generalises [extractLabelledDays] for the waiting-period / HC option families). Unit tokens
     * (days/months/X/times) are stripped; numeric and "Nil"/"Waived" tokens are kept.
     */
    private fun extractLabelledList(text: String, vararg labels: String): List<String> {
        if (text.isBlank() || labels.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            val matched = labels.firstOrNull { line.contains(it, ignoreCase = true) } ?: continue
            val afterColon = line.substringAfter(':', line.substringAfter(matched, "")).trim()
            val value = afterColon.ifBlank { line }
            value.split('/', ',')
                .map { tok ->
                    tok.replace(
                        Regex("""(?i)\b(days?|months?|years?|yrs?|Per Year|Per day|Per claim|upto|up to|multiple of|times)\b"""),
                        "",
                    ).trim()
                }
                .map { it.trim('.', ' ', '(', ')') }
                .filter {
                    it.isNotEmpty() &&
                        (it.any { ch -> ch.isDigit() } || it.equals("Waived", true) || it.equals("Nil", true))
                }
                .forEach { out.add(it) }
        }
        return out.toList().take(20)
    }

    /** Member categories named in a cover's text ("Employee", "Spouse", "Child", "Parent", …). */
    private fun extractMemberCategories(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val candidates = listOf(
            "Employee", "Spouse", "Son", "Daughter", "Child", "Children", "Parent", "Parents",
            "Father", "Mother", "Partner", "Dependent",
        )
        return candidates.filter { Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(text) }
    }

    /** Payout-linkage bases referenced in a cover's text ("per day", "per claim", "per policy", …). */
    private fun extractPayoutLinkageBases(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val bases = listOf("per day", "per week", "per month", "per claim", "per event", "per policy", "per member")
        return bases.filter { it.contains(' ') && Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(text) }
    }

    /**
     * Parse a "Sublimit / Options" or coverage cell's lines into [Sublimit]s. Each non-blank line
     * becomes a sublimit (label = text), with an optional rupee [Money] limit parsed from the line
     * when present ("upto 12 Cr", "INR 5,00,000"). Bounded to 12.
     */
    private fun parseSublimits(cell: String): List<Sublimit> {
        if (cell.isBlank()) return emptyList()
        return cell.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val limit = parseMoneyLimit(line).takeIf { it != Money.ZERO }
                Sublimit(label = line.take(200), limit = limit, details = "")
            }
            .distinctBy { it.label }
            .take(12)
    }

    /**
     * PBT-PA sub-table heading rows ("1.PERSONAL ACCIDENT", "A.Base Covers", "B. Optional Covers")
     * carry no real Sl No: the Sl-No cell is blank/non-numeric AND the name starts with a single
     * letter or digit followed by '.'/')' then ALL-CAPS or "Base/Optional Covers" copy.
     */
    private fun isCoverSubTableHeading(slNo: String, name: String): Boolean {
        if (slNo.toIntOrNull() != null) return false
        if (Regex("""^\d+[a-z]$""", RegexOption.IGNORE_CASE).matches(slNo)) return false
        val n = name.trim()
        if (n.isBlank()) return false
        return Regex("""^[A-Za-z0-9][.)]\s*.+""").matches(n) &&
            (n.contains("Base Cover", true) || n.contains("Optional Cover", true) ||
                n.contains("Add", true) || n == n.uppercase())
    }
}
