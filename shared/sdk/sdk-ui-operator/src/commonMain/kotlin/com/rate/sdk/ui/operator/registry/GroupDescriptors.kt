package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.group.BenefitSchedule
import com.rate.sdk.catalog.model.group.BenefitScheduleLine
import com.rate.sdk.catalog.model.group.CheckupPackageType
import com.rate.sdk.catalog.model.group.ChronicOpdGrid
import com.rate.sdk.catalog.model.group.ConsumablesList
import com.rate.sdk.catalog.model.group.ConsumablesListType
import com.rate.sdk.catalog.model.group.DayCareItem
import com.rate.sdk.catalog.model.group.DayCareProcedure
import com.rate.sdk.catalog.model.group.DeviceCategory
import com.rate.sdk.catalog.model.group.DisabilityTableType
import com.rate.sdk.catalog.model.group.EligibilityCriteria
import com.rate.sdk.catalog.model.group.GroupGrade
import com.rate.sdk.catalog.model.group.GroupPolicyType
import com.rate.sdk.catalog.model.group.GroupProductConfig
import com.rate.sdk.catalog.model.group.HealthCheckupPackage
import com.rate.sdk.catalog.model.group.MedicalDeviceCatalog
import com.rate.sdk.catalog.model.group.PpdPtdRow
import com.rate.sdk.catalog.model.group.PpdPtdSourceGroup
import com.rate.sdk.catalog.model.group.PpdPtdTable
import com.rate.sdk.catalog.model.group.RiskClass
import com.rate.sdk.catalog.model.group.SublimitScope
import com.rate.sdk.catalog.model.group.SurgicalSublimit
import com.rate.sdk.catalog.model.group.VaccinationCatalog
import com.rate.sdk.catalog.model.group.VaccinationItem
import com.rate.sdk.catalog.model.group.WaitingPeriod
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField
import com.rate.sdk.ui.operator.model.RowColumn
import com.rate.sdk.ui.operator.model.RowSpec

/**
 * Admin-CRUD descriptors for the GROUP product line. The ProductLine discriminator keeps GROUP a
 * facet of the one catalog, not a parallel tree — but its extra constructs (grades, schedules,
 * waiting periods, eligibility, PPD/PTD, day-care, consumables, health-checkup, chronic-OPD) each
 * get a descriptor here so the operator console edits them through the same generic screen.
 */
