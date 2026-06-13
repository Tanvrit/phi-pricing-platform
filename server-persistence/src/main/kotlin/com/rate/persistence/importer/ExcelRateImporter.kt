package com.rate.persistence.importer

import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Tenure
import com.rate.core.regulatory.FAMILY_TYPES
import com.rate.core.regulatory.Zone
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.MemberLevelRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.sdk.rating.model.CoverIds
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

/**
 * JVM-only Apache POI Excel rate importer — the ONLY POI consumer in the platform (CSV
 * ingestion stays pure-KMP in sdk-ingestion). It PARSES the v13.0 rate workbook into an
 * immutable [RateRowBatch] tagged with [version]; persistence + activation are done by
 * sdk-ingestion's [com.rate.sdk.ingestion.handler.RateImportHandler] over the Mongo
 * [com.rate.persistence.repository.RateImportRepositoryImpl].
 *
 * RELOCATED from the monolith's `server/import/ExcelImporter` (which inserted into Exposed
 * tables); the cell-reading semantics (`numericOrNull`, `stringOrEmpty`, age-label mapping,
 * the zone-block plan-sheet layout, the per-cover section parsers) are ported verbatim, but
 * the side effect is now "append a typed rate row" via the deterministic-id `*.of(...)`
 * builders so the result is data, not a DB write — keeping the importer pure of persistence.
 *
 * The engine reads rates as Double (parity); no Money conversion happens here.
 */
class ExcelRateImporter {

    /** Parse the workbook bytes into a [RateRowBatch] under [version]. Closes the stream. */
    fun parse(stream: InputStream, version: String): RateRowBatch =
        WorkbookFactory.create(stream).use { wb -> parse(wb, version) }

    fun parse(wb: Workbook, version: String): RateRowBatch {
        val baseRates = mutableListOf<BaseRateRow>()
        val coverRates = mutableListOf<CoverRateRow>()
        val memberRates = mutableListOf<MemberLevelRow>()
        val discountRates = mutableListOf<DiscountRow>()
        val availability = mutableListOf<CoverAvailabilityRow>()
        val instalment = mutableListOf<InstalmentRow>()

        val ctx = Ctx(version, baseRates, coverRates, memberRates, discountRates, availability, instalment)

        importBaseRates(wb, ctx)
        importCoverRates(wb, ctx)
        importCoverAvailability(wb, ctx)
        importInstalmentConfig(wb, ctx)

        return RateRowBatch(
            version = version,
            baseRates = baseRates,
            coverRates = coverRates,
            memberLevelRates = memberRates,
            discountRates = discountRates,
            instalmentConfig = instalment,
            coverAvailability = availability,
        )
    }

    /** Mutable accumulators threaded through the parsers. */
    private class Ctx(
        val version: String,
        val baseRates: MutableList<BaseRateRow>,
        val coverRates: MutableList<CoverRateRow>,
        val memberRates: MutableList<MemberLevelRow>,
        val discountRates: MutableList<DiscountRow>,
        val availability: MutableList<CoverAvailabilityRow>,
        val instalment: MutableList<InstalmentRow>,
    ) {
        fun cover(
            coverId: String, rate: Double, param1: String? = null, param2: String? = null,
            ageBandMin: Int? = null, sumInsured: Long? = null, planOrTenureKey: String? = null,
        ) = coverRates.add(
            CoverRateRow.of(version, coverId, rate, param1, param2, ageBandMin, sumInsured, planOrTenureKey),
        )

        fun member(coverId: String, rate: Double, ageBandMin: Int? = null, param1: String? = null) =
            memberRates.add(MemberLevelRow.of(version, coverId, rate, ageBandMin, param1))

        fun discount(id: String, rate: Double, paramKey: String? = null) =
            discountRates.add(DiscountRow.of(version, id, rate, paramKey))
    }

    // ── Plan id ↔ Excel name maps (verbatim from the monolith) ───────────────
    private val planNameToId = mapOf(
        "PHI Basic" to "PHI_BASIC", "PHI POSP" to "PHI_POSP",
        "PHI Flagship 1" to "PHI_FLAGSHIP1", "PHI Flagship 2" to "PHI_FLAGSHIP2",
        "PHI Flagship 3" to "PHI_FLAGSHIP3", "PHI Sub Standard" to "PHI_SUBSTANDARD",
        "PHI Senior" to "PHI_SENIOR", "PHI Global Excl US Canada" to "PHI_GLOBAL_EXCL",
        "PHI Global Asia Excl India" to "PHI_GLOBAL_ASIA", "PHI Global Europe" to "PHI_GLOBAL_EUROPE",
        "PHI Global Plus Incl US Canada" to "PHI_GLOBAL_PLUS",
        "PHI Global Plus Excl US Canada" to "PHI_GLOBAL_PLUS_EXCL",
        "PHI Global Plus Asia Excl India" to "PHI_GLOBAL_PLUS_ASIA",
        "PHI Global Plus Europe" to "PHI_GLOBAL_PLUS_EUROPE",
    )

