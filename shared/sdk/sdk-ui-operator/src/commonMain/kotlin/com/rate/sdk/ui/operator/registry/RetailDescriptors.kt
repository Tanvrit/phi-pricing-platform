package com.rate.sdk.ui.operator.registry

import com.rate.core.money.Money
import com.rate.core.rating.ports.model.CoPaymentTable
import com.rate.core.rating.ports.model.GeographyScope
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanLifecycle
import com.rate.core.rating.ports.model.PlanType
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.UnderwritingCategory
import com.rate.core.regulatory.Zone
import com.rate.core.regulatory.CoverageLogic
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.AddOnItem
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.AnnexureCategory
import com.rate.sdk.catalog.model.CIItem
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CoverOption
import com.rate.sdk.catalog.model.CoverOptionGroup
import com.rate.sdk.catalog.model.CoverParamType
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.RateKind
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Sublimit
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.catalog.model.Vendor
import com.rate.sdk.catalog.model.VendorBank
import com.rate.sdk.catalog.model.VendorCategory
import com.rate.sdk.catalog.model.VendorDocType
import com.rate.core.regulatory.PayoutType
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField
import com.rate.sdk.ui.operator.model.RowColumn
import com.rate.sdk.ui.operator.model.RowSpec

/**
 * Admin-CRUD descriptors for the RETAIL line. One [TypedEntityDescriptor] per entity feeds the
 * generic [com.rate.sdk.ui.operator.screen.ConfigEntityScreen]. Each captures its `_id`-named
 * resource path, an ordered list editor field set, list columns, a factory, and the buffer↔entity
 * read/applyEdit closures. Nested/list-of-object fields (CoverOption, Sublimit, CIItem, AddOnItem)
 * are fully editable via [FieldKind.TABLE] row editors (JSON round-tripped through the buffer);
 * map fields (tieredPlanRefs) via [FieldKind.KEY_VALUE]; cross-entity references via
 * [FieldKind.ENTITY_PICKER] / [FieldKind.MULTI_SELECT]. Every model field is exposed.
 */