internal object GroupDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    // ── Row shapes for structured (TABLE) sub-editors ───────────────────────
    /** BenefitScheduleLine row: cover code + limit + option + sub-limit copy + included. */
    private val benefitLineRow = RowSpec(
        addLabel = "Add benefit line",
        columns = listOf(
            RowColumn("coverCode", "Cover code", FieldKind.TEXT),
            RowColumn("coverName", "Cover name", FieldKind.TEXT),
            RowColumn("limit", "Limit", FieldKind.MONEY),
            RowColumn("selectedOption", "Selected option", FieldKind.TEXT),
            RowColumn("subLimitText", "Sub-limit", FieldKind.MULTILINE),
            RowColumn("included", "Included", FieldKind.BOOL),
        ),
    )

    /** PpdPtdRow row: slNo / event / fraction of SI / category / sub-row linkage. */
    private val ppdPtdRow = RowSpec(
        addLabel = "Add disability row",
        columns = listOf(
            RowColumn("slNo", "Sl no", FieldKind.TEXT),
            RowColumn("insuredEvent", "Insured event", FieldKind.MULTILINE),
            RowColumn("percentOfSI", "% of SI (fraction)", FieldKind.NUMBER, helper = "0..1, e.g. 0.5 = 50%"),
            RowColumn("category", "Category", FieldKind.TEXT, helper = "e.g. Fingers, Toes, Burns"),
            RowColumn("parentSlNo", "Parent sl no", FieldKind.TEXT, helper = "Sl no of the parent row when this is a sub-row"),
            RowColumn("isSubRow", "Sub-row", FieldKind.BOOL),
        ),
    )

    /** RiskClass row: occupational class with loading + exclusions. */
    private val riskClassRow = RowSpec(
        addLabel = "Add risk class",
        columns = listOf(
            RowColumn("name", "Class name", FieldKind.TEXT, helper = "e.g. Class I - Administrative"),
            RowColumn("occupations", "Occupations", FieldKind.LIST),
            RowColumn("premiumLoadingFactor", "Loading factor", FieldKind.NUMBER, helper = "e.g. 1.25 = +25%; blank = none"),
            RowColumn("exclusions", "Exclusions", FieldKind.LIST),
        ),
    )

    /** VaccinationItem row: slNo / name. */
    private val vaccinationItemRow = RowSpec(
        addLabel = "Add vaccination",
        columns = listOf(
            RowColumn("slNo", "Sl no", FieldKind.NUMBER),
            RowColumn("name", "Name", FieldKind.TEXT),
        ),
    )

    /** DeviceCategory row: category heading + the device names under it. */
    private val deviceCategoryRow = RowSpec(
        addLabel = "Add category",
        columns = listOf(
            RowColumn("name", "Category", FieldKind.TEXT),
            RowColumn("devices", "Devices", FieldKind.LIST),
        ),
    )

    /** DayCareItem row: number / name. */
    private val dayCareItemRow = RowSpec(
        addLabel = "Add procedure",
        columns = listOf(
            RowColumn("number", "Number", FieldKind.NUMBER),
            RowColumn("name", "Procedure name", FieldKind.TEXT),
        ),
    )

    val groupProductConfig = TypedEntityDescriptor(
        id = "group-products",
        singular = "Group Product",
        plural = "Group Products",
        description = "Top-level Employer-Employee / GHI product config: relations, family constructs, geography, tenures.",
        isGroup = true,
        serializer = GroupProductConfig.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("policyType", "Policy type", FieldKind.ENUM, enumNames(GroupPolicyType.entries.toTypedArray())),
            FormField("geography", "Geography", FieldKind.TEXT),
            FormField("relationsCovered", "Relations covered", FieldKind.LIST),
            FormField("familyConstructs", "Family constructs", FieldKind.LIST, helper = "ESCP / TCS combinations"),
            FormField("availableTenures", "Available tenures (years)", FieldKind.LIST),
            FormField("minSumInsured", "Min SI", FieldKind.MONEY),
            FormField("maxSumInsured", "Max SI", FieldKind.MONEY),
            FormField("premiumPaymentTerms", "Premium payment terms (years)", FieldKind.LIST),
            FormField("premiumPaymentModes", "Premium payment modes", FieldKind.LIST, helper = "Monthly / Quarterly / Half Yearly / Yearly / Single / Modular"),
            FormField("entryAge", "Entry age", FieldKind.TEXT, helper = "e.g. 0 Years"),
            FormField("exitAge", "Exit age", FieldKind.TEXT, helper = "e.g. Lifelong"),
            FormField("gradeRefs", "Grades", FieldKind.MULTI_SELECT, refEntityId = "group-grades"),
            FormField("eligibilityRef", "Eligibility", FieldKind.ENTITY_PICKER, refEntityId = "eligibility"),
            FormField("sectionRefs", "Sections", FieldKind.MULTI_SELECT, refEntityId = "sections"),
        ),
        columns = listOf(
            "Code" to { g: GroupProductConfig -> g.code },
            "Name" to { g -> g.name },
            "Policy" to { g -> g.policyType.name },
            "Grades" to { g -> g.gradeRefs.size.toString() },
        ),
        factory = { GroupProductConfig(code = "GROUP_NEW", name = "New group product") },
        reader = { g ->
            mapOf(
                "code" to g.code, "name" to g.name, "policyType" to g.policyType.name,
                "geography" to g.geography,
                "relationsCovered" to FormCodec.fmtList(g.relationsCovered),
                "familyConstructs" to FormCodec.fmtList(g.familyConstructs),
                "availableTenures" to FormCodec.fmtList(g.availableTenures),
                "minSumInsured" to FormCodec.fmtMoney(g.minSumInsured),
                "maxSumInsured" to FormCodec.fmtMoney(g.maxSumInsured),
                "premiumPaymentTerms" to FormCodec.fmtList(g.premiumPaymentTerms),
                "premiumPaymentModes" to FormCodec.fmtList(g.premiumPaymentModes),
                "entryAge" to g.entryAge,
                "exitAge" to g.exitAge,
                "gradeRefs" to FormCodec.fmtList(g.gradeRefs),
                "eligibilityRef" to (g.eligibilityRef ?: ""),
                "sectionRefs" to FormCodec.fmtList(g.sectionRefs),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                policyType = FormCodec.enum(b, "policyType", base.policyType),
                geography = FormCodec.str(b, "geography", base.geography),
                relationsCovered = FormCodec.list(b, "relationsCovered"),
                familyConstructs = FormCodec.list(b, "familyConstructs"),
                availableTenures = FormCodec.intList(b, "availableTenures"),
                minSumInsured = FormCodec.money(b, "minSumInsured", base.minSumInsured),
                maxSumInsured = FormCodec.money(b, "maxSumInsured", base.maxSumInsured),
                premiumPaymentTerms = FormCodec.intList(b, "premiumPaymentTerms"),
                premiumPaymentModes = FormCodec.list(b, "premiumPaymentModes"),
                entryAge = FormCodec.str(b, "entryAge", base.entryAge),
                exitAge = FormCodec.str(b, "exitAge", base.exitAge),
                gradeRefs = FormCodec.list(b, "gradeRefs"),
                eligibilityRef = FormCodec.strOrNull(b, "eligibilityRef"),
                sectionRefs = FormCodec.list(b, "sectionRefs"),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val groupGrade = TypedEntityDescriptor(
        id = "group-grades",
        singular = "Grade",
        plural = "Group Grades",
        description = "Employee bands with their own SI + benefit schedules.",
        isGroup = true,
        serializer = GroupGrade.serializer(),
        fields = listOf(
            FormField("grade", "Grade", FieldKind.TEXT, required = true),
            FormField("groupProductRef", "Group product", FieldKind.ENTITY_PICKER, refEntityId = "group-products"),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("sumInsured", "Sum insured", FieldKind.MONEY),
            FormField("benefitScheduleRefs", "Benefit schedules", FieldKind.MULTI_SELECT, refEntityId = "benefit-schedules"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Grade" to { g: GroupGrade -> g.grade },
            "SI" to { g -> g.sumInsured.formatIndian(showDecimals = false) },
            "Schedules" to { g -> g.benefitScheduleRefs.size.toString() },
        ),
        factory = { GroupGrade(grade = "Grade A") },
        reader = { g ->
            mapOf(
                "grade" to g.grade, "groupProductRef" to g.groupProductRef,
                "description" to g.description, "sumInsured" to FormCodec.fmtMoney(g.sumInsured),
                "benefitScheduleRefs" to FormCodec.fmtList(g.benefitScheduleRefs),
                "displayOrder" to g.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                grade = FormCodec.str(b, "grade", base.grade),
                groupProductRef = FormCodec.str(b, "groupProductRef", base.groupProductRef),
                description = FormCodec.str(b, "description", base.description),
                sumInsured = FormCodec.money(b, "sumInsured", base.sumInsured),
                benefitScheduleRefs = FormCodec.list(b, "benefitScheduleRefs"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "grade").isBlank()) add("Grade is required") } },
    )

    val benefitSchedule = TypedEntityDescriptor(
        id = "benefit-schedules",
        singular = "Benefit Schedule",
        plural = "Benefit Schedules",
        description = "Resolved cover limits per grade — edit each benefit line (cover, limit, option).",
        isGroup = true,
        serializer = BenefitSchedule.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("groupProductRef", "Group product", FieldKind.ENTITY_PICKER, refEntityId = "group-products"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
            FormField("lines", "Benefit lines", FieldKind.TABLE, rowSpec = benefitLineRow, helper = "Covers configured by this schedule with their limits"),
        ),
        columns = listOf(
            "Name" to { s: BenefitSchedule -> s.name },
            "Lines" to { s -> s.lines.size.toString() },
        ),
        factory = { BenefitSchedule(name = "New schedule") },
        reader = { s ->
            mapOf(
                "name" to s.name, "groupProductRef" to s.groupProductRef,
                "displayOrder" to s.displayOrder.toString(),
                "lines" to FormCodec.encodeList(BenefitScheduleLine.serializer(), s.lines),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                groupProductRef = FormCodec.str(b, "groupProductRef", base.groupProductRef),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
                lines = FormCodec.decodeList(BenefitScheduleLine.serializer(), b, "lines"),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") } },
    )

    val waitingPeriod = TypedEntityDescriptor(
        id = "waiting-periods",
        singular = "Waiting Period",
        plural = "Waiting Periods",
        description = "Waiting-period clauses (PED, specific illnesses, maternity).",
        isGroup = true,
        serializer = WaitingPeriod.serializer(),
        fields = listOf(
            FormField("clause", "Clause", FieldKind.TEXT, required = true),
            FormField("period", "Period", FieldKind.TEXT, required = true),
            FormField("slNo", "Sl no", FieldKind.NUMBER),
            FormField("groupProductRef", "Group product", FieldKind.ENTITY_PICKER, refEntityId = "group-products"),
            FormField("optionsToModify", "Options to modify", FieldKind.TEXT),
        ),
        columns = listOf(
            "Sl" to { w: WaitingPeriod -> w.slNo.toString() },
            "Clause" to { w -> w.clause },
            "Period" to { w -> w.period },
        ),
        factory = { WaitingPeriod(clause = "New clause", period = "") },
        reader = { w ->
            mapOf(
                "clause" to w.clause, "period" to w.period, "slNo" to w.slNo.toString(),
                "groupProductRef" to w.groupProductRef, "optionsToModify" to w.optionsToModify,
            )
        },
        editor = { base, b ->
            base.copy(
                clause = FormCodec.str(b, "clause", base.clause),
                period = FormCodec.str(b, "period", base.period),
                slNo = FormCodec.int(b, "slNo", base.slNo),
                groupProductRef = FormCodec.str(b, "groupProductRef", base.groupProductRef),
                optionsToModify = FormCodec.str(b, "optionsToModify", base.optionsToModify),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "clause").isBlank()) add("Clause is required") } },
    )

    val eligibilityCriteria = TypedEntityDescriptor(
        id = "eligibility",
        singular = "Eligibility",
        plural = "Eligibility Criteria",
        description = "Entry/renewal age, proposer/cover/policy type for a group product.",
        isGroup = true,
        serializer = EligibilityCriteria.serializer(),
        fields = listOf(
            FormField("groupProductRef", "Group product", FieldKind.ENTITY_PICKER, refEntityId = "group-products"),
            FormField("minEntryAge", "Min entry age", FieldKind.TEXT, helper = "e.g. Day1"),
            FormField("maxEntryAge", "Max entry age", FieldKind.TEXT, helper = "e.g. Lifelong"),
            FormField("renewalAge", "Renewal age", FieldKind.TEXT),
            FormField("minEntryAgeYears", "Min entry age (years)", FieldKind.NUMBER),
            FormField("maxEntryAgeYears", "Max entry age (years)", FieldKind.NUMBER),
            FormField("proposerType", "Proposer type", FieldKind.TEXT),
            FormField("coverType", "Cover type", FieldKind.TEXT),
            FormField("policyType", "Policy type", FieldKind.TEXT),
            FormField("riskClasses", "Risk classes", FieldKind.TABLE, rowSpec = riskClassRow, helper = "Occupational risk classes and their loadings/exclusions"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
        ),
        columns = listOf(
            "Product" to { e: EligibilityCriteria -> e.groupProductRef.ifBlank { "—" } },
            "Entry" to { e -> "${e.minEntryAge}–${e.maxEntryAge}" },
            "Renewal" to { e -> e.renewalAge },
        ),
        factory = { EligibilityCriteria() },
        reader = { e ->
            mapOf(
                "groupProductRef" to e.groupProductRef, "minEntryAge" to e.minEntryAge,
                "maxEntryAge" to e.maxEntryAge, "renewalAge" to e.renewalAge,
                "minEntryAgeYears" to (e.minEntryAgeYears?.toString() ?: ""),
                "maxEntryAgeYears" to (e.maxEntryAgeYears?.toString() ?: ""),
                "proposerType" to e.proposerType, "coverType" to e.coverType,
                "policyType" to e.policyType, "notes" to e.notes,
                "riskClasses" to FormCodec.encodeList(RiskClass.serializer(), e.riskClasses),
            )
        },
        editor = { base, b ->
            base.copy(
                groupProductRef = FormCodec.str(b, "groupProductRef", base.groupProductRef),
                minEntryAge = FormCodec.str(b, "minEntryAge", base.minEntryAge),
                maxEntryAge = FormCodec.str(b, "maxEntryAge", base.maxEntryAge),
                renewalAge = FormCodec.str(b, "renewalAge", base.renewalAge),
                minEntryAgeYears = FormCodec.strOrNull(b, "minEntryAgeYears")?.toIntOrNull(),
                maxEntryAgeYears = FormCodec.strOrNull(b, "maxEntryAgeYears")?.toIntOrNull(),
                proposerType = FormCodec.str(b, "proposerType", base.proposerType),
                coverType = FormCodec.str(b, "coverType", base.coverType),
                policyType = FormCodec.str(b, "policyType", base.policyType),
                riskClasses = FormCodec.decodeList(RiskClass.serializer(), b, "riskClasses"),
                notes = FormCodec.str(b, "notes", base.notes),
            )
        },
    )

    val ppdPtdTable = TypedEntityDescriptor(
        id = "ppd-ptd",
        singular = "PPD/PTD Table",
        plural = "PPD/PTD Tables",
        description = "Permanent partial/total disability schedules (event → % of SI). Edit the rows directly.",
        isGroup = true,
        serializer = PpdPtdTable.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT),
            FormField("tableType", "Table type", FieldKind.ENUM, enumNames(DisabilityTableType.entries.toTypedArray())),
            FormField("sourceGroup", "Source group", FieldKind.ENUM, enumNames(PpdPtdSourceGroup.entries.toTypedArray()), helper = "Side-by-side source block this table came from"),
            FormField("groupProductRef", "Group product", FieldKind.ENTITY_PICKER, refEntityId = "group-products"),
            FormField("rows", "Disability rows", FieldKind.TABLE, rowSpec = ppdPtdRow, helper = "Insured event → fraction of SI payable"),
            FormField("reviewNotes", "Review notes", FieldKind.MULTILINE, helper = "Reviewer notes captured during ingestion/QA"),
        ),
        columns = listOf(
            "Name" to { t: PpdPtdTable -> t.name.ifBlank { t.tableType.name } },
            "Type" to { t -> t.tableType.name },
            "Rows" to { t -> t.rows.size.toString() },
        ),
        factory = { PpdPtdTable() },
        reader = { t ->
            mapOf(
                "name" to t.name, "tableType" to t.tableType.name,
                "sourceGroup" to t.sourceGroup.name,
                "groupProductRef" to t.groupProductRef,
                "rows" to FormCodec.encodeList(PpdPtdRow.serializer(), t.rows),
                "reviewNotes" to t.reviewNotes,
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                tableType = FormCodec.enum(b, "tableType", base.tableType),
                sourceGroup = FormCodec.enum(b, "sourceGroup", base.sourceGroup),
                groupProductRef = FormCodec.str(b, "groupProductRef", base.groupProductRef),
                rows = FormCodec.decodeList(PpdPtdRow.serializer(), b, "rows"),
                reviewNotes = FormCodec.str(b, "reviewNotes", base.reviewNotes),
            )
        },
    )

    val dayCareProcedure = TypedEntityDescriptor(
        id = "day-care",
        singular = "Day-Care Group",
        plural = "Day-Care Procedures",
        description = "Day-care treatment groups (Annexure II). Edit the numbered procedures directly.",
        isGroup = true,
        serializer = DayCareProcedure.serializer(),
        fields = listOf(
            FormField("category", "Category", FieldKind.TEXT, required = true),
            FormField("listCode", "List code", FieldKind.TEXT),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
            FormField("items", "Procedures", FieldKind.TABLE, rowSpec = dayCareItemRow, helper = "Numbered day-care procedures in this category"),
        ),
        columns = listOf(
            "Category" to { d: DayCareProcedure -> d.category },
            "Items" to { d -> d.items.size.toString() },
        ),
        factory = { DayCareProcedure(category = "New category") },
        reader = { d ->
            mapOf(
                "category" to d.category, "listCode" to d.listCode,
                "displayOrder" to d.displayOrder.toString(),
                "items" to FormCodec.encodeList(DayCareItem.serializer(), d.items),
            )
        },
        editor = { base, b ->
            base.copy(
                category = FormCodec.str(b, "category", base.category),
                listCode = FormCodec.str(b, "listCode", base.listCode),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
                items = FormCodec.decodeList(DayCareItem.serializer(), b, "items"),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "category").isBlank()) add("Category is required") } },
    )

    val consumablesList = TypedEntityDescriptor(
        id = "consumables",
        singular = "Consumables List",
        plural = "Consumables Lists",
        description = "The four consumables lists (I–IV).",
        isGroup = true,
        serializer = ConsumablesList.serializer(),
        fields = listOf(
            FormField("listType", "List type", FieldKind.ENUM, enumNames(ConsumablesListType.entries.toTypedArray())),
            FormField("title", "Title", FieldKind.TEXT),
            FormField("items", "Items (comma/newline-sep)", FieldKind.MULTILINE),
        ),
        columns = listOf(
            "Type" to { c: ConsumablesList -> c.listType.name },
            "Title" to { c -> c.title },
            "Items" to { c -> c.items.size.toString() },
        ),
        factory = { ConsumablesList(listType = ConsumablesListType.LIST_I) },
        reader = { c ->
            mapOf(
                "listType" to c.listType.name, "title" to c.title,
                "items" to c.items.joinToString("\n"),
            )
        },
        editor = { base, b ->
            base.copy(
                listType = FormCodec.enum(b, "listType", base.listType),
                title = FormCodec.str(b, "title", base.title),
                items = FormCodec.list(b, "items"),
            )
        },
    )

    val healthCheckupPackage = TypedEntityDescriptor(
        id = "health-checkup",
        singular = "Checkup Package",
        plural = "Health-Checkup Packages",
        description = "Health check-up packages (tests + home/center visit).",
        isGroup = true,
        serializer = HealthCheckupPackage.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("packageType", "Package type", FieldKind.ENUM, enumNames(CheckupPackageType.entries.toTypedArray())),
            FormField("tests", "Tests (comma/newline-sep)", FieldKind.MULTILINE),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Name" to { h: HealthCheckupPackage -> h.name },
            "Type" to { h -> h.packageType.name },
            "Tests" to { h -> h.tests.size.toString() },
        ),
        factory = { HealthCheckupPackage(name = "New package") },
        reader = { h ->
            mapOf(
                "name" to h.name, "packageType" to h.packageType.name,
                "tests" to h.tests.joinToString("\n"), "displayOrder" to h.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                packageType = FormCodec.enum(b, "packageType", base.packageType),
                tests = FormCodec.list(b, "tests"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") } },
    )

    val chronicOpdGrid = TypedEntityDescriptor(
        id = "chronic-opd",
        singular = "Chronic-OPD Row",
        plural = "Chronic-OPD Grid",
        description = "Chronic-management OPD entitlements per condition.",
        isGroup = true,
        serializer = ChronicOpdGrid.serializer(),
        fields = listOf(
            FormField("condition", "Condition", FieldKind.TEXT, required = true),
            FormField("category", "Category", FieldKind.TEXT, helper = "Single / Double / Triple"),
            FormField("gpConsults", "GP consults", FieldKind.NUMBER),
            FormField("imConsults", "IM consults", FieldKind.NUMBER),
            FormField("superSpecialist", "Super-specialist", FieldKind.TEXT),
            FormField("keyTests", "Key tests", FieldKind.LIST),
            FormField("testFrequency", "Test frequency", FieldKind.TEXT),
            FormField("healthCoaching", "Health coaching", FieldKind.LIST),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Condition" to { c: ChronicOpdGrid -> c.condition },
            "Category" to { c -> c.category },
            "GP/IM" to { c -> "${c.gpConsults}/${c.imConsults}" },
        ),
        factory = { ChronicOpdGrid(condition = "New condition") },
        reader = { c ->
            mapOf(
                "condition" to c.condition, "category" to c.category,
                "gpConsults" to c.gpConsults.toString(), "imConsults" to c.imConsults.toString(),
                "superSpecialist" to c.superSpecialist,
                "keyTests" to FormCodec.fmtList(c.keyTests),
                "testFrequency" to c.testFrequency,
                "healthCoaching" to FormCodec.fmtList(c.healthCoaching),
                "displayOrder" to c.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                condition = FormCodec.str(b, "condition", base.condition),
                category = FormCodec.str(b, "category", base.category),
                gpConsults = FormCodec.int(b, "gpConsults", base.gpConsults),
                imConsults = FormCodec.int(b, "imConsults", base.imConsults),
                superSpecialist = FormCodec.str(b, "superSpecialist", base.superSpecialist),
                keyTests = FormCodec.list(b, "keyTests"),
                testFrequency = FormCodec.str(b, "testFrequency", base.testFrequency),
                healthCoaching = FormCodec.list(b, "healthCoaching"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "condition").isBlank()) add("Condition is required") } },
    )

    val surgicalSublimit = TypedEntityDescriptor(
        id = "surgical-sublimits",
        singular = "Surgical Sub-limit",
        plural = "Surgical Sub-limits",
        description = "Per-claim surgery sub-limits expressed as a % of opted SI (Annexure). SI-agnostic, resolved at quote time.",
        isGroup = true,
        serializer = SurgicalSublimit.serializer(),
        fields = listOf(
            FormField("surgeryName", "Surgery name", FieldKind.TEXT, required = true),
            FormField("slNo", "Sl no", FieldKind.NUMBER),
            FormField("category", "Category", FieldKind.TEXT, helper = "Optional grouping heading"),
            FormField("minPercentOfSI", "Min % of SI (fraction)", FieldKind.NUMBER, helper = "e.g. 0.10 for 10%; blank = open-ended"),
            FormField("maxPercentOfSI", "Max % of SI (fraction)", FieldKind.NUMBER, helper = "e.g. 0.90 for 90%; blank = open-ended"),
            FormField("scope", "Scope", FieldKind.ENUM, enumNames(SublimitScope.entries.toTypedArray())),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Surgery" to { s: SurgicalSublimit -> s.surgeryName },
            "Category" to { s -> s.category ?: "—" },
            "Min–Max %" to { s ->
                val lo = s.minPercentOfSI?.let { "${(it * 100).toInt()}%" } ?: "—"
                val hi = s.maxPercentOfSI?.let { "${(it * 100).toInt()}%" } ?: "—"
                "$lo – $hi"
            },
            "Scope" to { s -> s.scope.name },
        ),
        factory = { SurgicalSublimit(surgeryName = "New surgery") },
        reader = { s ->
            mapOf(
                "surgeryName" to s.surgeryName,
                "slNo" to s.slNo.toString(),
                "category" to (s.category ?: ""),
                "minPercentOfSI" to (s.minPercentOfSI?.toString() ?: ""),
                "maxPercentOfSI" to (s.maxPercentOfSI?.toString() ?: ""),
                "scope" to s.scope.name,
                "displayOrder" to s.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                surgeryName = FormCodec.str(b, "surgeryName", base.surgeryName),
                slNo = FormCodec.int(b, "slNo", base.slNo),
                category = FormCodec.strOrNull(b, "category"),
                minPercentOfSI = FormCodec.strOrNull(b, "minPercentOfSI")?.toDoubleOrNull(),
                maxPercentOfSI = FormCodec.strOrNull(b, "maxPercentOfSI")?.toDoubleOrNull(),
                scope = FormCodec.enum(b, "scope", base.scope),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "surgeryName").isBlank()) add("Surgery name is required") } },
    )

    val vaccinationCatalog = TypedEntityDescriptor(
        id = "vaccination-catalogs",
        singular = "Vaccination Catalog",
        plural = "Vaccination Catalogs",
        description = "Named vaccination lists referenced by vaccination covers (e.g. Adult Vaccination List).",
        isGroup = true,
        serializer = VaccinationCatalog.serializer(),
        fields = listOf(
            FormField("listCode", "List code", FieldKind.TEXT, required = true, helper = "Stable lookup key covers point at"),
            FormField("name", "Name", FieldKind.TEXT),
            FormField("items", "Vaccinations", FieldKind.TABLE, rowSpec = vaccinationItemRow, helper = "The covered vaccinations in order"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Code" to { c: VaccinationCatalog -> c.listCode },
            "Name" to { c -> c.name },
            "Items" to { c -> c.items.size.toString() },
        ),
        factory = { VaccinationCatalog(listCode = "VAX_NEW") },
        reader = { c ->
            mapOf(
                "listCode" to c.listCode, "name" to c.name,
                "items" to FormCodec.encodeList(VaccinationItem.serializer(), c.items),
                "displayOrder" to c.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                listCode = FormCodec.str(b, "listCode", base.listCode),
                name = FormCodec.str(b, "name", base.name),
                items = FormCodec.decodeList(VaccinationItem.serializer(), b, "items"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "listCode").isBlank()) add("List code is required") } },
    )

    val medicalDeviceCatalog = TypedEntityDescriptor(
        id = "medical-device-catalogs",
        singular = "Medical-Device Catalog",
        plural = "Medical-Device Catalogs",
        description = "Named medical-device lists (category → devices) referenced by device/durable-equipment covers.",
        isGroup = true,
        serializer = MedicalDeviceCatalog.serializer(),
        fields = listOf(
            FormField("listCode", "List code", FieldKind.TEXT, required = true, helper = "Stable lookup key covers point at"),
            FormField("name", "Name", FieldKind.TEXT),
            FormField("categories", "Categories", FieldKind.TABLE, rowSpec = deviceCategoryRow, helper = "Category heading → device names"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Code" to { c: MedicalDeviceCatalog -> c.listCode },
            "Name" to { c -> c.name },
            "Categories" to { c -> c.categories.size.toString() },
        ),
        factory = { MedicalDeviceCatalog(listCode = "DEV_NEW") },
        reader = { c ->
            mapOf(
                "listCode" to c.listCode, "name" to c.name,
                "categories" to FormCodec.encodeList(DeviceCategory.serializer(), c.categories),
                "displayOrder" to c.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                listCode = FormCodec.str(b, "listCode", base.listCode),
                name = FormCodec.str(b, "name", base.name),
                categories = FormCodec.decodeList(DeviceCategory.serializer(), b, "categories"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "listCode").isBlank()) add("List code is required") } },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(
        groupProductConfig, groupGrade, benefitSchedule, waitingPeriod, eligibilityCriteria,
        ppdPtdTable, dayCareProcedure, consumablesList, healthCheckupPackage, chronicOpdGrid,
        surgicalSublimit, vaccinationCatalog, medicalDeviceCatalog,
    )
}
