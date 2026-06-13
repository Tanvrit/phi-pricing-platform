package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.actuarial.AuthorityLevel
import com.rate.sdk.catalog.model.actuarial.ClaimThreshold
import com.rate.sdk.catalog.model.actuarial.ClaimThresholdType
import com.rate.sdk.catalog.model.actuarial.IBNRMethod
import com.rate.sdk.catalog.model.actuarial.IBNRReserve
import com.rate.sdk.catalog.model.actuarial.IndividualLoadingFactor
import com.rate.sdk.catalog.model.actuarial.InflationMTF
import com.rate.sdk.catalog.model.actuarial.MedicalTrendFactor
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the ACTUARIAL masters (Dorian slide 10) surfaced in the operator
 * console under the [HubGroup.ACTUARIAL] hub:
 *  - authority-levels — RBAC approval tiers (role, max amount, discount cap).
 *  - medical-trend-factors / inflation-factors — annual cost-projection assumptions.
 *  - claim-thresholds — monetary triggers for large/cat/reinsurance handling.
 *  - loading-factors — individual underwriting premium loadings.
 *  - ibnr-reserves — incurred-but-not-reported reserve estimates.
 *
 * All ride the same generic ConfigEntityScreen as every other admin entity; each descriptor's id
 * doubles as the admin REST resource path segment ("/api/admin/{id}").
 */