    private val planSheets = mapOf(
        "PHI_Basic" to "PHI_BASIC", "PHI_POSP" to "PHI_POSP",
        "PHI_Flagship1" to "PHI_FLAGSHIP1", "PHI_Flagship_2" to "PHI_FLAGSHIP2",
        "PHI_Flagship_4_(PB)" to "PHI_FLAGSHIP3", "PHI_Sub_Standard" to "PHI_SUBSTANDARD",
        "PHI_Senior" to "PHI_SENIOR", "Global_excluding US&Canda" to "PHI_GLOBAL_EXCL",
        "Global_Asia_excluding_India" to "PHI_GLOBAL_ASIA", "Global_Europe" to "PHI_GLOBAL_EUROPE",
        "Global_Plus_Including USA & Can" to "PHI_GLOBAL_PLUS",
        "Global_Plus_excluding_USA & Can" to "PHI_GLOBAL_PLUS_EXCL",
        "Global_Plus_Ais_excluding_India" to "PHI_GLOBAL_PLUS_ASIA",
        "Global_Plus_Europe" to "PHI_GLOBAL_PLUS_EUROPE",
    )

    private val ageLabelToMin = mapOf(
        "5 - 17" to 5, "18 - 25" to 18, "26 - 30" to 26, "31 - 35" to 31,
        "36 - 40" to 36, "41 - 45" to 41, "46 - 50" to 46, "51 - 55" to 51,
        "56 - 60" to 56, "61 - 65" to 61, "66 - 70" to 66, "71 - 75" to 71,
        "76 - 80" to 76, "81 - 85" to 81, "85+" to 86, "86+" to 86,
    )

    private fun ageLabel(s: String): Int? = ageLabelToMin[s.trim()]
        ?: ageLabelToMin.entries.firstOrNull { s.trim().startsWith(it.key.split(" ").first()) }?.value

