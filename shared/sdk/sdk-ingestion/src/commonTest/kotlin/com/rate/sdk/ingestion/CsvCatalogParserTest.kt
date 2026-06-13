package com.rate.sdk.ingestion

import com.rate.core.regulatory.PayoutType
import com.rate.sdk.catalog.model.group.CheckupPackageType
import com.rate.sdk.catalog.model.group.ConsumablesListType
import com.rate.sdk.catalog.model.group.DisabilityTableType
import com.rate.sdk.catalog.model.group.GroupPolicyType
import com.rate.sdk.ingestion.handler.Csv
import com.rate.sdk.ingestion.handler.CsvCatalogParser
import com.rate.sdk.ingestion.handler.SourceHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvCatalogParserTest {

    private val parser = CsvCatalogParser()

    // ── CSV reader ──────────────────────────────────────────────────────────
    @Test
    fun csvHandlesQuotedFieldsWithCommasAndNewlines() {
        val text = "a,\"b,c\",d\n\"line1\nline2\",x,\"he said \"\"hi\"\"\"\n"
        val records = Csv.parse(text)
        assertEquals(2, records.size)
        assertEquals(listOf("a", "b,c", "d"), records[0])
        assertEquals("line1\nline2", records[1][0])
        assertEquals("he said \"hi\"", records[1][2])
    }

    @Test
    fun csvDoesNotEmitSpuriousFinalRecordOnTrailingNewline() {
        assertEquals(2, Csv.parse("a,b\nc,d\n").size)
        assertEquals(2, Csv.parse("a,b\nc,d").size)
    }

    // ── Sections (PBT Index shape) ──────────────────────────────────────────
    @Test
    fun parsesSectionsWithSubSectionsAndDayMarkers() {
        val csv = """
            Section #,Section name,Sub -Section ,Day 1,Day 2,New
            1,Personal Accident,1.1 Personal Accident,Day 1,-,Modified
            ,,1.2 Accidental Hospitalisation,,-,-
            2,Critical Illness,,Day 1,-,Modified
            9,OPD Cover,,-,Day 2,Modified
        """.trimIndent() + "\n"
        val sections = parser.parseSections(csv)
        assertTrue(sections.size >= 3)
        val pa = sections.first { it.name.contains("Personal Accident") }
        assertEquals("1.1", pa.sectionNumber)
        assertTrue(pa.day1)
        val opd = sections.first { it.name.contains("OPD") }
        assertTrue(opd.day2)
        assertTrue(!opd.day1)
    }

    // ── Group product config ────────────────────────────────────────────────
    @Test
    fun parsesGroupProductBoundaryConditions() {
        val csv = """
            Group Product - Employer Employee,,,
            Features,Type,Details,
            Policy Type,Boundary Conditions,Floater / Non-Floater,
            Relations Covered,Boundary Conditions,"Self, Spouse, Son, Daughter",LGBTQ
            Family Floater Size,Boundary Conditions,ESCP and all TCS Combinations,"Employee,Spouse,Children,Parents"
            Tenure,Boundary Conditions,Tenure :1/2/3/4/5 years,
            Geography,Boundary Conditions,India Only,
            PART 1- BASE COVERS,,,
            Sl No,Cover,Cover Description,
            1,In-Patient Hospitalization Expenses,Hospitalization covered up to Sum Insured,
        """.trimIndent() + "\n"
        val gp = parser.parseGroupProductConfig(csv)
        assertEquals(GroupPolicyType.FLOATER, gp.policyType)
        assertEquals(listOf(1, 2, 3, 4, 5), gp.availableTenures)
        assertTrue(gp.relationsCovered.contains("Self"))
        assertTrue(gp.familyConstructs.contains("Employee"))
        assertEquals("India Only", gp.geography)
    }

    // ── Base benefit schedule + covers ──────────────────────────────────────
    @Test
    fun parsesBaseBenefitScheduleLines() {
        val csv = """
            PART 1- BASE COVERS,,,,,,
            Sl No,Cover,Cover Description,Minimum Sum Insured,Maximum Sum Insured,Additional Details,Logic
            1,In-Patient Hospitalization Expenses,Hospitalization up to SI,10000,300 Lacs,opts,Base SI
            2,Room Rent,Limit cover towards Room Rent,500,"20,000 Per Day",opts,Part Of Base SI
        """.trimIndent() + "\n"
        val schedule = parser.parseBaseBenefitSchedule(csv)
        assertEquals(2, schedule.lines.size)
        val inpatient = schedule.lines.first()
        assertEquals("in_patient_hospitalization_expenses", inpatient.coverCode)
        // 300 Lacs = 3,00,00,000 rupees.
        assertEquals(3_00_00_000L * 100, inpatient.limit.paise)
    }

    // ── Critical illness lists ──────────────────────────────────────────────
    @Test
    fun parsesFlatCiListAndDedupes() {
        val csv = """
            101 list of CI,
            Sl no,Name of CI
            1,Major Cancer
            2,Kidney Failure
            3,Major Cancer
        """.trimIndent() + "\n"
        val list = parser.parseFlatCriticalIllnessList(csv, "CI_101")
        assertEquals(2, list.size) // dedup removes the repeat
        assertEquals(list.items.size, list.size)
        assertEquals(1, list.items.first().slNo)
    }

    @Test
    fun parsesTieredCiListsCumulatively() {
        val csv = """
            List of CI [Flexible],
            Plan 1,Plan2,Plan 3
            1 CI(Unique),4 CI(Unique),16 CI(Unique)
            Major Cancer,Kidney Failure,Open Chest
            ,Bone Marrow,Myocardial
        """.trimIndent() + "\n"
        val tiers = parser.parseTieredCriticalIllnessLists(csv)
        assertEquals(3, tiers.size)
        assertEquals(1, tiers[0].items.size) // Plan 1 = just Major Cancer
        assertEquals(3, tiers[1].items.size) // Plan 2 cumulative = +Kidney Failure +Bone Marrow
        assertEquals(5, tiers[2].items.size) // Plan 3 cumulative = +Open Chest +Myocardial
    }

    // ── Waiting periods ─────────────────────────────────────────────────────
    @Test
    fun parsesWaitingPeriods() {
        val csv = """
            Limits and Waiting Periods,,,
            Sl no,Clause,Waiting Period,Options to modify
            1,Pre-Existing Disease,3 Years since date of inception,0-36 Months
            2,30-Day Waiting Period,30 Days,0-90 Days
        """.trimIndent() + "\n"
        val wps = parser.parseWaitingPeriods(csv)
        assertEquals(2, wps.size)
        assertEquals("Pre-Existing Disease", wps[0].clause)
        assertEquals(1, wps[0].slNo)
    }

    // ── Eligibility ─────────────────────────────────────────────────────────
    @Test
    fun parsesEligibility() {
        val csv = """
            Eligibility Criteria,,
            Minimum Entry Age,Day1,
            Maximum Entry Age,Lifelong,
            Cover Type,Individual/Floater,
            Risk Categories,,
            Risk Class I,Students,
        """.trimIndent() + "\n"
        val e = parser.parseEligibility(csv)
        assertEquals("Day1", e.minEntryAge)
        assertEquals("Lifelong", e.maxEntryAge)
        assertEquals("Individual/Floater", e.coverType)
    }

    // ── PPD / PTD tables ────────────────────────────────────────────────────
    @Test
    fun parsesPpdPtdTables() {
        val csv = """
            PTD Table,,,
            Sr. No.,Insured Events,Amount payable,
            1,Total loss of sight of both eyes,1,
            2,Loss of two hands,1,
            PPD Table,,,
            Sr. No.,Insured Events,Amount payable,
            I,Loss of hearing both ears,0.75,
        """.trimIndent() + "\n"
        val tables = parser.parsePpdPtdTables(csv)
        assertTrue(tables.any { it.tableType == DisabilityTableType.PTD })
        assertTrue(tables.any { it.tableType == DisabilityTableType.PPD })
        val ptd = tables.first { it.tableType == DisabilityTableType.PTD }
        assertEquals(2, ptd.rows.size)
        assertEquals(1.0, ptd.rows.first().percentOfSI)
    }

    @Test
    fun parsesPpdPtdSideBySideColumnGroups() {
        // Two independent column groups: left at cols 0-2, right at cols 6-8.
        val csv = """
            PTD Table,,,,,,PTD Table,,
            Sr. No.,Insured Events,% SI,,,,Sr. No.,PTD,% SI
            1,Loss of both eyes,1,,,,1,Loss of sight both eyes,1
            2,Loss of two hands,1,,,,2,Loss of both feet,1
            PPD Table,,,,,,PPD Table,,
            Sr. No.,Insured Events,% SI,,,,Sl no,Loss Covered,% SI
            I,Loss of hearing both ears,0.75,,,,1,One entire hand,0.5
            ,a) Both ears,0.75,,,,,One entire foot,0.5
        """.trimIndent() + "\n"
        val tables = parser.parsePpdPtdTables(csv)
        // Two PTD + two PPD tables across the two column groups.
        assertEquals(2, tables.count { it.tableType == DisabilityTableType.PTD })
        assertEquals(2, tables.count { it.tableType == DisabilityTableType.PPD })
        // The RIGHT-group tables (previously dropped) are present with their own rows.
        assertTrue(tables.any { t -> t.rows.any { it.insuredEvent.contains("One entire hand") } })
        assertTrue(tables.any { t -> t.rows.any { it.insuredEvent.contains("One entire foot") } })
    }

    // ── Day-care procedures ─────────────────────────────────────────────────
    @Test
    fun parsesDayCareGroupedByCategory() {
        val csv = """
            Annexure – II: List of Day Care Treatments
            Microsurgical Operations on the middle ear
            1. Stapedotomy
            2. Revision of Stapedotomy
            Other operations on the middle ear
            3. Myringotomy
        """.trimIndent() + "\n"
        val groups = parser.parseDayCare(csv)
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].items.size)
        assertEquals(1, groups[0].items.first().number)
        assertEquals("Stapedotomy", groups[0].items.first().name)
    }

    // ── Annexure (three differently-shaped section groups) ──────────────────
    @Test
    fun parsesAnnexureThreeGroupsWithUniqueCodes() {
        val csv = """
            1,OPD Minor Procedure,,
            ,List of OPD Minor Procedures covered,,
            ,a. Drainage of abscess,,
            ,b. Injection,,
            2,Dental Treatment,,
            ,a. Dental Examination,,
            6,List of CI,Critical Illness,9 Cls
            ,1,Cancer Of Specific Severity,a
            ,2,Open Chest Cabg,a
            Sl no,ADULT VACCINATIONS,,
            1,Influenza,,
            2,Pneumonia,,
            List of Monitoring / Medical Devices,,,
            1,Cardio-Respiratory Monitoring Devices,,
            ,Blood Pressure Monitor,,
            ,Pulse Oximeter,,
            2,Diabetes Monitoring Devices,,
            ,Glucometer,,
        """.trimIndent() + "\n"
        val anx = parser.parseAnnexures(csv)
        // 2 OPD/Dental + 1 CI + 1 vaccinations + 2 device categories = 6 titled blocks.
        assertEquals(6, anx.size)
        // Codes are globally unique (no overwrites across groups).
        assertEquals(anx.size, anx.map { it.annexureCode }.distinct().size)
        // Group-1 letter-item list captured.
        assertTrue(anx.first { it.title.contains("OPD") }.items.any { it.contains("Drainage") })
        // Group-1 CI list: numbered col-1 rows captured as item names.
        assertTrue(anx.first { it.title.contains("List of CI") }.items.any { it.contains("Cancer") })
        // Group-2 vaccinations: numbered col-0 rows captured as items, NOT new blocks.
        val vacc = anx.first { it.title.contains("VACCINATIONS") }
        assertEquals(2, vacc.items.size)
        // Group-3 device categories captured with their sub-items.
        assertTrue(anx.first { it.title.contains("Cardio") }.items.any { it.contains("Pulse Oximeter") })
    }

    // ── Consumables (4 lists) ───────────────────────────────────────────────
    @Test
    fun parsesConsumablesFourLists() {
        val csv = """
            Sr No.,List I,Sr No.,List II,Sr No.,LIST III,Sr No.,LIST IV
            1,BABY FOOD,1,BABY CHARGES,1,HAIR REMOVAL CREAM,1,ADMISSION CHARGES
            2,BABY UTILITIES,2,HAND WASH,2,RAZORS,2,DIAGNOSTIC
        """.trimIndent() + "\n"
        val lists = parser.parseConsumables(csv)
        assertEquals(4, lists.size)
        assertEquals(ConsumablesListType.LIST_I, lists[0].listType)
        assertEquals(2, lists[0].items.size)
        assertTrue(lists[0].items.contains("BABY FOOD"))
    }

    // ── Health check-up packages ────────────────────────────────────────────
    @Test
    fun parsesHealthCheckupPackages() {
        val csv = """
            Package Name,Test Details,Package Type
            Package 1,"HbA1c, ECG, Lipid profile",Home Visit
            Package 6,"CBC, USG Abdomen",Center Visit
        """.trimIndent() + "\n"
        val pkgs = parser.parseHealthCheckupPackages(csv)
        assertEquals(2, pkgs.size)
        assertEquals(3, pkgs[0].tests.size)
        assertEquals(CheckupPackageType.HOME_VISIT, pkgs[0].packageType)
        assertEquals(CheckupPackageType.CENTER_VISIT, pkgs[1].packageType)
    }

    // ── Money parsing ───────────────────────────────────────────────────────
    @Test
    fun parsesIndianMoneyLimits() {
        assertEquals(0L, parser.parseMoneyLimit("Covered").paise)
        assertEquals(10000L * 100, parser.parseMoneyLimit("10000").paise)
        assertEquals(3_00_00_000L * 100, parser.parseMoneyLimit("300 Lacs").paise)
        assertEquals(25_00_00_000L * 100, parser.parseMoneyLimit("INR 25,00,00,000").paise)
    }

    // ── cover code stability ────────────────────────────────────────────────
    @Test
    fun coverCodeIsStableAndSlugified() {
        assertEquals("in_patient_hospitalization_expenses", parser.toCoverCode("In-Patient Hospitalization Expenses"))
        assertEquals("personal_accident", parser.toCoverCode("Personal Accident "))
    }

    // ── SHA dedupe hash ─────────────────────────────────────────────────────
    @Test
    fun sha256MatchesKnownVector() {
        // SHA-256("abc") canonical test vector.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SourceHash.ofText("abc"),
        )
        // Empty string.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SourceHash.ofText(""),
        )
    }

    @Test
    fun payoutTypeEnumStillReachable() {
        // Sanity: regulatory types resolve through the dependency graph.
        assertEquals(PayoutType.FIXED, PayoutType.valueOf("FIXED"))
    }
}
