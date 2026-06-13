package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.financialcapital.CostOfCapitalMatrix
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceTreaty
import com.rate.sdk.catalog.model.financialcapital.ReinsuranceType
import com.rate.sdk.catalog.model.financialcapital.RiskTier
import com.rate.sdk.catalog.model.financialcapital.SiProductLine
import com.rate.sdk.catalog.model.financialcapital.SumInsuredTier
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the FINANCIAL-CAPITAL domain (Dorian slides 09/10):
 *  - `sum-insured-tiers`   — SI bands offered per product line.
 *  - `reinsurance-treaties`— ceded-risk arrangements incl. the obligatory GIC Re cession.
 *  - `cost-of-capital`     — return-on-capital / capital-charge by risk tier.
 *
 * All ride the same generic ConfigEntityScreen. Descriptor ids equal the admin route segment.
 */
internal object FinancialCapitalDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val sumInsuredTier = TypedEntityDescriptor(
        id = "sum-insured-tiers",
        singular = "Sum Insured Tier",
        plural = "Sum Insured Tiers",
        description = "Sum-insured bands offered per product line — drive rating, RI retention and capital lookups.",
        isGroup = false,
        serializer = SumInsuredTier.serializer(),
        fields = listOf(
            FormField("label", "Label", FieldKind.TEXT, required = true, helper = "Display name, e.g. \"10 Lakh\""),
            FormField("minSi", "Min sum insured", FieldKind.MONEY),
            FormField("maxSi", "Max sum insured", FieldKind.MONEY),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(SiProductLine.entries.toTypedArray())),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Label" to { t: SumInsuredTier -> t.label },
            "Range" to { t -> "${FormCodec.fmtMoney(t.minSi)} - ${FormCodec.fmtMoney(t.maxSi)}" },
            "Line" to { t -> t.productLine.name },
            "Active" to { t -> if (t.active) "Yes" else "No" },
        ),
        factory = { SumInsuredTier(label = "") },
        reader = { t ->
            mapOf(
                "label" to t.label,
                "minSi" to FormCodec.fmtMoney(t.minSi),
                "maxSi" to FormCodec.fmtMoney(t.maxSi),
                "productLine" to t.productLine.name,
                "sortOrder" to t.sortOrder.toString(),
                "active" to t.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                label = FormCodec.str(b, "label", base.label),
                minSi = FormCodec.money(b, "minSi", base.minSi),
                maxSi = FormCodec.money(b, "maxSi", base.maxSi),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
            }
        },
    )

    val reinsuranceTreaty = TypedEntityDescriptor(
        id = "reinsurance-treaties",
        singular = "Reinsurance Treaty",
        plural = "Reinsurance Treaties",
        description = "Ceded-risk arrangements incl. the obligatory GIC Re cession — proportional and non-proportional.",
        isGroup = false,
        serializer = ReinsuranceTreaty.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("reinsurerRef", "Reinsurer", FieldKind.ENTITY_PICKER, refEntityId = "insurers"),
            FormField("type", "Type", FieldKind.ENUM, enumNames(ReinsuranceType.entries.toTypedArray())),
            FormField("cededPct", "Ceded %", FieldKind.NUMBER, helper = "Share of risk ceded (0-100)"),
            FormField("retentionLimit", "Retention limit", FieldKind.MONEY, helper = "Retained amount before the treaty engages"),
            FormField("commissionPct", "Commission %", FieldKind.NUMBER, helper = "Ceding commission (0-100)"),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { t: ReinsuranceTreaty -> t.name },
            "Type" to { t -> t.type.name },
            "Ceded %" to { t -> t.cededPct.toString() },
            "Retention" to { t -> FormCodec.fmtMoney(t.retentionLimit) },
            "Active" to { t -> if (t.active) "Yes" else "No" },
        ),
        factory = { ReinsuranceTreaty(name = "") },
        reader = { t ->
            mapOf(
                "name" to t.name,
                "reinsurerRef" to t.reinsurerRef,
                "type" to t.type.name,
                "cededPct" to t.cededPct.toString(),
                "retentionLimit" to FormCodec.fmtMoney(t.retentionLimit),
                "commissionPct" to t.commissionPct.toString(),
                "effectiveFrom" to t.effectiveFrom,
                "effectiveTo" to t.effectiveTo,
                "active" to t.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                reinsurerRef = FormCodec.str(b, "reinsurerRef", base.reinsurerRef),
                type = FormCodec.enum(b, "type", base.type),
                cededPct = FormCodec.double(b, "cededPct", base.cededPct),
                retentionLimit = FormCodec.money(b, "retentionLimit", base.retentionLimit),
                commissionPct = FormCodec.double(b, "commissionPct", base.commissionPct),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                val ceded = FormCodec.double(b, "cededPct", 0.0)
                if (ceded < 0.0 || ceded > 100.0) add("Ceded % must be between 0 and 100")
            }
        },
    )

    val costOfCapital = TypedEntityDescriptor(
        id = "cost-of-capital",
        singular = "Cost of Capital",
        plural = "Cost of Capital",
        description = "Return-on-capital and capital-charge percentages by risk tier — feed the pricing capital loading.",
        isGroup = false,
        serializer = CostOfCapitalMatrix.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("riskTier", "Risk tier", FieldKind.ENUM, enumNames(RiskTier.entries.toTypedArray())),
            FormField("cocRatePct", "Cost of capital %", FieldKind.NUMBER, helper = "Return demanded on held capital"),
            FormField("capitalChargePct", "Capital charge %", FieldKind.NUMBER, helper = "Solvency capital charge on exposure"),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { c: CostOfCapitalMatrix -> c.name },
            "Tier" to { c -> c.riskTier.name },
            "CoC %" to { c -> c.cocRatePct.toString() },
            "Charge %" to { c -> c.capitalChargePct.toString() },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { CostOfCapitalMatrix(name = "") },
        reader = { c ->
            mapOf(
                "name" to c.name,
                "riskTier" to c.riskTier.name,
                "cocRatePct" to c.cocRatePct.toString(),
                "capitalChargePct" to c.capitalChargePct.toString(),
                "effectiveFrom" to c.effectiveFrom,
                "effectiveTo" to c.effectiveTo,
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                riskTier = FormCodec.enum(b, "riskTier", base.riskTier),
                cocRatePct = FormCodec.double(b, "cocRatePct", base.cocRatePct),
                capitalChargePct = FormCodec.double(b, "capitalChargePct", base.capitalChargePct),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(sumInsuredTier, reinsuranceTreaty, costOfCapital)
}
