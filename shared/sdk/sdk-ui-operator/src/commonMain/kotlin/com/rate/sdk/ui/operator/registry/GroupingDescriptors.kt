package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.grouping.FamilyType
import com.rate.sdk.catalog.model.grouping.GroupSize
import com.rate.sdk.catalog.model.grouping.GroupType
import com.rate.sdk.catalog.model.grouping.IndustryRiskCategory
import com.rate.sdk.catalog.model.grouping.IndustryType
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the GROUPING demographics masters surfaced in the operator console
 * (DEMOGRAPHICS hub):
 *  - group-types    — the kind of group a policy is issued to (min lives, formal relationship).
 *  - group-sizes    — size bands of lives with the size-based premium loading factor.
 *  - family-types   — adults+children family definitions and floater entitlement.
 *  - industry-types — NIC-like industry/occupation class, risk tier and base industry loading.
 *
 * Each rides the same generic ConfigEntityScreen as every other admin entity. Descriptor ids equal
 * the route path segment (e.g. "group-types" -> /api/admin/group-types).
 */
internal object GroupingDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val groupType = TypedEntityDescriptor(
        id = "group-types",
        singular = "Group Type",
        plural = "Group Types",
        description = "The kind of group a policy is issued to — drives minimum lives and eligibility.",
        isGroup = true,
        serializer = GroupType.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable machine code, e.g. EE, AFFINITY"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("minLives", "Min lives", FieldKind.NUMBER, helper = "IRDAI-style minimum lives to constitute the group"),
            FormField("formal", "Formal relationship", FieldKind.BOOL, helper = "Requires a formal employer-employee / homogeneous link"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { g: GroupType -> g.code },
            "Label" to { g -> g.label },
            "Min lives" to { g -> g.minLives.toString() },
            "Formal" to { g -> if (g.formal) "Yes" else "No" },
            "Active" to { g -> if (g.active) "Yes" else "No" },
        ),
        factory = { GroupType(code = "", label = "") },
        reader = { g ->
            mapOf(
                "code" to g.code,
                "label" to g.label,
                "description" to g.description,
                "minLives" to g.minLives.toString(),
                "formal" to g.formal.toString(),
                "sortOrder" to g.sortOrder.toString(),
                "active" to g.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                description = FormCodec.str(b, "description", base.description),
                minLives = FormCodec.int(b, "minLives", base.minLives),
                formal = FormCodec.bool(b, "formal", base.formal),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "minLives", -1) < 0) add("Min lives must be zero or more")
            }
        },
    )

    val groupSize = TypedEntityDescriptor(
        id = "group-sizes",
        singular = "Group Size",
        plural = "Group Sizes",
        description = "Size bands of lives with the size-based premium loading factor.",
        isGroup = true,
        serializer = GroupSize.serializer(),
        fields = listOf(
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("minLives", "Min lives", FieldKind.NUMBER),
            FormField("maxLives", "Max lives", FieldKind.NUMBER),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("loadingFactor", "Loading factor", FieldKind.NUMBER, helper = "Premium multiplier, e.g. 0.92 = 8% discount"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Label" to { g: GroupSize -> g.label },
            "Range" to { g -> "${g.minLives}-${g.maxLives}" },
            "Loading" to { g -> g.loadingFactor.toString() },
            "Active" to { g -> if (g.active) "Yes" else "No" },
        ),
        factory = { GroupSize(label = "") },
        reader = { g ->
            mapOf(
                "label" to g.label,
                "minLives" to g.minLives.toString(),
                "maxLives" to g.maxLives.toString(),
                "sortOrder" to g.sortOrder.toString(),
                "loadingFactor" to g.loadingFactor.toString(),
                "active" to g.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                label = FormCodec.str(b, "label", base.label),
                minLives = FormCodec.int(b, "minLives", base.minLives),
                maxLives = FormCodec.int(b, "maxLives", base.maxLives),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                loadingFactor = FormCodec.double(b, "loadingFactor", base.loadingFactor),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "maxLives", 0) < FormCodec.int(b, "minLives", 0)) add("Max lives must be at least Min lives")
            }
        },
    )

    val familyType = TypedEntityDescriptor(
        id = "family-types",
        singular = "Family Type",
        plural = "Family Types",
        description = "Adults+children family definitions and floater entitlement for the premium basis.",
        isGroup = true,
        serializer = FamilyType.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Compact code, e.g. 2A+2C"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("adults", "Adults", FieldKind.NUMBER),
            FormField("children", "Children", FieldKind.NUMBER),
            FormField("definition", "Definition", FieldKind.MULTILINE),
            FormField("isFloater", "Floater", FieldKind.BOOL, helper = "Shared family floater sum insured"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { f: FamilyType -> f.code },
            "Label" to { f -> f.label },
            "Composition" to { f -> "${f.adults}A + ${f.children}C" },
            "Floater" to { f -> if (f.isFloater) "Yes" else "No" },
            "Active" to { f -> if (f.active) "Yes" else "No" },
        ),
        factory = { FamilyType(code = "", label = "") },
        reader = { f ->
            mapOf(
                "code" to f.code,
                "label" to f.label,
                "adults" to f.adults.toString(),
                "children" to f.children.toString(),
                "definition" to f.definition,
                "isFloater" to f.isFloater.toString(),
                "sortOrder" to f.sortOrder.toString(),
                "active" to f.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                adults = FormCodec.int(b, "adults", base.adults),
                children = FormCodec.int(b, "children", base.children),
                definition = FormCodec.str(b, "definition", base.definition),
                isFloater = FormCodec.bool(b, "isFloater", base.isFloater),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "adults", 0) < 1) add("At least one adult is required")
            }
        },
    )

    val industryType = TypedEntityDescriptor(
        id = "industry-types",
        singular = "Industry Type",
        plural = "Industry Types",
        description = "NIC-like industry/occupation class, risk tier and base industry loading.",
        isGroup = true,
        serializer = IndustryType.serializer(),
        fields = listOf(
            FormField("code", "NIC code", FieldKind.TEXT, required = true, helper = "NIC-like industry code"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("sector", "Sector", FieldKind.TEXT),
            FormField("riskCategory", "Risk category", FieldKind.ENUM, enumNames(IndustryRiskCategory.entries.toTypedArray())),
            FormField("baseLoadingFactor", "Base loading factor", FieldKind.NUMBER, helper = "Premium multiplier, e.g. 1.25 = 25% load"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { i: IndustryType -> i.code },
            "Name" to { i -> i.name },
            "Risk" to { i -> i.riskCategory.name },
            "Loading" to { i -> i.baseLoadingFactor.toString() },
            "Active" to { i -> if (i.active) "Yes" else "No" },
        ),
        factory = { IndustryType(code = "", name = "") },
        reader = { i ->
            mapOf(
                "code" to i.code,
                "name" to i.name,
                "sector" to i.sector,
                "riskCategory" to i.riskCategory.name,
                "baseLoadingFactor" to i.baseLoadingFactor.toString(),
                "sortOrder" to i.sortOrder.toString(),
                "active" to i.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                sector = FormCodec.str(b, "sector", base.sector),
                riskCategory = FormCodec.enum(b, "riskCategory", base.riskCategory),
                baseLoadingFactor = FormCodec.double(b, "baseLoadingFactor", base.baseLoadingFactor),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("NIC code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.double(b, "baseLoadingFactor", 1.0) <= 0.0) add("Base loading factor must be positive")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(groupType, groupSize, familyType, industryType)
}
