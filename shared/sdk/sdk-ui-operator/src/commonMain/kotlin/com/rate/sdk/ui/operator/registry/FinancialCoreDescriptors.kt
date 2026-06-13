package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.financialcore.DiscountConfig
import com.rate.sdk.catalog.model.financialcore.DiscountType
import com.rate.sdk.catalog.model.financialcore.InvestmentIncomeConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostConfig
import com.rate.sdk.catalog.model.financialcore.PolicyCostType
import com.rate.sdk.catalog.model.financialcore.VariableExpenseConfig
import com.rate.sdk.catalog.model.financialcore.VariableExpenseType
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the FINANCIAL-CORE masters (Dorian slide 09) surfaced under the
 * FINANCIAL hub:
 *  - discount-configs   — discount levers + the overall discount cap (effective-dated).
 *  - variable-expenses  — premium-proportional expense ratios (commission/admin/marketing/...).
 *  - policy-costs       — per-policy fixed/percentage costs (issuance, stamp duty, servicing, exam).
 *  - investment-income  — yield / discount-rate / duration assumptions for cash-flow discounting.
 *
 * These ride the same generic ConfigEntityScreen as every other config entity. Each descriptor id
 * equals the admin REST resource path segment, e.g. "discount-configs" -> /api/admin/discount-configs.
 */