internal object ActuarialDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    private val ROLE_OPTIONS = listOf("CUSTOMER", "BUSINESS", "ADMIN", "OWNER")
    private val PRODUCT_LINE_OPTIONS = listOf("RETAIL", "GROUP")

    val authorityLevel = TypedEntityDescriptor(
        id = "authority-levels",
        singular = "Authority Level",
        plural = "Authority Levels",
        description = "RBAC approval tiers — the role, the largest amount and the discount % each may sign off.",
        isGroup = false,
        serializer = AuthorityLevel.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("roleName", "Role", FieldKind.ENUM, ROLE_OPTIONS, helper = "Coarse RBAC role this tier maps to"),
            FormField("maxApprovalAmount", "Max approval amount", FieldKind.MONEY),
            FormField("discountApprovalCapPct", "Discount cap %", FieldKind.NUMBER, helper = "0..100"),
            FormField("scope", "Scope", FieldKind.TEXT, helper = "e.g. Branch / Regional / National / Board"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { a: AuthorityLevel -> a.name },
            "Role" to { a -> a.roleName },
            "Max amount" to { a -> a.maxApprovalAmount.formatIndian(showDecimals = false) },
            "Discount cap" to { a -> "${a.discountApprovalCapPct}%" },
            "Active" to { a -> if (a.active) "Yes" else "No" },
        ),
        factory = { AuthorityLevel(name = "") },
        reader = { a ->
            mapOf(
                "name" to a.name,
                "roleName" to a.roleName,
                "maxApprovalAmount" to FormCodec.fmtMoney(a.maxApprovalAmount),
                "discountApprovalCapPct" to a.discountApprovalCapPct.toString(),
                "scope" to a.scope,
                "sortOrder" to a.sortOrder.toString(),
                "active" to a.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                roleName = FormCodec.str(b, "roleName", base.roleName),
                maxApprovalAmount = FormCodec.money(b, "maxApprovalAmount", base.maxApprovalAmount),
                discountApprovalCapPct = FormCodec.double(b, "discountApprovalCapPct", base.discountApprovalCapPct),
                scope = FormCodec.str(b, "scope", base.scope),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                val cap = FormCodec.double(b, "discountApprovalCapPct", -1.0)
                if (cap < 0.0 || cap > 100.0) add("Discount cap % must be between 0 and 100")
            }
        },
    )

    val medicalTrendFactor = TypedEntityDescriptor(
        id = "medical-trend-factors",
        singular = "Medical Trend Factor",
        plural = "Medical Trend Factors",
        description = "Annual medical-cost-inflation assumptions used to project claims for renewals and multi-year contracts.",
        isGroup = false,
        serializer = MedicalTrendFactor.serializer(),
        fields = listOf(
            FormField("yearLabel", "Year", FieldKind.TEXT, required = true, helper = "e.g. FY2024-25"),
            FormField("region", "Region", FieldKind.TEXT),
            FormField("trendPct", "Trend %", FieldKind.NUMBER, helper = "e.g. 13.0 = +13%"),
            FormField("category", "Category", FieldKind.TEXT, helper = "Overall / In-patient / Pharmacy / Diagnostics"),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Year" to { m: MedicalTrendFactor -> m.yearLabel },
            "Region" to { m -> m.region },
            "Trend" to { m -> "${m.trendPct}%" },
            "Category" to { m -> m.category },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { MedicalTrendFactor(yearLabel = "") },
        reader = { m ->
            mapOf(
                "yearLabel" to m.yearLabel,
                "region" to m.region,
                "trendPct" to m.trendPct.toString(),
                "category" to m.category,
                "effectiveFrom" to m.effectiveFrom,
                "effectiveTo" to m.effectiveTo,
                "active" to m.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                yearLabel = FormCodec.str(b, "yearLabel", base.yearLabel),
                region = FormCodec.str(b, "region", base.region),
                trendPct = FormCodec.double(b, "trendPct", base.trendPct),
                category = FormCodec.str(b, "category", base.category),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "yearLabel").isBlank()) add("Year is required") }
        },
    )

    val inflationFactor = TypedEntityDescriptor(
        id = "inflation-factors",
        singular = "Inflation Factor",
        plural = "Inflation Factors",
        description = "General (non-medical) inflation assumptions used to escalate fixed expenses and indexed benefits.",
        isGroup = false,
        serializer = InflationMTF.serializer(),
        fields = listOf(
            FormField("yearLabel", "Year", FieldKind.TEXT, required = true, helper = "e.g. FY2024-25"),
            FormField("inflationPct", "Inflation %", FieldKind.NUMBER, helper = "e.g. 4.8 = +4.8%"),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "CPI / WPI / Wage / GDP deflator"),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Year" to { i: InflationMTF -> i.yearLabel },
            "Inflation" to { i -> "${i.inflationPct}%" },
            "Basis" to { i -> i.basis },
            "Active" to { i -> if (i.active) "Yes" else "No" },
        ),
        factory = { InflationMTF(yearLabel = "") },
        reader = { i ->
            mapOf(
                "yearLabel" to i.yearLabel,
                "inflationPct" to i.inflationPct.toString(),
                "basis" to i.basis,
                "effectiveFrom" to i.effectiveFrom,
                "effectiveTo" to i.effectiveTo,
                "active" to i.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                yearLabel = FormCodec.str(b, "yearLabel", base.yearLabel),
                inflationPct = FormCodec.double(b, "inflationPct", base.inflationPct),
                basis = FormCodec.str(b, "basis", base.basis),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "yearLabel").isBlank()) add("Year is required") }
        },
    )

    val claimThreshold = TypedEntityDescriptor(
        id = "claim-thresholds",
        singular = "Claim Threshold",
        plural = "Claim Thresholds",
        description = "Monetary triggers that, when a claim crosses them, drive large-claim / catastrophe / reinsurance handling.",
        isGroup = false,
        serializer = ClaimThreshold.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("thresholdAmount", "Threshold amount", FieldKind.MONEY),
            FormField("type", "Type", FieldKind.ENUM, enumNames(ClaimThresholdType.entries.toTypedArray())),
            FormField("action", "Action", FieldKind.TEXT, helper = "What happens when crossed"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: ClaimThreshold -> c.name },
            "Type" to { c -> c.type.name },
            "Amount" to { c -> c.thresholdAmount.formatIndian(showDecimals = false) },
            "Action" to { c -> c.action.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { ClaimThreshold(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "thresholdAmount" to FormCodec.fmtMoney(c.thresholdAmount),
                "type" to c.type.name,
                "action" to c.action,
                "sortOrder" to c.sortOrder.toString(),
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                thresholdAmount = FormCodec.money(b, "thresholdAmount", base.thresholdAmount),
                type = FormCodec.enum(b, "type", base.type),
                action = FormCodec.str(b, "action", base.action),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val loadingFactor = TypedEntityDescriptor(
        id = "loading-factors",
        singular = "Loading Factor",
        plural = "Loading Factors",
        description = "Individual underwriting premium loadings for declared conditions / risks, scoped per product line.",
        isGroup = false,
        serializer = IndividualLoadingFactor.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("condition", "Condition", FieldKind.TEXT, helper = "Risk that triggers the loading"),
            FormField("loadingPct", "Loading %", FieldKind.NUMBER, helper = "e.g. 25.0 = +25%"),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "Base premium / Total premium / Per member"),
            FormField("productLine", "Product line", FieldKind.ENUM, PRODUCT_LINE_OPTIONS),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { l: IndividualLoadingFactor -> l.name },
            "Condition" to { l -> l.condition.ifBlank { "—" } },
            "Loading" to { l -> "${l.loadingPct}%" },
            "Line" to { l -> l.productLine },
            "Active" to { l -> if (l.active) "Yes" else "No" },
        ),
        factory = { IndividualLoadingFactor(name = "") },
        reader = { l ->
            mapOf(
                "name" to l.name,
                "condition" to l.condition,
                "loadingPct" to l.loadingPct.toString(),
                "basis" to l.basis,
                "productLine" to l.productLine,
                "active" to l.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                condition = FormCodec.str(b, "condition", base.condition),
                loadingPct = FormCodec.double(b, "loadingPct", base.loadingPct),
                basis = FormCodec.str(b, "basis", base.basis),
                productLine = FormCodec.str(b, "productLine", base.productLine),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val ibnrReserve = TypedEntityDescriptor(
        id = "ibnr-reserves",
        singular = "IBNR Reserve",
        plural = "IBNR Reserves",
        description = "Incurred-but-not-reported reserve estimates per valuation period, method and held amount.",
        isGroup = false,
        serializer = IBNRReserve.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("method", "Method", FieldKind.ENUM, enumNames(IBNRMethod.entries.toTypedArray())),
            FormField("reservePct", "Reserve %", FieldKind.NUMBER, helper = "% of earned premium"),
            FormField("period", "Period", FieldKind.TEXT, helper = "e.g. FY2024-25"),
            FormField("amount", "Amount", FieldKind.MONEY),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { r: IBNRReserve -> r.name },
            "Method" to { r -> r.method.name },
            "Reserve" to { r -> "${r.reservePct}%" },
            "Period" to { r -> r.period.ifBlank { "—" } },
            "Amount" to { r -> r.amount.formatIndian(showDecimals = false) },
            "Active" to { r -> if (r.active) "Yes" else "No" },
        ),
        factory = { IBNRReserve(name = "") },
        reader = { r ->
            mapOf(
                "name" to r.name,
                "method" to r.method.name,
                "reservePct" to r.reservePct.toString(),
                "period" to r.period,
                "amount" to FormCodec.fmtMoney(r.amount),
                "active" to r.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                method = FormCodec.enum(b, "method", base.method),
                reservePct = FormCodec.double(b, "reservePct", base.reservePct),
                period = FormCodec.str(b, "period", base.period),
                amount = FormCodec.money(b, "amount", base.amount),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(
        authorityLevel, medicalTrendFactor, inflationFactor, claimThreshold, loadingFactor, ibnrReserve,
    )
}