internal object RetailDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    // ── Row shapes for structured (TABLE / KEY_VALUE) sub-editors ───────────
    /** CoverOption row: label / logic / limits / engine param / param-type / range / details. */
    private val coverOptionRow = RowSpec(
        addLabel = "Add option",
        columns = listOf(
            RowColumn("label", "Label", FieldKind.TEXT),
            RowColumn("logic", "Logic", FieldKind.ENUM, enumNames(CoverageLogic.entries.toTypedArray())),
            RowColumn("paramType", "Param type", FieldKind.ENUM, enumNames(CoverParamType.entries.toTypedArray()), helper = "Amount / % of SI / count"),
            RowColumn("minLimit", "Min limit", FieldKind.MONEY),
            RowColumn("maxLimit", "Max limit", FieldKind.MONEY),
            RowColumn("paramKey", "Param key", FieldKind.TEXT, helper = "Engine param1 if distinct from label"),
            RowColumn("rangeText", "Range text", FieldKind.TEXT, helper = "Free-text range when not a single amount, e.g. ₹1L - ₹5L"),
            RowColumn("details", "Details", FieldKind.MULTILINE),
        ),
    )

    /**
     * CoverOptionGroup row: a categorised set of options. The nested per-option editing stays in the
     * flat `options` / `optionsParam2` editors; here the operator edits the group's category +
     * exclusivity. (Nested option rows are preserved via the JSON round-trip when only these cells
     * change.)
     */
    private val coverOptionGroupRow = RowSpec(
        addLabel = "Add option group",
        columns = listOf(
            RowColumn("category", "Category", FieldKind.TEXT),
            RowColumn("exclusive", "Exclusive (pick one)", FieldKind.BOOL),
        ),
    )

    /** Sublimit row: label / limit / details. */
    private val sublimitRow = RowSpec(
        addLabel = "Add sub-limit",
        columns = listOf(
            RowColumn("label", "Label", FieldKind.TEXT),
            RowColumn("limit", "Limit", FieldKind.MONEY),
            RowColumn("details", "Details", FieldKind.MULTILINE),
        ),
    )

    /** CIItem row: slNo / name. */
    private val ciItemRow = RowSpec(
        addLabel = "Add CI item",
        columns = listOf(
            RowColumn("slNo", "Sl no", FieldKind.NUMBER),
            RowColumn("name", "Name", FieldKind.TEXT),
        ),
    )

    /** tieredPlanRefs row: tier label → plan ids. */
    private val tieredPlanRefRow = RowSpec(
        addLabel = "Add tier mapping",
        columns = listOf(
            RowColumn("key", "Plan/tier label", FieldKind.TEXT),
            RowColumn("value", "Plan ids", FieldKind.LIST),
        ),
    )

    /** AddOnItem row: cover code + default params + label/description overrides. */
    private val addOnItemRow = RowSpec(
        addLabel = "Add bundle item",
        columns = listOf(
            RowColumn("coverCode", "Cover code", FieldKind.TEXT),
            RowColumn("defaultParam.param1", "Default param 1", FieldKind.TEXT),
            RowColumn("defaultParam.param2", "Default param 2", FieldKind.TEXT),
            RowColumn("title", "Title override", FieldKind.TEXT),
            RowColumn("description", "Description override", FieldKind.MULTILINE),
        ),
    )

    val plan = TypedEntityDescriptor(
        id = "plans",
        singular = "Plan",
        plural = "Plans",
        description = "Actuarial rating anchors — the engine reads these. Edit availability, SI grid, GST and lifecycle.",
        isGroup = false,
        serializer = Plan.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("planType", "Plan type", FieldKind.ENUM, enumNames(PlanType.entries.toTypedArray())),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("underwritingCategory", "UW category", FieldKind.ENUM, enumNames(UnderwritingCategory.entries.toTypedArray())),
            FormField("geographyScope", "Geography", FieldKind.ENUM, enumNames(GeographyScope.entries.toTypedArray())),
            FormField("coPaymentTable", "Co-pay table", FieldKind.ENUM, enumNames(CoPaymentTable.entries.toTypedArray())),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("availableSumInsureds", "Available SI (₹, comma-sep)", FieldKind.LIST, helper = "e.g. 1000000, 2500000, 5000000"),
            FormField("availableZones", "Available zones", FieldKind.LIST),
            FormField("availableFamilyTypes", "Available family types", FieldKind.LIST),
            FormField("allowedCoverIds", "Allowed covers", FieldKind.MULTI_SELECT, refEntityId = "covers", helper = "Empty = all covers available"),
            FormField("maxDiscountCap", "Max discount cap", FieldKind.NUMBER, helper = "Fraction, e.g. 0.30"),
            FormField("rateTableId", "Rate table id", FieldKind.TEXT),
            FormField("minAge", "Min age", FieldKind.NUMBER),
            FormField("maxAge", "Max age", FieldKind.NUMBER),
            FormField("gstRate", "GST rate", FieldKind.NUMBER, helper = "Fraction, e.g. 0.18"),
            FormField("isActive", "Active", FieldKind.BOOL),
            FormField("lifecycle", "Lifecycle", FieldKind.ENUM, enumNames(PlanLifecycle.entries.toTypedArray())),
            FormField("ciListRef", "CI list", FieldKind.ENTITY_PICKER, refEntityId = "ci-lists", helper = "CI list this plan tier offers"),
            FormField("gradeRefs", "Grades", FieldKind.MULTI_SELECT, refEntityId = "group-grades", helper = "Applicable grade definitions"),
            FormField("eligibilityRef", "Eligibility", FieldKind.ENTITY_PICKER, refEntityId = "eligibility", helper = "Eligibility rule-set governing this plan"),
            FormField("benefitScheduleRefs", "Benefit schedules", FieldKind.MULTI_SELECT, refEntityId = "benefit-schedules"),
            FormField("rateVersion", "Rate version", FieldKind.TEXT, helper = "Version tag of the rate set this plan resolves against"),
        ),
        columns = listOf(
            "Name" to { p: Plan -> p.name },
            "Type" to { p -> p.planType.displayName },
            "Line" to { p -> p.productLine.name },
            "Min–Max age" to { p -> "${p.minAge}–${p.maxAge}" },
            "Lifecycle" to { p -> p.lifecycle.name },
        ),
        factory = { Plan(name = "New plan", planType = PlanType.DOMESTIC) },
        reader = { p ->
            mapOf(
                "name" to p.name,
                "planType" to p.planType.name,
                "productLine" to p.productLine.name,
                "underwritingCategory" to p.underwritingCategory.name,
                "geographyScope" to p.geographyScope.name,
                "coPaymentTable" to p.coPaymentTable.name,
                "description" to p.description,
                "availableSumInsureds" to FormCodec.fmtList(p.availableSumInsureds),
                "availableZones" to FormCodec.fmtList(p.availableZones),
                "availableFamilyTypes" to FormCodec.fmtList(p.availableFamilyTypes),
                "allowedCoverIds" to FormCodec.fmtList(p.allowedCoverIds.toList()),
                "maxDiscountCap" to p.maxDiscountCap.toString(),
                "rateTableId" to p.rateTableId,
                "minAge" to p.minAge.toString(),
                "maxAge" to p.maxAge.toString(),
                "gstRate" to p.gstRate.toString(),
                "isActive" to p.isActive.toString(),
                "lifecycle" to p.lifecycle.name,
                "ciListRef" to (p.ciListRef ?: ""),
                "gradeRefs" to FormCodec.fmtList(p.gradeRefs),
                "eligibilityRef" to (p.eligibilityRef ?: ""),
                "benefitScheduleRefs" to FormCodec.fmtList(p.benefitScheduleRefs),
                "rateVersion" to (p.rateVersion ?: ""),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                planType = FormCodec.enum(b, "planType", base.planType),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                underwritingCategory = FormCodec.enum(b, "underwritingCategory", base.underwritingCategory),
                geographyScope = FormCodec.enum(b, "geographyScope", base.geographyScope),
                coPaymentTable = FormCodec.enum(b, "coPaymentTable", base.coPaymentTable),
                description = FormCodec.str(b, "description", base.description),
                availableSumInsureds = FormCodec.longList(b, "availableSumInsureds"),
                availableZones = FormCodec.list(b, "availableZones"),
                availableFamilyTypes = FormCodec.list(b, "availableFamilyTypes"),
                allowedCoverIds = FormCodec.list(b, "allowedCoverIds").toSet(),
                maxDiscountCap = FormCodec.double(b, "maxDiscountCap", base.maxDiscountCap),
                rateTableId = FormCodec.str(b, "rateTableId", base.rateTableId),
                minAge = FormCodec.int(b, "minAge", base.minAge),
                maxAge = FormCodec.int(b, "maxAge", base.maxAge),
                gstRate = FormCodec.double(b, "gstRate", base.gstRate),
                isActive = FormCodec.bool(b, "isActive", base.isActive),
                lifecycle = FormCodec.enum(b, "lifecycle", base.lifecycle),
                ciListRef = FormCodec.strOrNull(b, "ciListRef"),
                gradeRefs = FormCodec.list(b, "gradeRefs"),
                eligibilityRef = FormCodec.strOrNull(b, "eligibilityRef"),
                benefitScheduleRefs = FormCodec.list(b, "benefitScheduleRefs"),
                rateVersion = FormCodec.strOrNull(b, "rateVersion"),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.int(b, "minAge") > FormCodec.int(b, "maxAge")) add("Min age must be ≤ max age")
                FormCodec.double(b, "maxDiscountCap").let { if (it < 0 || it > 1) add("Max discount cap must be 0..1") }
            }
        },
    )

    val product = TypedEntityDescriptor(
        id = "products",
        singular = "Product",
        plural = "Products",
        description = "Catalog product tree heads (RETAIL/GROUP). Group sections, plans and UIN here.",
        isGroup = false,
        serializer = Product.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Unique within product line, e.g. PHI_BASIC"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("uin", "UIN", FieldKind.TEXT, helper = "IRDAI UIN once approved"),
            FormField("sectionRefs", "Sections", FieldKind.MULTI_SELECT, refEntityId = "sections"),
            FormField("planRefs", "Plans", FieldKind.MULTI_SELECT, refEntityId = "plans"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Code" to { p: Product -> p.code },
            "Name" to { p -> p.name },
            "Line" to { p -> p.productLine.name },
            "UIN" to { p -> p.uin ?: "—" },
            "Sections" to { p -> p.sectionRefs.size.toString() },
        ),
        factory = { Product(code = "NEW_PRODUCT", name = "New product") },
        reader = { p ->
            mapOf(
                "code" to p.code, "name" to p.name, "productLine" to p.productLine.name,
                "description" to p.description, "uin" to (p.uin ?: ""),
                "sectionRefs" to FormCodec.fmtList(p.sectionRefs),
                "planRefs" to FormCodec.fmtList(p.planRefs),
                "displayOrder" to p.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                description = FormCodec.str(b, "description", base.description),
                uin = FormCodec.strOrNull(b, "uin"),
                sectionRefs = FormCodec.list(b, "sectionRefs"),
                planRefs = FormCodec.list(b, "planRefs"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val section = TypedEntityDescriptor(
        id = "sections",
        singular = "Section",
        plural = "Sections",
        description = "Benefit sections (PBT section index) grouping covers. Day-1 / Day-2 classification.",
        isGroup = false,
        serializer = Section.serializer(),
        fields = listOf(
            FormField("sectionNumber", "Section number", FieldKind.TEXT, required = true, helper = "e.g. 1, 1.1, 9"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("subSection", "Sub-section", FieldKind.TEXT),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("day1", "Day-1 cover", FieldKind.BOOL),
            FormField("day2", "Day-2 cover", FieldKind.BOOL),
            FormField("coverRefs", "Covers", FieldKind.MULTI_SELECT, refEntityId = "covers"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "No." to { s: Section -> s.sectionNumber },
            "Name" to { s -> s.name },
            "Line" to { s -> s.productLine.name },
            "Covers" to { s -> s.coverRefs.size.toString() },
        ),
        factory = { Section(sectionNumber = "0", name = "New section") },
        reader = { s ->
            mapOf(
                "sectionNumber" to s.sectionNumber, "name" to s.name,
                "subSection" to (s.subSection ?: ""), "productLine" to s.productLine.name,
                "day1" to s.day1.toString(), "day2" to s.day2.toString(),
                "coverRefs" to FormCodec.fmtList(s.coverRefs), "displayOrder" to s.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                sectionNumber = FormCodec.str(b, "sectionNumber", base.sectionNumber),
                name = FormCodec.str(b, "name", base.name),
                subSection = FormCodec.strOrNull(b, "subSection"),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                day1 = FormCodec.bool(b, "day1", base.day1),
                day2 = FormCodec.bool(b, "day2", base.day2),
                coverRefs = FormCodec.list(b, "coverRefs"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val cover = TypedEntityDescriptor(
        id = "covers",
        singular = "Cover",
        plural = "Covers & Benefits",
        description = "The atomic rate-row / benefit. Set the rate dispatch (rateKind), accumulation base, SI limits and copy.",
        isGroup = false,
        serializer = Cover.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable engine id, e.g. personal_accident"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("sectionRef", "Section", FieldKind.ENTITY_PICKER, refEntityId = "sections"),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("rateKind", "Rate kind", FieldKind.ENUM, enumNames(RateKind.entries.toTypedArray())),
            FormField("payoutType", "Payout type", FieldKind.ENUM, enumNames(PayoutType.entries.toTypedArray())),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("trigger", "Trigger", FieldKind.TEXT),
            FormField("coverageText", "Coverage text", FieldKind.MULTILINE),
            FormField("minSumInsured", "Min SI", FieldKind.MONEY),
            FormField("maxSumInsured", "Max SI", FieldKind.MONEY),
            FormField("percentOfSI", "Percentage of SI", FieldKind.NUMBER, helper = "PBT PA col — e.g. 0.5 for 0.5% of SI; 0 if not %-driven"),
            FormField("accumBase", "Accumulation base (covers)", FieldKind.MULTI_SELECT, refEntityId = "covers", helper = "Ordered covers summed as the multiplication base"),
            FormField("annexureRefs", "Annexures", FieldKind.MULTI_SELECT, refEntityId = "annexures"),
            FormField("criticalIllnessListRef", "CI list", FieldKind.ENTITY_PICKER, refEntityId = "ci-lists"),
            FormField("survivalPeriod", "Survival period options", FieldKind.LIST, helper = "e.g. 7 days, 15 days, 30 days, Waived"),
            FormField("deductibleOptions", "Deductible options (days)", FieldKind.LIST, helper = "Hospital-Cash deductibles, e.g. 0, 1, 2, 3, 5"),
            FormField("maxPayableDuration", "Max payable duration", FieldKind.LIST, helper = "e.g. 5, 10, 30, 60, 90, 180 days"),
            FormField("isDiscount", "Is discount", FieldKind.BOOL),
            FormField("importAliases", "Import aliases", FieldKind.LIST),
            FormField("changeNote", "Change note", FieldKind.MULTILINE, helper = "Change-from-last-version audit note"),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
            FormField("options", "Options", FieldKind.TABLE, rowSpec = coverOptionRow, helper = "Selectable parameter options (param1)"),
            FormField("optionsParam2", "Param-2 options", FieldKind.TABLE, rowSpec = coverOptionRow, helper = "Second-parameter options (e.g. maternity waiting-period)"),
            FormField("optionGroups", "Option groups", FieldKind.TABLE, rowSpec = coverOptionGroupRow, helper = "Categorised option sets (waiting-period categories, room-rent tiers). Per-option rows edited via the flat options editors above."),
            FormField("sublimits", "Sub-limits", FieldKind.TABLE, rowSpec = sublimitRow, helper = "Named sub-limits (EMI months, room-rent cap, modern-treatment cap)"),
            FormField("initialWaitingOptions", "Initial waiting options", FieldKind.LIST, helper = "e.g. 30 days, Nil"),
            FormField("specificWaitingOptions", "Specific-illness waiting options", FieldKind.LIST, helper = "e.g. 1 year, 2 years"),
            FormField("pedWaitingOptions", "PED waiting options", FieldKind.LIST, helper = "e.g. 2 years, 3 years, 4 years"),
            FormField("maternityWaitingOptions", "Maternity waiting options", FieldKind.LIST, helper = "e.g. 9 months, 2 years"),
            FormField("dailyHospitalizationLimitOptions", "Daily hospitalization-cash limits", FieldKind.LIST, helper = "e.g. ₹1000, ₹2000"),
            FormField("icuMultiplierOptions", "ICU multiplier options", FieldKind.LIST, helper = "ICU daily-cash multipliers, e.g. 1x, 2x"),
            FormField("franchiseOptions", "Franchise (waiting-day) options", FieldKind.LIST, helper = "Days before a daily-cash benefit triggers"),
            FormField("payoutLinkageBases", "Payout linkage bases", FieldKind.LIST, helper = "per day / per claim / per policy / per member"),
            FormField("emiPaymentOptions", "EMI payment options", FieldKind.LIST, helper = "e.g. 6 months, 12 months"),
            FormField("coveredMemberCategories", "Covered member categories", FieldKind.LIST, helper = "Employee / Spouse / Child / Parent"),
            FormField("ppdPtdTableRef", "PPD/PTD table", FieldKind.ENTITY_PICKER, refEntityId = "ppd-ptd", helper = "Disablement payout table when applicable"),
        ),
        columns = listOf(
            "Code" to { c: Cover -> c.code },
            "Name" to { c -> c.name },
            "Section" to { c -> c.sectionRef.ifBlank { "—" } },
            "Rate kind" to { c -> c.rateKind.name },
            "% of SI" to { c -> if (c.percentOfSI != 0.0) c.percentOfSI.toString() else "—" },
            "Discount" to { c -> if (c.isDiscount) "yes" else "no" },
            "Options" to { c -> c.options.size.toString() },
        ),
        factory = { Cover(code = "new_cover", name = "New cover") },
        reader = { c ->
            mapOf(
                "code" to c.code, "name" to c.name, "sectionRef" to c.sectionRef,
                "productLine" to c.productLine.name, "rateKind" to c.rateKind.name,
                "payoutType" to c.payoutType.name, "description" to c.description,
                "trigger" to c.trigger, "coverageText" to c.coverageText,
                "minSumInsured" to FormCodec.fmtMoney(c.minSumInsured),
                "maxSumInsured" to FormCodec.fmtMoney(c.maxSumInsured),
                "percentOfSI" to c.percentOfSI.toString(),
                "accumBase" to FormCodec.fmtList(c.accumBase),
                "annexureRefs" to FormCodec.fmtList(c.annexureRefs),
                "criticalIllnessListRef" to (c.criticalIllnessListRef ?: ""),
                "survivalPeriod" to FormCodec.fmtList(c.survivalPeriod),
                "deductibleOptions" to FormCodec.fmtList(c.deductibleOptions),
                "maxPayableDuration" to FormCodec.fmtList(c.maxPayableDuration),
                "isDiscount" to c.isDiscount.toString(),
                "importAliases" to FormCodec.fmtList(c.importAliases),
                "changeNote" to c.changeNote,
                "displayOrder" to c.displayOrder.toString(),
                "options" to FormCodec.encodeList(CoverOption.serializer(), c.options),
                "optionsParam2" to FormCodec.encodeList(CoverOption.serializer(), c.optionsParam2),
                "optionGroups" to FormCodec.encodeList(CoverOptionGroup.serializer(), c.optionGroups),
                "sublimits" to FormCodec.encodeList(Sublimit.serializer(), c.sublimits),
                "initialWaitingOptions" to FormCodec.fmtList(c.initialWaitingOptions),
                "specificWaitingOptions" to FormCodec.fmtList(c.specificWaitingOptions),
                "pedWaitingOptions" to FormCodec.fmtList(c.pedWaitingOptions),
                "maternityWaitingOptions" to FormCodec.fmtList(c.maternityWaitingOptions),
                "dailyHospitalizationLimitOptions" to FormCodec.fmtList(c.dailyHospitalizationLimitOptions),
                "icuMultiplierOptions" to FormCodec.fmtList(c.icuMultiplierOptions),
                "franchiseOptions" to FormCodec.fmtList(c.franchiseOptions),
                "payoutLinkageBases" to FormCodec.fmtList(c.payoutLinkageBases),
                "emiPaymentOptions" to FormCodec.fmtList(c.emiPaymentOptions),
                "coveredMemberCategories" to FormCodec.fmtList(c.coveredMemberCategories),
                "ppdPtdTableRef" to (c.ppdPtdTableRef ?: ""),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                sectionRef = FormCodec.str(b, "sectionRef", base.sectionRef),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                rateKind = FormCodec.enum(b, "rateKind", base.rateKind),
                payoutType = FormCodec.enum(b, "payoutType", base.payoutType),
                description = FormCodec.str(b, "description", base.description),
                trigger = FormCodec.str(b, "trigger", base.trigger),
                coverageText = FormCodec.str(b, "coverageText", base.coverageText),
                minSumInsured = FormCodec.money(b, "minSumInsured", base.minSumInsured),
                maxSumInsured = FormCodec.money(b, "maxSumInsured", base.maxSumInsured),
                percentOfSI = FormCodec.double(b, "percentOfSI", base.percentOfSI),
                accumBase = FormCodec.list(b, "accumBase"),
                annexureRefs = FormCodec.list(b, "annexureRefs"),
                criticalIllnessListRef = FormCodec.strOrNull(b, "criticalIllnessListRef"),
                survivalPeriod = FormCodec.list(b, "survivalPeriod"),
                deductibleOptions = FormCodec.list(b, "deductibleOptions"),
                maxPayableDuration = FormCodec.list(b, "maxPayableDuration"),
                isDiscount = FormCodec.bool(b, "isDiscount", base.isDiscount),
                importAliases = FormCodec.list(b, "importAliases"),
                changeNote = FormCodec.str(b, "changeNote", base.changeNote),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
                options = FormCodec.decodeList(CoverOption.serializer(), b, "options"),
                optionsParam2 = FormCodec.decodeList(CoverOption.serializer(), b, "optionsParam2"),
                optionGroups = FormCodec.decodeList(CoverOptionGroup.serializer(), b, "optionGroups"),
                sublimits = FormCodec.decodeList(Sublimit.serializer(), b, "sublimits"),
                initialWaitingOptions = FormCodec.list(b, "initialWaitingOptions"),
                specificWaitingOptions = FormCodec.list(b, "specificWaitingOptions"),
                pedWaitingOptions = FormCodec.list(b, "pedWaitingOptions"),
                maternityWaitingOptions = FormCodec.list(b, "maternityWaitingOptions"),
                dailyHospitalizationLimitOptions = FormCodec.list(b, "dailyHospitalizationLimitOptions"),
                icuMultiplierOptions = FormCodec.list(b, "icuMultiplierOptions"),
                franchiseOptions = FormCodec.list(b, "franchiseOptions"),
                payoutLinkageBases = FormCodec.list(b, "payoutLinkageBases"),
                emiPaymentOptions = FormCodec.list(b, "emiPaymentOptions"),
                coveredMemberCategories = FormCodec.list(b, "coveredMemberCategories"),
                ppdPtdTableRef = FormCodec.strOrNull(b, "ppdPtdTableRef"),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val criticalIllnessList = TypedEntityDescriptor(
        id = "ci-lists",
        singular = "CI List",
        plural = "Critical-Illness Lists",
        description = "Tiered CI lists (1/4/16/32/48/92/101) referenced by CI covers.",
        isGroup = false,
        serializer = CriticalIllnessList.serializer(),
        fields = listOf(
            FormField("listCode", "List code", FieldKind.TEXT, required = true, helper = "e.g. CI_101, CI_92"),
            FormField("name", "Name", FieldKind.TEXT),
            FormField("size", "Size", FieldKind.NUMBER, helper = "Number of CIs (denormalised)"),
            FormField("isWomenSpecific", "Women-specific", FieldKind.BOOL, helper = "True for women-only CI lists"),
            FormField("items", "Items", FieldKind.TABLE, rowSpec = ciItemRow, helper = "The critical illnesses in this list"),
            FormField("tieredPlanRefs", "Tiered plan refs", FieldKind.KEY_VALUE, rowSpec = tieredPlanRefRow, helper = "Plan/tier label → plan ids offering this CI count"),
        ),
        columns = listOf(
            "Code" to { c: CriticalIllnessList -> c.listCode },
            "Name" to { c -> c.name },
            "Size" to { c -> (if (c.size > 0) c.size else c.items.size).toString() },
        ),
        factory = { CriticalIllnessList(listCode = "CI_NEW") },
        reader = { c ->
            mapOf(
                "listCode" to c.listCode, "name" to c.name,
                "size" to c.size.toString(),
                "isWomenSpecific" to c.isWomenSpecific.toString(),
                "items" to FormCodec.encodeList(CIItem.serializer(), c.items),
                "tieredPlanRefs" to FormCodec.encodeStringListMap(c.tieredPlanRefs),
            )
        },
        editor = { base, b ->
            base.copy(
                listCode = FormCodec.str(b, "listCode", base.listCode),
                name = FormCodec.str(b, "name", base.name),
                size = FormCodec.int(b, "size", base.size),
                isWomenSpecific = FormCodec.bool(b, "isWomenSpecific", base.isWomenSpecific),
                items = FormCodec.decodeList(CIItem.serializer(), b, "items"),
                tieredPlanRefs = FormCodec.decodeStringListMap(b, "tieredPlanRefs"),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "listCode").isBlank()) add("List code is required") } },
    )

    val annexure = TypedEntityDescriptor(
        id = "annexures",
        singular = "Annexure",
        plural = "Annexures",
        description = "Free-text list annexures (day-care, OPD minor procedures, consumables, exclusions).",
        isGroup = false,
        serializer = Annexure.serializer(),
        fields = listOf(
            FormField("annexureCode", "Code", FieldKind.TEXT, required = true),
            FormField("title", "Title", FieldKind.TEXT, required = true),
            FormField("category", "Category", FieldKind.ENUM, enumNames(AnnexureCategory.entries.toTypedArray())),
            FormField("items", "Items (comma/newline-sep)", FieldKind.MULTILINE),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Code" to { a: Annexure -> a.annexureCode },
            "Title" to { a -> a.title },
            "Category" to { a -> a.category.name },
            "Items" to { a -> a.items.size.toString() },
        ),
        factory = { Annexure(annexureCode = "ANX_NEW", title = "New annexure") },
        reader = { a ->
            mapOf(
                "annexureCode" to a.annexureCode, "title" to a.title,
                "category" to a.category.name, "items" to a.items.joinToString("\n"),
                "displayOrder" to a.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                annexureCode = FormCodec.str(b, "annexureCode", base.annexureCode),
                title = FormCodec.str(b, "title", base.title),
                category = FormCodec.enum(b, "category", base.category),
                items = FormCodec.list(b, "items"),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "annexureCode").isBlank()) add("Code is required")
                if (FormCodec.str(b, "title").isBlank()) add("Title is required")
            }
        },
    )

    val tenure = TypedEntityDescriptor(
        id = "tenures",
        singular = "Tenure",
        plural = "Tenures",
        description = "Available policy terms + multi-year single-premium discount (admin-tunable).",
        isGroup = false,
        serializer = Tenure.serializer(),
        fields = listOf(
            FormField("years", "Years", FieldKind.NUMBER, required = true),
            FormField("label", "Label", FieldKind.TEXT),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("multiTenureDiscount", "Multi-year discount", FieldKind.NUMBER, helper = "Fraction, e.g. 0.075"),
            FormField("discountFactor", "Discount factor", FieldKind.NUMBER, helper = "Premium multiplier, e.g. 0.925 (= 1 − discount)"),
            FormField("isActive", "Active", FieldKind.BOOL),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
        ),
        columns = listOf(
            "Years" to { t: Tenure -> t.years.toString() },
            "Label" to { t -> t.label },
            "Discount" to { t -> "${(t.multiTenureDiscount * 100).toInt()}%" },
            "Active" to { t -> if (t.isActive) "yes" else "no" },
        ),
        factory = { Tenure(years = 1) },
        reader = { t ->
            mapOf(
                "years" to t.years.toString(), "label" to t.label,
                "productLine" to t.productLine.name,
                "multiTenureDiscount" to t.multiTenureDiscount.toString(),
                "discountFactor" to t.discountFactor.toString(),
                "isActive" to t.isActive.toString(), "displayOrder" to t.displayOrder.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                years = FormCodec.int(b, "years", base.years),
                label = FormCodec.str(b, "label", base.label),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                multiTenureDiscount = FormCodec.double(b, "multiTenureDiscount", base.multiTenureDiscount),
                discountFactor = FormCodec.double(b, "discountFactor", base.discountFactor),
                isActive = FormCodec.bool(b, "isActive", base.isActive),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
            )
        },
        validator = { b -> buildList { if (FormCodec.int(b, "years") <= 0) add("Years must be > 0") } },
    )

    val pincodeZone = TypedEntityDescriptor(
        id = "pincode-zones",
        singular = "Pincode Zone",
        plural = "Pincode Zones",
        description = "Pincode-prefix → rating zone map (longest-prefix wins). Editable without code change.",
        isGroup = false,
        serializer = PincodeZone.serializer(),
        fields = listOf(
            FormField("prefix", "Prefix", FieldKind.TEXT, required = true, helper = "Leading pincode digits, e.g. 110 or 40"),
            FormField("prefixLength", "Prefix length", FieldKind.NUMBER, helper = "3 or 2"),
            FormField("zone", "Zone", FieldKind.ENUM, enumNames(Zone.entries.toTypedArray())),
            FormField("cityHint", "City hint", FieldKind.TEXT),
        ),
        columns = listOf(
            "Prefix" to { z: PincodeZone -> z.prefix },
            "Zone" to { z -> z.zone.label },
            "City" to { z -> z.cityHint ?: "—" },
        ),
        factory = { PincodeZone(prefix = "000", zone = Zone.ZONE_1) },
        reader = { z ->
            mapOf(
                "prefix" to z.prefix, "prefixLength" to z.prefixLength.toString(),
                "zone" to z.zone.name, "cityHint" to (z.cityHint ?: ""),
            )
        },
        editor = { base, b ->
            val prefix = FormCodec.str(b, "prefix", base.prefix)
            base.copy(
                prefix = prefix,
                prefixLength = FormCodec.int(b, "prefixLength", prefix.length),
                zone = FormCodec.enum(b, "zone", base.zone),
                cityHint = FormCodec.strOrNull(b, "cityHint"),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "prefix").isBlank()) add("Prefix is required") } },
    )

    val addOn = TypedEntityDescriptor(
        id = "addons",
        singular = "Add-on",
        plural = "Add-on Bundles",
        description = "Curated buy-online add-on bundles (tiers). Map to a plan and toggle pre-selection.",
        isGroup = false,
        serializer = AddOn.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "e.g. premier, signature, global"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(ProductLine.entries.toTypedArray())),
            FormField("planRef", "Plan", FieldKind.ENTITY_PICKER, refEntityId = "plans", helper = "Actuarial Plan this bundle rates against"),
            FormField("preSelected", "Pre-selected", FieldKind.BOOL),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
            FormField("items", "Bundle items", FieldKind.TABLE, rowSpec = addOnItemRow, helper = "Covers in this bundle with their pre-filled engine params"),
        ),
        columns = listOf(
            "Code" to { a: AddOn -> a.code },
            "Name" to { a -> a.name },
            "Plan" to { a -> a.planRef.ifBlank { "—" } },
            "Pre-sel" to { a -> if (a.preSelected) "yes" else "no" },
            "Items" to { a -> a.items.size.toString() },
        ),
        factory = { AddOn(code = "new_bundle", name = "New bundle") },
        reader = { a ->
            mapOf(
                "code" to a.code, "name" to a.name, "description" to a.description,
                "productLine" to a.productLine.name, "planRef" to a.planRef,
                "preSelected" to a.preSelected.toString(), "displayOrder" to a.displayOrder.toString(),
                "items" to FormCodec.encodeList(AddOnItem.serializer(), a.items),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                description = FormCodec.str(b, "description", base.description),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                planRef = FormCodec.str(b, "planRef", base.planRef),
                preSelected = FormCodec.bool(b, "preSelected", base.preSelected),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
                items = FormCodec.decodeList(AddOnItem.serializer(), b, "items"),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val vendor = TypedEntityDescriptor(
        id = "vendors",
        singular = "Vendor",
        plural = "Vendors",
        description = "Registered suppliers/vendors (New Vendor KYC). Global — shared across RETAIL and GROUP.",
        isGroup = false,
        serializer = Vendor.serializer(),
        fields = listOf(
            FormField("vendorName", "Vendor name", FieldKind.TEXT, required = true),
            FormField("category", "Category", FieldKind.ENUM, enumNames(VendorCategory.entries.toTypedArray())),
            FormField("officeAddress", "Office address", FieldKind.MULTILINE),
            FormField("contactPerson", "Contact person", FieldKind.TEXT),
            FormField("contactPhoneOffice", "Office phone", FieldKind.TEXT),
            FormField("contactPhoneMobile", "Mobile phone", FieldKind.TEXT),
            FormField("email", "Email", FieldKind.TEXT),
            FormField("website", "Website", FieldKind.TEXT),
            FormField("panNumber", "PAN number", FieldKind.TEXT),
            FormField("gstNumber", "GST number", FieldKind.TEXT),
            FormField("msmeNumber", "MSME number", FieldKind.TEXT, helper = "Only when MSME-registered"),
            FormField("bank.accountNo", "Bank account no", FieldKind.TEXT),
            FormField("bank.bankName", "Bank name", FieldKind.TEXT),
            FormField("bank.bankAddress", "Bank address", FieldKind.MULTILINE),
            FormField("bank.ifscCode", "IFSC code", FieldKind.TEXT),
            FormField("requiredDocuments", "Required documents", FieldKind.LIST, helper = "Any of: ${enumNames(VendorDocType.entries.toTypedArray()).joinToString(", ")}"),
        ),
        columns = listOf(
            "Name" to { v: Vendor -> v.vendorName },
            "Category" to { v -> v.category.name },
            "PAN" to { v -> v.panNumber.ifBlank { "—" } },
            "GST" to { v -> v.gstNumber.ifBlank { "—" } },
            "Docs" to { v -> v.requiredDocuments.size.toString() },
        ),
        factory = { Vendor(vendorName = "New vendor") },
        reader = { v ->
            mapOf(
                "vendorName" to v.vendorName,
                "category" to v.category.name,
                "officeAddress" to v.officeAddress,
                "contactPerson" to v.contactPerson,
                "contactPhoneOffice" to v.contactPhoneOffice,
                "contactPhoneMobile" to v.contactPhoneMobile,
                "email" to v.email,
                "website" to v.website,
                "panNumber" to v.panNumber,
                "gstNumber" to v.gstNumber,
                "msmeNumber" to (v.msmeNumber ?: ""),
                "bank.accountNo" to v.bank.accountNo,
                "bank.bankName" to v.bank.bankName,
                "bank.bankAddress" to v.bank.bankAddress,
                "bank.ifscCode" to v.bank.ifscCode,
                "requiredDocuments" to FormCodec.fmtList(v.requiredDocuments.map { it.name }),
            )
        },
        editor = { base, b ->
            base.copy(
                vendorName = FormCodec.str(b, "vendorName", base.vendorName),
                category = FormCodec.enum(b, "category", base.category),
                officeAddress = FormCodec.str(b, "officeAddress", base.officeAddress),
                contactPerson = FormCodec.str(b, "contactPerson", base.contactPerson),
                contactPhoneOffice = FormCodec.str(b, "contactPhoneOffice", base.contactPhoneOffice),
                contactPhoneMobile = FormCodec.str(b, "contactPhoneMobile", base.contactPhoneMobile),
                email = FormCodec.str(b, "email", base.email),
                website = FormCodec.str(b, "website", base.website),
                panNumber = FormCodec.str(b, "panNumber", base.panNumber),
                gstNumber = FormCodec.str(b, "gstNumber", base.gstNumber),
                msmeNumber = FormCodec.strOrNull(b, "msmeNumber"),
                bank = VendorBank(
                    accountNo = FormCodec.str(b, "bank.accountNo", base.bank.accountNo),
                    bankName = FormCodec.str(b, "bank.bankName", base.bank.bankName),
                    bankAddress = FormCodec.str(b, "bank.bankAddress", base.bank.bankAddress),
                    ifscCode = FormCodec.str(b, "bank.ifscCode", base.bank.ifscCode),
                ),
                requiredDocuments = FormCodec.list(b, "requiredDocuments").mapNotNull { name ->
                    VendorDocType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                },
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "vendorName").isBlank()) add("Vendor name is required") } },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(
        plan, product, section, cover, criticalIllnessList, annexure, tenure, pincodeZone, addOn, vendor,
    )
}
