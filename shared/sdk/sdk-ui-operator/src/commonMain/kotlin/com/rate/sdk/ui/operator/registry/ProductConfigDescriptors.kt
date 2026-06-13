package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.productconfig.AdaptiveCategory
import com.rate.sdk.catalog.model.productconfig.AdaptiveRuleType
import com.rate.sdk.catalog.model.productconfig.BenefitType
import com.rate.sdk.catalog.model.productconfig.BenefitValueType
import com.rate.sdk.catalog.model.productconfig.Category
import com.rate.sdk.catalog.model.productconfig.CategoryModel
import com.rate.sdk.catalog.model.productconfig.ProductAddonConfig
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the PRODUCT-CONFIG master entities surfaced in the operator console:
 *  - product-addon-configs — product x addon JOIN backing the addon matrix.
 *  - categories            — benefit-category taxonomy (self-nesting).
 *  - benefit-types         — the value semantics a benefit line carries.
 *  - category-models       — reusable benefit templates binding a category to benefit types.
 *  - adaptive-categories   — dynamic-pricing rule categories (Dorian slide 11).
 *
 * These ride the same generic ConfigEntityScreen as the other config entities; the entityId equals
 * the REST resource path segment the integrator wires (e.g. "categories" -> /api/admin/categories).
 */