    // ── Cell helpers (verbatim) ──────────────────────────────────────────────
    private fun Cell?.numericOrNull(): Double? {
        if (this == null) return null
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.NUMERIC -> numericCellValue
                else -> runCatching { numericCellValue }.getOrNull()
            }
            else -> null
        }
    }

    private fun Cell?.stringOrEmpty(): String {
        if (this == null) return ""
        return when (cellType) {
            CellType.STRING -> stringCellValue.trim()
            CellType.NUMERIC -> numericCellValue.toLong().toString()
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.STRING -> stringCellValue.trim()
                CellType.NUMERIC -> numericCellValue.toLong().toString()
                else -> ""
            }
            else -> ""
        }
    }

    // ── STEP B: base rates from the 14 plan sheets (zone-block layout) ────────
    private fun importBaseRates(wb: Workbook, ctx: Ctx) {
        for ((sheetName, planId) in planSheets) {
            val sheet = wb.getSheet(sheetName) ?: continue
            parsePlanSheet(sheet, planId, ctx)
        }
    }

    private data class ZoneBlock(
        val zone: String,
        val labelColIdx: Int,
        val siCols: MutableList<Pair<Int, Long>> = mutableListOf(),
    )

    private fun parsePlanSheet(sheet: Sheet, planId: String, ctx: Ctx) {
        val zoneRegex = Regex("Zone\\s+[1-4]", RegexOption.IGNORE_CASE)
        val isNoZonePlan = planId == "PHI_SUBSTANDARD" || planId == "PHI_SENIOR"
        var currentFT: String? = null
        var activeZoneBlocks = listOf<ZoneBlock>()

        for (r in 0..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val cell1 = row.getCell(1).stringOrEmpty()

            if (isNoZonePlan) {
                if (cell1.contains("Office Premium (Excl", ignoreCase = true) &&
                    !cell1.trim().startsWith("Office", ignoreCase = true)
                ) {
                    val ftCode = cell1.trim().substringBefore(" ").trim()
                    val ftMatch = FAMILY_TYPES.firstOrNull { it.code == ftCode }
                    if (ftMatch != null) {
                        currentFT = ftMatch.code
                        val siHeaderRow = sheet.getRow(r + 3) ?: continue
                        val siCols = mutableListOf<Pair<Int, Long>>()
                        for (c in siHeaderRow) {
                            if (c.columnIndex < 2) continue
                            val si = c.numericOrNull()?.toLong() ?: continue
                            if (si > 0) siCols.add(c.columnIndex to si)
                        }
                        activeZoneBlocks =
                            if (siCols.isNotEmpty()) listOf(ZoneBlock("Zone 4", 1, siCols)) else emptyList()
                    }
                    continue
                }
            } else {
                val detected = mutableListOf<ZoneBlock>()
                for (c in row) {
                    val cv = c.stringOrEmpty()
                    if (cv.isBlank()) continue
                    val zm = zoneRegex.find(cv) ?: continue
                    if (!cv.contains("Risk Premium", ignoreCase = true) &&
                        !cv.contains("Office Premium for Zone", ignoreCase = true)
                    ) {
                        continue
                    }
                    val zoneLabel = "Zone " + zm.value.trim().last()
                    val zone = Zone.entries.firstOrNull { it.label.equals(zoneLabel, ignoreCase = true) }?.label
                        ?: zoneLabel
                    val ftCode = cv.trim().substringBefore(" ").trim()
                    if (FAMILY_TYPES.none { it.code == ftCode }) continue
                    currentFT = ftCode
                    detected.add(ZoneBlock(zone, c.columnIndex))
                }
                if (detected.isNotEmpty()) {
                    detected.sortBy { it.labelColIdx }
                    val siHeaderRow = sheet.getRow(r + 1)
                    if (siHeaderRow != null) {
                        for (i in detected.indices) {
                            val zb = detected[i]
                            val nextBoundary =
                                if (i < detected.size - 1) detected[i + 1].labelColIdx else Int.MAX_VALUE
                            for (c in siHeaderRow) {
                                val colIdx = c.columnIndex
                                if (colIdx <= zb.labelColIdx || colIdx >= nextBoundary) continue
                                val si = c.numericOrNull()?.toLong() ?: continue
                                if (si > 0) zb.siCols.add(colIdx to si)
                            }
                        }
                    }
                    activeZoneBlocks = detected.filter { it.siCols.isNotEmpty() }
                    continue
                }
            }

            val ageMin = ageLabel(cell1) ?: continue
            val ft = currentFT ?: continue
            if (activeZoneBlocks.isEmpty()) continue
            for (zoneBlock in activeZoneBlocks) {
                for ((col, si) in zoneBlock.siCols) {
                    val premium = row.getCell(col).numericOrNull() ?: continue
                    if (premium <= 0) continue
                    ctx.baseRates.add(
                        BaseRateRow.of(ctx.version, planId, ft, zoneBlock.zone, ageMin, si, premium),
                    )
                }
            }
        }
    }

    // ── STEP C: cover rates from "Loadings and Discounts" ────────────────────
    private fun importCoverRates(wb: Workbook, ctx: Ctx) {
        val sheet = wb.getSheet("Loadings and Discounts")
            ?: wb.getSheet("Loadings And Discounts")
            ?: return
        val rows = (0..sheet.lastRowNum).mapNotNull { sheet.getRow(it) }

        // Simple flat-rate sections (cover ↦ single rate cell, with a default fallback).
        flatPct(ctx, CoverIds.CONSUMABLES_LIST1, rows, "Consumables Cover - List I", 0.05)
        flatPct(ctx, CoverIds.PRE_POST_HOSP, rows, "Pre-Post Hospitalisation", 0.10)
        flatPct(ctx, CoverIds.CONSUMABLE_PLUS, rows, "Consumable Plus Cover", 0.09)
        flatPct(ctx, CoverIds.HOME_CARE, rows, "Home Care Treatment", 167.0)
        flatPct(ctx, CoverIds.DONOR_PLUS, rows, "Donor Plus Cover", 417.0)
        flatPct(ctx, CoverIds.SMART_SELECT, rows, "Smart Select", -0.15)
        flatPct(ctx, CoverIds.GOOD_HEALTH, rows, "Incentivize Good Health", 0.0)
        flatPct(ctx, CoverIds.CHILD_PROTECT, rows, "Child Protect", 100.0)
        flatPct(ctx, CoverIds.AIR_AMBULANCE, rows, "Air Ambulance", 433.0)
        flatPct(ctx, CoverIds.FITNESS_PLUS, rows, "Fitness Plus Benefit", 649.0)
        flatPct(ctx, CoverIds.WELLNESS_PACKAGE, rows, "Wellness Package", 0.0)
        flatPct(ctx, CoverIds.SECOND_OPINION, rows, "Second E-opinion", 67.0)
        flatPct(ctx, CoverIds.POST_DELIVERY_CARE, rows, "Post Delivery Care", 0.0)
        flatPct(ctx, CoverIds.SURROGATE_MOTHER, rows, "Surrogate Mother", 908.0)
        flatPct(ctx, CoverIds.OOCYTE_DONOR, rows, "Oocyte", 846.0)
        flatPct(ctx, CoverIds.POST_DISCHARGE_CARE, rows, "Post Discharge Care Guidance", 167.0)
        flatPct(ctx, CoverIds.ADVENTURE_SPORTS, rows, "Adventure Sports Cover", 183.0)
        flatPct(ctx, CoverIds.FEMALE_VACCINATION, rows, "Female Vaccination", 3333.0)
        flatPct(ctx, CoverIds.PRU_HEALTH_SPECIALIST, rows, "Pru Health Specialist", 83.0)
        flatPct(ctx, CoverIds.PRUDENTIAL_HEALTHY, rows, "Prudential Healthy", 0.0075)

        // Structured sections (matrices / member-level / discounts).
        day1Instant(ctx, rows)
        loyaltyBonus(ctx, rows)
        doubleCover(ctx, rows)
        chronicInstant(ctx, rows)
        pedWaiting(ctx, rows)
        specificIllnessWaiting(ctx, rows)
        modernTreatmentPlus(ctx, rows)
        roomRentMod(ctx, rows)
        diseaseSublimit(ctx, rows)
        infiniteClaim(ctx, rows)
        restorationPlus(ctx, rows)
        spouseProtect(ctx, rows)
        durableMedical(ctx, rows)
        tenureWise(ctx, rows)
        aggregateDeductible(ctx, rows)
        coPay(ctx, rows)
        dailyHospitalCash(ctx, rows)
        convalescence(ctx, rows)
        compassionate(ctx, rows)
        personalAccident(ctx, rows)
        maternityNewborn(ctx, rows)
        infertility(ctx, rows)
        chronicManagement(ctx, rows)
        advanceHealthCheckup(ctx, rows)
        cashlessOpd(ctx, rows)
        criticalIllness(ctx, rows)
        maternityFixed(ctx, rows)
        enhancedGeo(ctx, rows)
        cancerBooster(ctx, rows)
        cancerScreening(ctx, rows)
        perClaimDeductible(ctx, rows)
        discounts(ctx, rows)
    }

    private fun findSectionRow(rows: List<Row>, keyword: String): Int =
        rows.indexOfFirst { it.getCell(0).stringOrEmpty().contains(keyword, ignoreCase = true) }

    private fun flatPct(ctx: Ctx, coverId: String, rows: List<Row>, keyword: String, default: Double) {
        val idx = findSectionRow(rows, keyword)
        if (idx < 0) { ctx.cover(coverId, default); return }
        for (i in idx..minOf(idx + 5, rows.size - 1)) {
            val v = rows[i].getCell(1).numericOrNull()
            if (v != null) { ctx.cover(coverId, v); return }
        }
        ctx.cover(coverId, default)
    }

    private fun day1Instant(ctx: Ctx, rows: List<Row>) {
        val secIdx = findSectionRow(rows, "Day 1 Instant"); if (secIdx < 0) return
        val headerRow = rows.getOrNull(secIdx + 1) ?: return
        val multipliers = (1..8).mapNotNull { c ->
            headerRow.getCell(c).stringOrEmpty().takeIf { it.contains("X", true) }?.let { c to it }
        }
        for (i in secIdx + 2 until minOf(secIdx + 20, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            for ((col, mult) in multipliers) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.DAY1_INSTANT, rate, param1 = mult, sumInsured = si)
            }
        }
    }

    private fun loyaltyBonus(ctx: Ctx, rows: List<Row>) {
        val options = listOf("100% upto 200%", "100% upto 500%", "100% upto 1000%")
        if (findSectionRow(rows, "Loyalty Bonus") < 0) return
        for (opt in options) {
            val optIdx = rows.indexOfFirst { r ->
                val v = r.getCell(0).stringOrEmpty()
                v.contains(opt.split("(").first().trim(), true) ||
                    (v.isBlank() && r.getCell(1).stringOrEmpty().contains(opt, true))
            }.takeIf { it >= 0 } ?: continue
            val headerRow = rows.getOrNull(optIdx + 1) ?: continue
            val siCols = (1..20).mapNotNull { c ->
                headerRow.getCell(c).numericOrNull()?.toLong()?.takeIf { it > 0 }?.let { c to it }
            }
            if (siCols.isEmpty()) continue
            for (i in optIdx + 2 until minOf(optIdx + 20, rows.size)) {
                val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
                for ((col, si) in siCols) {
                    val rate = rows[i].getCell(col).numericOrNull() ?: continue
                    ctx.cover(CoverIds.LOYALTY_BONUS, rate, param1 = opt, ageBandMin = ageMin, sumInsured = si)
                }
            }
        }
    }

    private fun doubleCover(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Double your Cover")
        val rate = rows.getOrNull(idx + 1)?.getCell(1).numericOrNull()
            ?: rows.getOrNull(idx)?.getCell(1).numericOrNull() ?: 0.0325
        ctx.cover(CoverIds.DOUBLE_COVER_7YR, rate)
    }

    private fun chronicInstant(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Chronic Conditions"); if (idx < 0) return
        val conditions = listOf("Single condition", "Two comorbid condition", "Three comorbid condition")
        var ci = 0
        for (i in idx + 1 until minOf(idx + 8, rows.size)) {
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            if (ci < conditions.size) ctx.cover(CoverIds.CHRONIC_INSTANT, rate, param1 = conditions[ci++])
        }
    }

    private fun pedWaiting(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "PED Waiting"); if (idx < 0) return
        val periods = listOf("3 to 2 Years", "3 to 1 Year"); var p = 0
        for (i in idx + 1 until minOf(idx + 6, rows.size)) {
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            if (p < periods.size) ctx.cover(CoverIds.PED_WAITING, rate, param1 = periods[p++])
        }
    }

    private fun specificIllnessWaiting(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Specific Illness Waiting"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            val std = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.SPECIFIC_ILLNESS_WAITING, std, ageBandMin = ageMin)
            rows[i].getCell(2).numericOrNull()?.let {
                ctx.cover(CoverIds.SPECIFIC_ILLNESS_WAITING, it, ageBandMin = ageMin, planOrTenureKey = "PHI_SENIOR")
            }
        }
    }

    private fun modernTreatmentPlus(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Modern Treatment Plus"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val planName = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            val pid = planNameToId[planName] ?: continue
            ctx.cover(CoverIds.MODERN_TREATMENT_PLUS, rate, planOrTenureKey = pid)
        }
    }

    private fun roomRentMod(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Modification of Room Rent"); if (idx < 0) return
        val roomTypes = listOf("General Room", "Shared Room", "Single Private AC Room", "Any Room")
        var rt = 0
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            if (rt < roomTypes.size) ctx.cover(CoverIds.ROOM_RENT_MOD, rate, param1 = roomTypes[rt])
            rt++
        }
    }

    private fun diseaseSublimit(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Diseases Specific Sub-limit"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 15, rows.size)) {
            val cell0 = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            planNameToId[cell0]?.let { ctx.cover(CoverIds.DISEASE_SUBLIMIT, rate, planOrTenureKey = it) }
            cell0.toLongOrNull()?.let { ctx.cover(CoverIds.DISEASE_SUBLIMIT, rate, sumInsured = it) }
        }
    }

    private fun siMatrix(rows: List<Row>, idx: Int, maxCols: Int = 20): List<Pair<Int, Long>> {
        val headerRow = rows.getOrNull(idx + 1) ?: return emptyList()
        return (1..maxCols).mapNotNull { c ->
            headerRow.getCell(c).numericOrNull()?.toLong()?.takeIf { it > 0 }?.let { c to it }
        }
    }

    private fun infiniteClaim(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Infinite Claim"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.INFINITE_CLAIM, rate, sumInsured = si)
        }
    }

    private fun restorationPlus(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Restoration Plus"); if (idx < 0) return
        val siCols = siMatrix(rows, idx)
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            for ((col, si) in siCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.RESTORATION_PLUS, rate, ageBandMin = ageMin, sumInsured = si)
            }
        }
    }

    private fun spouseProtect(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Spouse Protect"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.SPOUSE_PROTECT, rate, sumInsured = si)
        }
    }

    private fun durableMedical(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Durable Medical"); if (idx < 0) return
        val siCols = siMatrix(rows, idx)
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            for ((col, si) in siCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.DURABLE_MEDICAL, rate, ageBandMin = ageMin, sumInsured = si)
            }
        }
    }

    private fun tenureWise(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Tenure Wise"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val tenureCols = (1..5).mapNotNull { c ->
            headerRow.getCell(c).stringOrEmpty().takeIf { it.contains("Year", true) }?.let { c to it }
        }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            for ((col, label) in tenureCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.TENURE_WISE, rate, sumInsured = si, planOrTenureKey = label)
            }
        }
    }

    private fun aggregateDeductible(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Aggregate Deductible"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val dedCols = (1..5).mapNotNull { c -> headerRow.getCell(c).numericOrNull()?.toLong()?.let { c to it.toString() } }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            for ((col, ded) in dedCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.AGGREGATE_DEDUCTIBLE, rate, param1 = ded, sumInsured = si)
            }
        }
    }

    private fun coPay(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Co-Payment"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val colMap = mutableMapOf<String, Int>()
        for (c in 0..10) {
            val v = headerRow.getCell(c).stringOrEmpty()
            when {
                v.contains("Omnibus", true) -> colMap["OMNIBUS"] = c + 1
                v.contains("Senior", true) -> colMap["SENIOR"] = c + 1
                v.contains("Sub Standard", true) -> colMap["SUB_STANDARD"] = c + 1
            }
        }
        for (i in idx + 2 until minOf(idx + 15, rows.size)) {
            val copayPct = rows[i].getCell(0).numericOrNull()?.toString() ?: break
            for ((tableId, rateCol) in colMap) {
                val rate = rows[i].getCell(rateCol).numericOrNull() ?: continue
                ctx.cover(CoverIds.CO_PAY, rate, param1 = copayPct, planOrTenureKey = tableId)
            }
        }
    }

    private fun dailyHospitalCash(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Daily Hospital Cash"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val benefitCols = (1..15).mapNotNull { c ->
            headerRow.getCell(c).numericOrNull()?.toLong()?.takeIf { it > 0 }?.let { c to it.toString() }
        }
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            for ((col, benefit) in benefitCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.member(CoverIds.DAILY_HOSPITAL_CASH, rate, ageBandMin = ageMin, param1 = benefit)
            }
        }
    }

    private fun convalescence(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Convalescence"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val triggerCols = (1..4).mapNotNull { c ->
            headerRow.getCell(c).stringOrEmpty().takeIf { it.contains("Days", true) }?.let { c to it }
        }
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val amount = rows[i].getCell(0).numericOrNull()?.toLong()?.toString() ?: break
            for ((col, trigger) in triggerCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.CONVALESCENCE, rate, param1 = amount, param2 = trigger)
            }
        }
    }

    private fun compassionate(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Compassionate"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 6, rows.size)) {
            val amount = rows[i].getCell(0).numericOrNull()?.toLong()?.toString() ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.COMPASSIONATE, rate, param1 = amount)
        }
    }

    private fun personalAccident(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Personal Accident"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            val siStr = rows[i].getCell(0).numericOrNull()?.toLong()?.toString() ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.member(CoverIds.PERSONAL_ACCIDENT, rate, param1 = siStr)
        }
    }

    private fun waitingSiMatrix(
        ctx: Ctx, rows: List<Row>, keyword: String, coverId: String, waitingPeriods: List<String>, maxCols: Int,
    ) {
        val idx = findSectionRow(rows, keyword); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val siCols = (1..maxCols).mapNotNull { c ->
            headerRow.getCell(c).numericOrNull()?.toLong()?.takeIf { it > 0 }?.let { c to it.toString() }
        }
        var wp = 0
        for (i in idx + 2 until minOf(idx + 10, rows.size)) {
            rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
            val period = waitingPeriods.getOrNull(wp++) ?: break
            for ((col, si) in siCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(coverId, rate, param1 = si, param2 = period)
            }
        }
    }

    private fun maternityNewborn(ctx: Ctx, rows: List<Row>) =
        waitingSiMatrix(ctx, rows, "Maternity & New Born", CoverIds.MATERNITY_NEWBORN,
            listOf("9 Months", "24 Months", "36 Months", "48 Months"), 5)

    private fun infertility(ctx: Ctx, rows: List<Row>) =
        waitingSiMatrix(ctx, rows, "Infertility", CoverIds.INFERTILITY,
            listOf("9 Months", "24 Months", "36 Months", "48 Months"), 4)

    private fun maternityFixed(ctx: Ctx, rows: List<Row>) =
        waitingSiMatrix(ctx, rows, "Maternity Cover Fixed Benefit", CoverIds.MATERNITY_FIXED,
            listOf("0 Month", "3 Months", "6 Months", "9 Months"), 4)

    private fun chronicManagement(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Chronic Management"); if (idx < 0) return
        val conditions = listOf("1", "2", "3"); var c = 0
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.member(CoverIds.CHRONIC_MANAGEMENT, rate, param1 = conditions.getOrNull(c++))
        }
    }

    private fun advanceHealthCheckup(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Advance Prudential Health Check"); if (idx < 0) return
        val dataRow = rows.getOrNull(idx + 2) ?: return
        dataRow.getCell(1).numericOrNull()?.let { ctx.member(CoverIds.ADVANCE_HEALTH_CHECKUP, it, param1 = "Advance") }
        dataRow.getCell(2).numericOrNull()?.let { ctx.member(CoverIds.ADVANCE_HEALTH_CHECKUP, it, param1 = "Basic") }
    }

    private fun cashlessOpd(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Cashless OPD"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 6, rows.size)) {
            val limit = rows[i].getCell(0).numericOrNull()?.toLong()?.toString() ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.CASHLESS_OPD, rate, param1 = limit)
        }
    }

    private fun criticalIllness(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Critical Illness Cover"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.member(CoverIds.CRITICAL_ILLNESS, rate, ageBandMin = ageMin)
        }
    }

    private fun enhancedGeo(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Enhanced Geographical"); if (idx < 0) return
        val geoMap = mapOf(
            "Worlwide exluding USA & CANADA" to "Worldwide excl. USA & Canada",
            "Worldwide excl. USA & Canada" to "Worldwide excl. USA & Canada",
            "Asia excluding India" to "Asia excluding India",
            "Asia excl. India" to "Asia excluding India",
            "Europe" to "Europe",
            "Worldwide incl. USA & Canada" to "Worldwide incl. USA & Canada",
        )
        for (i in idx + 2 until minOf(idx + 8, rows.size)) {
            val geoRaw = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
            val geo = geoMap.entries.firstOrNull { geoRaw.contains(it.key, true) }?.value ?: geoRaw
            val rate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.ENHANCED_GEO, rate, param1 = geo)
        }
    }

    private fun cancerBooster(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Cancer Booster"); if (idx < 0) return
        val siCols = siMatrix(rows, idx)
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            for ((col, si) in siCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.CANCER_BOOSTER, rate, ageBandMin = ageMin, sumInsured = si)
            }
        }
    }

    private fun cancerScreening(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Annual Screening"); if (idx < 0) return
        for (i in idx + 2 until minOf(idx + 20, rows.size)) {
            val ageMin = ageLabel(rows[i].getCell(0).stringOrEmpty()) ?: break
            val offRate = rows[i].getCell(1).numericOrNull() ?: continue
            ctx.cover(CoverIds.CANCER_SCREENING, offRate, ageBandMin = ageMin)
            rows[i].getCell(2).numericOrNull()?.let {
                ctx.cover(CoverIds.CANCER_SCREENING, it, ageBandMin = ageMin, planOrTenureKey = "GLOBAL")
            }
        }
    }

    private fun perClaimDeductible(ctx: Ctx, rows: List<Row>) {
        val idx = findSectionRow(rows, "Per Claim Deductible"); if (idx < 0) return
        val headerRow = rows.getOrNull(idx + 1) ?: return
        val dedCols = (1..4).mapNotNull { c -> headerRow.getCell(c).numericOrNull()?.toLong()?.let { c to it.toString() } }
        for (i in idx + 2 until minOf(idx + 25, rows.size)) {
            val si = rows[i].getCell(0).numericOrNull()?.toLong() ?: break
            for ((col, ded) in dedCols) {
                val rate = rows[i].getCell(col).numericOrNull() ?: continue
                ctx.cover(CoverIds.PER_CLAIM_DEDUCTIBLE, rate, param1 = ded, sumInsured = si)
            }
        }
    }

    private fun discounts(ctx: Ctx, rows: List<Row>) {
        findSectionRow(rows, "Tenure Discount").takeIf { it >= 0 }?.let { tIdx ->
            val tenureLabels = listOf("1 Year", "2 Years", "3 Years", "4 Years", "5 Years")
            for (i in tIdx + 2 until minOf(tIdx + 10, rows.size)) {
                val label = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
                val rate = rows[i].getCell(1).numericOrNull() ?: break
                if (label in tenureLabels) ctx.discount(CoverIds.DISC_TENURE, rate, label)
            }
        }
        findSectionRow(rows, "Affiliate Employee Discount").takeIf { it >= 0 }?.let { e ->
            ctx.discount(CoverIds.DISC_EMPLOYEE, rows.getOrNull(e + 1)?.getCell(1).numericOrNull() ?: 0.10)
        }
        findSectionRow(rows, "CIBIL Score Discount").takeIf { it >= 0 }?.let { cIdx ->
            val bandLabels = listOf("Upto 700", "701 to 750", "751 to 800", "801 to 849", "850 and above")
            val bandKeys = listOf("<=700", "701-750", "751-800", "801-849", ">=850")
            for (i in cIdx + 2 until minOf(cIdx + 10, rows.size)) {
                val label = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
                val rate = rows[i].getCell(1).numericOrNull() ?: break
                val bi = bandLabels.indexOfFirst { label.contains(it.split(" ").first(), true) }
                ctx.discount(CoverIds.DISC_CIBIL, rate, bandKeys.getOrNull(bi) ?: label)
            }
        }
        findSectionRow(rows, "NRI").takeIf { it >= 0 }?.let { n ->
            ctx.discount(CoverIds.DISC_NRI, rows.getOrNull(n + 1)?.getCell(1).numericOrNull() ?: 0.15)
        }
        findSectionRow(rows, "Auto Debit").takeIf { it >= 0 }?.let { a ->
            ctx.discount(CoverIds.DISC_AUTO_DEBIT, rows.getOrNull(a + 1)?.getCell(1).numericOrNull() ?: 0.025)
        }
        findSectionRow(rows, "Multiple Member").takeIf { it >= 0 }?.let { m ->
            for (i in m + 2 until minOf(m + 6, rows.size)) {
                val label = rows[i].getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: break
                val rate = rows[i].getCell(1).numericOrNull() ?: break
                val key = if (label.contains("4")) "4+ members" else "2-3 members"
                ctx.discount(CoverIds.DISC_MULTI_MEMBER, rate, key)
            }
        }
        findSectionRow(rows, "Discount in lieu of commission").takeIf { it >= 0 }?.let { c ->
            ctx.discount(CoverIds.DISC_COMMISSION_LIEU, rows.getOrNull(c + 1)?.getCell(1).numericOrNull() ?: 0.15)
        }
        findSectionRow(rows, "Corporate GMC").takeIf { it >= 0 }?.let { g ->
            ctx.discount(CoverIds.DISC_GMC, rows.getOrNull(g + 1)?.getCell(1).numericOrNull() ?: 0.05)
        }
    }

    // ── STEP D: cover availability from Sheet1 ───────────────────────────────
    private fun importCoverAvailability(wb: Workbook, ctx: Ctx) {
        val sheet = wb.getSheet("Sheet1") ?: return
        val headerRow = sheet.getRow(0) ?: return
        val planCols = mutableMapOf<Int, String>()
        for (c in 1..20) {
            val planName = headerRow.getCell(c).stringOrEmpty().takeIf { it.isNotBlank() } ?: continue
            planNameToId[planName]?.let { planCols[c] = it }
        }
        // Cover name → id resolution: the row label IS a display name; we match against the
        // seed cover import-aliases via a lowercase contains so the importer needs no DB read.
        for (r in 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val coverName = row.getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: continue
            val coverId = resolveCoverId(coverName) ?: continue
            for ((col, planId) in planCols) {
                if (row.getCell(col).stringOrEmpty().contains("yes", true)) {
                    ctx.availability.add(CoverAvailabilityRow.of(ctx.version, planId, coverId))
                }
            }
        }
    }

    /** Heuristic cover-name → CoverId mapping (the seed catalog owns the authoritative aliases). */
    private fun resolveCoverId(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("day 1") || n.contains("day1") -> CoverIds.DAY1_INSTANT
            n.contains("loyalty") -> CoverIds.LOYALTY_BONUS
            n.contains("double") && n.contains("cover") -> CoverIds.DOUBLE_COVER_7YR
            n.contains("critical illness") -> CoverIds.CRITICAL_ILLNESS
            n.contains("personal accident") -> CoverIds.PERSONAL_ACCIDENT
            n.contains("maternity") && n.contains("new born") -> CoverIds.MATERNITY_NEWBORN
            n.contains("maternity") -> CoverIds.MATERNITY_FIXED
            n.contains("air ambulance") -> CoverIds.AIR_AMBULANCE
            n.contains("infinite claim") -> CoverIds.INFINITE_CLAIM
            n.contains("restoration") -> CoverIds.RESTORATION_PLUS
            n.contains("room rent") -> CoverIds.ROOM_RENT_MOD
            n.contains("co-pay") || n.contains("copay") -> CoverIds.CO_PAY
            n.contains("daily hospital") -> CoverIds.DAILY_HOSPITAL_CASH
            n.contains("cashless opd") -> CoverIds.CASHLESS_OPD
            else -> null
        }
    }

    // ── STEP E: instalment config from Sheet2 (fallback to rule-based) ───────
    private fun importInstalmentConfig(wb: Workbook, ctx: Ctx) {
        val sheet = wb.getSheet("Sheet2")
        if (sheet == null) {
            computeInstalmentConfig(ctx)
            return
        }
        val headerRow = sheet.getRow(3) ?: run { computeInstalmentConfig(ctx); return }
        val modeCols = mutableMapOf<PaymentMode, Int>()
        for (c in 0..10) {
            val label = headerRow.getCell(c).stringOrEmpty().takeIf { it.isNotBlank() } ?: continue
            when {
                label.contains("Monthly", true) -> modeCols[PaymentMode.MONTHLY] = c
                label.contains("Quarterly", true) -> modeCols[PaymentMode.QUARTERLY] = c
                label.contains("Half", true) -> modeCols[PaymentMode.HALF_YEARLY] = c
                label.contains("Annual", true) -> modeCols[PaymentMode.ANNUAL] = c
            }
        }
        val seen = mutableSetOf<String>()
        for (r in 4..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val tenureLabel = row.getCell(0).stringOrEmpty().takeIf { it.isNotBlank() } ?: continue
            val payTermLabel = row.getCell(1).stringOrEmpty().takeIf { it.isNotBlank() } ?: tenureLabel
            val tenure = Tenure.entries.firstOrNull { it.label == tenureLabel } ?: continue
            val payTenure = Tenure.entries.firstOrNull { it.label == payTermLabel } ?: tenure

            addInstalment(ctx, seen, tenure, payTenure, PaymentMode.SINGLE_PREMIUM, 1)
            for ((mode, col) in modeCols) {
                val count = row.getCell(col).numericOrNull()?.toInt() ?: continue
                addInstalment(ctx, seen, tenure, payTenure, mode, count)
            }
        }
        if (ctx.instalment.isEmpty()) computeInstalmentConfig(ctx)
    }

    private fun computeInstalmentConfig(ctx: Ctx) {
        val seen = mutableSetOf<String>()
        for (policyTenure in Tenure.entries) {
            for (payTenure in Tenure.entries) {
                for (mode in PaymentMode.entries) {
                    addInstalment(ctx, seen, policyTenure, payTenure, mode, InstalmentRow.computeCount(policyTenure, mode))
                }
            }
        }
    }

    private fun addInstalment(
        ctx: Ctx, seen: MutableSet<String>,
        policyTenure: Tenure, payTenure: Tenure, mode: PaymentMode, count: Int,
    ) {
        val key = "${policyTenure.name}|${payTenure.name}|${mode.name}"
        if (!seen.add(key)) return
        ctx.instalment.add(InstalmentRow.of(ctx.version, policyTenure, payTenure, mode, count))
    }
}