internal object FinancialCoreDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    private val productLineOptions = listOf("RETAIL", "GROUP")

    val discountConfig = TypedEntityDescriptor(
        id = "discount-configs",
        singular = "Discount Config",
        plural = "Discount Configs",
        description = "Discount levers and the overall discount cap, effective-dated, per product line.",
        isGroup = false,
        serializer = DiscountConfig.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("type", "Type", FieldKind.ENUM, enumNames(DiscountType.entries.toTypedArray())),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "What the rate is applied to"),
            FormField("ratePct", "Rate %", FieldKind.NUMBER, helper = "Discount rate as a percentage"),
            FormField("maxCapPct", "Max cap %", FieldKind.NUMBER, helper = "Cap this lever contributes toward the ceiling"),
            FormField("productLine", "Product line", FieldKind.ENUM, productLineOptions),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD (blank = open-ended)"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: DiscountConfig -> c.name },
            "Type" to { c -> c.type.name },
            "Rate %" to { c -> c.ratePct.toString() },
            "Cap %" to { c -> c.maxCapPct.toString() },
            "Line" to { c -> c.productLine },
            "From" to { c -> c.effectiveFrom.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { DiscountConfig(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "type" to c.type.name,
                "basis" to c.basis,
                "ratePct" to c.ratePct.toString(),
                "maxCapPct" to c.maxCapPct.toString(),
                "productLine" to c.productLine,
                "effectiveFrom" to c.effectiveFrom,
                "effectiveTo" to c.effectiveTo,
                "notes" to c.notes,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                type = FormCodec.enum(b, "type", base.type),
                basis = FormCodec.str(b, "basis", base.basis),
                ratePct = FormCodec.double(b, "ratePct", base.ratePct),
                maxCapPct = FormCodec.double(b, "maxCapPct", base.maxCapPct),
                productLine = FormCodec.str(b, "productLine", base.productLine),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                notes = FormCodec.str(b, "notes", base.notes),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.double(b, "maxCapPct") < FormCodec.double(b, "ratePct")) {
                    add("Max cap % should be at least the rate %")
                }
            }
        },
    )

    val variableExpenseConfig = TypedEntityDescriptor(
        id = "variable-expenses",
        singular = "Variable Expense",
        plural = "Variable Expenses",
        description = "Premium-proportional expense ratios loaded into the pricing build, effective-dated.",
        isGroup = false,
        serializer = VariableExpenseConfig.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("expenseType", "Expense type", FieldKind.ENUM, enumNames(VariableExpenseType.entries.toTypedArray())),
            FormField("ratePct", "Rate %", FieldKind.NUMBER, helper = "Expense as a percentage of basis"),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "e.g. Gross written premium"),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD (blank = open-ended)"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: VariableExpenseConfig -> c.name },
            "Type" to { c -> c.expenseType.name },
            "Rate %" to { c -> c.ratePct.toString() },
            "Basis" to { c -> c.basis.ifBlank { "—" } },
            "From" to { c -> c.effectiveFrom.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { VariableExpenseConfig(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "expenseType" to c.expenseType.name,
                "ratePct" to c.ratePct.toString(),
                "basis" to c.basis,
                "effectiveFrom" to c.effectiveFrom,
                "effectiveTo" to c.effectiveTo,
                "notes" to c.notes,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                expenseType = FormCodec.enum(b, "expenseType", base.expenseType),
                ratePct = FormCodec.double(b, "ratePct", base.ratePct),
                basis = FormCodec.str(b, "basis", base.basis),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                notes = FormCodec.str(b, "notes", base.notes),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val policyCostConfig = TypedEntityDescriptor(
        id = "policy-costs",
        singular = "Policy Cost",
        plural = "Policy Costs",
        description = "Per-policy fixed and percentage costs — issuance, stamp duty, servicing, medical exam.",
        isGroup = false,
        serializer = PolicyCostConfig.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("costType", "Cost type", FieldKind.ENUM, enumNames(PolicyCostType.entries.toTypedArray())),
            FormField("amount", "Amount", FieldKind.MONEY, helper = "Flat rupee amount (0 if purely %-driven)"),
            FormField("ratePct", "Rate %", FieldKind.NUMBER, helper = "Percentage of basis (0 if purely flat)"),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "e.g. Per policy, Per member"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: PolicyCostConfig -> c.name },
            "Type" to { c -> c.costType.name },
            "Amount" to { c -> "₹" + FormCodec.fmtMoney(c.amount) },
            "Rate %" to { c -> if (c.ratePct != 0.0) c.ratePct.toString() else "—" },
            "Basis" to { c -> c.basis.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { PolicyCostConfig(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "costType" to c.costType.name,
                "amount" to FormCodec.fmtMoney(c.amount),
                "ratePct" to c.ratePct.toString(),
                "basis" to c.basis,
                "notes" to c.notes,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                costType = FormCodec.enum(b, "costType", base.costType),
                amount = FormCodec.money(b, "amount", base.amount),
                ratePct = FormCodec.double(b, "ratePct", base.ratePct),
                basis = FormCodec.str(b, "basis", base.basis),
                notes = FormCodec.str(b, "notes", base.notes),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val investmentIncomeConfig = TypedEntityDescriptor(
        id = "investment-income",
        singular = "Investment Income",
        plural = "Investment Income",
        description = "Yield, discount-rate and duration assumptions for cash-flow discounting, effective-dated.",
        isGroup = false,
        serializer = InvestmentIncomeConfig.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("expectedYieldPct", "Expected yield %", FieldKind.NUMBER),
            FormField("discountRatePct", "Discount rate %", FieldKind.NUMBER),
            FormField("durationYears", "Duration (years)", FieldKind.NUMBER),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD (blank = open-ended)"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: InvestmentIncomeConfig -> c.name },
            "Yield %" to { c -> c.expectedYieldPct.toString() },
            "Discount %" to { c -> c.discountRatePct.toString() },
            "Years" to { c -> c.durationYears.toString() },
            "From" to { c -> c.effectiveFrom.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { InvestmentIncomeConfig(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "expectedYieldPct" to c.expectedYieldPct.toString(),
                "discountRatePct" to c.discountRatePct.toString(),
                "durationYears" to c.durationYears.toString(),
                "effectiveFrom" to c.effectiveFrom,
                "effectiveTo" to c.effectiveTo,
                "notes" to c.notes,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                expectedYieldPct = FormCodec.double(b, "expectedYieldPct", base.expectedYieldPct),
                discountRatePct = FormCodec.double(b, "discountRatePct", base.discountRatePct),
                durationYears = FormCodec.int(b, "durationYears", base.durationYears),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                notes = FormCodec.str(b, "notes", base.notes),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.int(b, "durationYears", 1) < 1) add("Duration must be at least 1 year")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(
        discountConfig, variableExpenseConfig, policyCostConfig, investmentIncomeConfig,
    )
}