internal object ProductConfigDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val productAddonConfig = TypedEntityDescriptor(
        id = "product-addon-configs",
        singular = "Product Addon Config",
        plural = "Product Addon Configs",
        description = "Product x addon matrix: which addons a product offers, defaults, mandatoriness and overrides.",
        isGroup = false,
        serializer = ProductAddonConfig.serializer(),
        fields = listOf(
            FormField("productRef", "Product", FieldKind.ENTITY_PICKER, refEntityId = "products", required = true),
            FormField("addonRef", "Addon", FieldKind.ENTITY_PICKER, refEntityId = "addons", required = true),
            FormField("included", "Included", FieldKind.BOOL, helper = "Whether this addon is offered on the product"),
            FormField("defaultSelected", "Default selected", FieldKind.BOOL, helper = "Pre-ticked in the journey"),
            FormField("mandatory", "Mandatory", FieldKind.BOOL, helper = "Forced on, cannot be removed"),
            FormField("priceOverride", "Price override", FieldKind.MONEY, helper = "0 = use the addon default price"),
            FormField("minSumInsured", "Min sum insured", FieldKind.MONEY),
            FormField("maxSumInsured", "Max sum insured", FieldKind.MONEY),
            FormField("displayOrder", "Display order", FieldKind.NUMBER),
            FormField("productLine", "Product line", FieldKind.ENUM, listOf("RETAIL", "GROUP")),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Product" to { c: ProductAddonConfig -> c.productRef.ifBlank { "—" } },
            "Addon" to { c -> c.addonRef.ifBlank { "—" } },
            "Default" to { c -> if (c.defaultSelected) "Yes" else "No" },
            "Mandatory" to { c -> if (c.mandatory) "Yes" else "No" },
            "Line" to { c -> c.productLine },
        ),
        factory = { ProductAddonConfig() },
        reader = { c ->
            mapOf(
                "productRef" to c.productRef,
                "addonRef" to c.addonRef,
                "included" to c.included.toString(),
                "defaultSelected" to c.defaultSelected.toString(),
                "mandatory" to c.mandatory.toString(),
                "priceOverride" to FormCodec.fmtMoney(c.priceOverride),
                "minSumInsured" to FormCodec.fmtMoney(c.minSumInsured),
                "maxSumInsured" to FormCodec.fmtMoney(c.maxSumInsured),
                "displayOrder" to c.displayOrder.toString(),
                "productLine" to c.productLine,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                productRef = FormCodec.str(b, "productRef", base.productRef),
                addonRef = FormCodec.str(b, "addonRef", base.addonRef),
                included = FormCodec.bool(b, "included", base.included),
                defaultSelected = FormCodec.bool(b, "defaultSelected", base.defaultSelected),
                mandatory = FormCodec.bool(b, "mandatory", base.mandatory),
                priceOverride = FormCodec.money(b, "priceOverride", base.priceOverride),
                minSumInsured = FormCodec.money(b, "minSumInsured", base.minSumInsured),
                maxSumInsured = FormCodec.money(b, "maxSumInsured", base.maxSumInsured),
                displayOrder = FormCodec.int(b, "displayOrder", base.displayOrder),
                productLine = FormCodec.str(b, "productLine", base.productLine),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "productRef").isBlank()) add("Product is required")
                if (FormCodec.str(b, "addonRef").isBlank()) add("Addon is required")
            }
        },
    )

    val category = TypedEntityDescriptor(
        id = "categories",
        singular = "Category",
        plural = "Categories",
        description = "Benefit-category taxonomy grouping covers into a navigable, self-nesting hierarchy.",
        isGroup = false,
        serializer = Category.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable token, e.g. HOSPITALIZATION"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("parentRef", "Parent category", FieldKind.ENTITY_PICKER, refEntityId = "categories", helper = "Leave blank for a top-level category"),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { c: Category -> c.code },
            "Name" to { c -> c.name },
            "Order" to { c -> c.sortOrder.toString() },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { Category() },
        reader = { c ->
            mapOf(
                "code" to c.code,
                "name" to c.name,
                "parentRef" to c.parentRef,
                "description" to c.description,
                "sortOrder" to c.sortOrder.toString(),
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                parentRef = FormCodec.str(b, "parentRef", base.parentRef),
                description = FormCodec.str(b, "description", base.description),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val benefitType = TypedEntityDescriptor(
        id = "benefit-types",
        singular = "Benefit Type",
        plural = "Benefit Types",
        description = "The value semantics a benefit line carries (amount, percent, days, count, boolean).",
        isGroup = false,
        serializer = BenefitType.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable token, e.g. ROOM_RENT"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("unit", "Unit", FieldKind.TEXT, helper = "Display unit, e.g. days, %, INR/day"),
            FormField("valueType", "Value type", FieldKind.ENUM, enumNames(BenefitValueType.entries.toTypedArray())),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { t: BenefitType -> t.code },
            "Name" to { t -> t.name },
            "Type" to { t -> t.valueType.name },
            "Unit" to { t -> t.unit.ifBlank { "—" } },
            "Active" to { t -> if (t.active) "Yes" else "No" },
        ),
        factory = { BenefitType() },
        reader = { t ->
            mapOf(
                "code" to t.code,
                "name" to t.name,
                "unit" to t.unit,
                "valueType" to t.valueType.name,
                "description" to t.description,
                "active" to t.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                unit = FormCodec.str(b, "unit", base.unit),
                valueType = FormCodec.enum(b, "valueType", base.valueType),
                description = FormCodec.str(b, "description", base.description),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val categoryModel = TypedEntityDescriptor(
        id = "category-models",
        singular = "Category Model",
        plural = "Category Models",
        description = "Reusable benefit templates binding a category to its benefit types and a default limit.",
        isGroup = false,
        serializer = CategoryModel.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("categoryRef", "Category", FieldKind.ENTITY_PICKER, refEntityId = "categories"),
            FormField("benefitTypeRefs", "Benefit types", FieldKind.MULTI_SELECT, refEntityId = "benefit-types"),
            FormField("defaultLimit", "Default limit", FieldKind.MONEY),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("productLine", "Product line", FieldKind.ENUM, listOf("RETAIL", "GROUP")),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { m: CategoryModel -> m.name },
            "Limit" to { m -> FormCodec.fmtMoney(m.defaultLimit) },
            "Types" to { m -> m.benefitTypeRefs.size.toString() },
            "Line" to { m -> m.productLine },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { CategoryModel() },
        reader = { m ->
            mapOf(
                "name" to m.name,
                "categoryRef" to m.categoryRef,
                "benefitTypeRefs" to FormCodec.fmtList(m.benefitTypeRefs),
                "defaultLimit" to FormCodec.fmtMoney(m.defaultLimit),
                "description" to m.description,
                "productLine" to m.productLine,
                "active" to m.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                categoryRef = FormCodec.str(b, "categoryRef", base.categoryRef),
                benefitTypeRefs = FormCodec.list(b, "benefitTypeRefs"),
                defaultLimit = FormCodec.money(b, "defaultLimit", base.defaultLimit),
                description = FormCodec.str(b, "description", base.description),
                productLine = FormCodec.str(b, "productLine", base.productLine),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val adaptiveCategory = TypedEntityDescriptor(
        id = "adaptive-categories",
        singular = "Adaptive Category",
        plural = "Adaptive Categories",
        description = "Dynamic-pricing rule categories that conditionally adjust a quote (Dorian slide 11).",
        isGroup = false,
        serializer = AdaptiveCategory.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("ruleType", "Rule type", FieldKind.ENUM, enumNames(AdaptiveRuleType.entries.toTypedArray())),
            FormField("conditionExpr", "Condition", FieldKind.MULTILINE, helper = "Predicate, e.g. groupSize >= 500"),
            FormField("adjustmentPct", "Adjustment %", FieldKind.NUMBER, helper = "Negative = discount, positive = loading"),
            FormField("priority", "Priority", FieldKind.NUMBER, helper = "Lower runs first when several rules match"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { a: AdaptiveCategory -> a.name },
            "Rule" to { a -> a.ruleType.name },
            "Adj %" to { a -> a.adjustmentPct.toString() },
            "Priority" to { a -> a.priority.toString() },
            "Active" to { a -> if (a.active) "Yes" else "No" },
        ),
        factory = { AdaptiveCategory() },
        reader = { a ->
            mapOf(
                "name" to a.name,
                "ruleType" to a.ruleType.name,
                "conditionExpr" to a.conditionExpr,
                "adjustmentPct" to a.adjustmentPct.toString(),
                "priority" to a.priority.toString(),
                "active" to a.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                ruleType = FormCodec.enum(b, "ruleType", base.ruleType),
                conditionExpr = FormCodec.str(b, "conditionExpr", base.conditionExpr),
                adjustmentPct = FormCodec.double(b, "adjustmentPct", base.adjustmentPct),
                priority = FormCodec.int(b, "priority", base.priority),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> =
        listOf(productAddonConfig, category, benefitType, categoryModel, adaptiveCategory)
}
