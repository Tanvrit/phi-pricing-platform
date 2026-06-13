package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.GroupSizeMatrix
import com.rate.sdk.catalog.model.rfq.GroupSizeProductLine
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.rfq.RfqStatus
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the RFQ (SALES) master entities surfaced in the operator console:
 *  - rfqs                  — top-of-funnel Requests For Quote and their pipeline status.
 *  - experience-ratings    — burning-cost / loss-experience attached to an RFQ.
 *  - group-size-matrices   — demography sizing factors by group-size band (Dorian slides 16-20).
 *
 * These ride the same generic ConfigEntityScreen as catalog entities; the entityId equals the REST
 * resource path segment the integrator wires (e.g. "rfqs" -> /api/config/rfqs). The SALES hub
 * grouping is wired by the integrator at registration time.
 */
internal object RfqDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val rfq = TypedEntityDescriptor(
        id = "rfqs",
        singular = "RFQ",
        plural = "RFQs",
        description = "Group-insurance Requests For Quote — the top-of-funnel sales pipeline.",
        isGroup = true,
        serializer = Rfq.serializer(),
        fields = listOf(
            FormField("rfqNumber", "RFQ number", FieldKind.TEXT, required = true, helper = "Reference number, e.g. RFQ-2026-0001"),
            FormField("clientName", "Client name", FieldKind.TEXT, required = true),
            FormField("groupTypeRef", "Group type", FieldKind.ENTITY_PICKER, refEntityId = "group-types", helper = "Kind of group being insured"),
            FormField("industryRef", "Industry", FieldKind.ENTITY_PICKER, refEntityId = "industry-types"),
            FormField("lives", "Lives", FieldKind.NUMBER, helper = "Headcount to be covered"),
            FormField("rfqStatus", "Status", FieldKind.ENUM, enumNames(RfqStatus.entries.toTypedArray()), helper = "Pipeline stage"),
            FormField("requestedSumInsured", "Requested sum insured", FieldKind.MONEY),
            FormField("effectiveDate", "Effective date", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("channelRef", "Channel", FieldKind.ENTITY_PICKER, refEntityId = "channels"),
            FormField("intermediaryRef", "Intermediary", FieldKind.ENTITY_PICKER, refEntityId = "intermediaries"),
            FormField("notes", "Notes", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "RFQ #" to { r: Rfq -> r.rfqNumber },
            "Client" to { r -> r.clientName },
            "Lives" to { r -> r.lives.toString() },
            "Status" to { r -> r.rfqStatus.name },
            "Requested SI" to { r -> FormCodec.fmtMoney(r.requestedSumInsured) },
            "Active" to { r -> if (r.active) "Yes" else "No" },
        ),
        factory = { Rfq(rfqNumber = "", clientName = "") },
        reader = { r ->
            mapOf(
                "rfqNumber" to r.rfqNumber,
                "clientName" to r.clientName,
                "groupTypeRef" to r.groupTypeRef,
                "industryRef" to r.industryRef,
                "lives" to r.lives.toString(),
                "rfqStatus" to r.rfqStatus.name,
                "requestedSumInsured" to FormCodec.fmtMoney(r.requestedSumInsured),
                "effectiveDate" to r.effectiveDate,
                "channelRef" to r.channelRef,
                "intermediaryRef" to r.intermediaryRef,
                "notes" to r.notes,
                "active" to r.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                rfqNumber = FormCodec.str(b, "rfqNumber", base.rfqNumber),
                clientName = FormCodec.str(b, "clientName", base.clientName),
                groupTypeRef = FormCodec.str(b, "groupTypeRef", base.groupTypeRef),
                industryRef = FormCodec.str(b, "industryRef", base.industryRef),
                lives = FormCodec.int(b, "lives", base.lives),
                rfqStatus = FormCodec.enum(b, "rfqStatus", base.rfqStatus),
                requestedSumInsured = FormCodec.money(b, "requestedSumInsured", base.requestedSumInsured),
                effectiveDate = FormCodec.str(b, "effectiveDate", base.effectiveDate),
                channelRef = FormCodec.str(b, "channelRef", base.channelRef),
                intermediaryRef = FormCodec.str(b, "intermediaryRef", base.intermediaryRef),
                notes = FormCodec.str(b, "notes", base.notes),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "rfqNumber").isBlank()) add("RFQ number is required")
                if (FormCodec.str(b, "clientName").isBlank()) add("Client name is required")
                if (FormCodec.int(b, "lives", 0) < 0) add("Lives cannot be negative")
            }
        },
    )

    val experienceRating = TypedEntityDescriptor(
        id = "experience-ratings",
        singular = "Experience Rating",
        plural = "Experience Ratings",
        description = "Loss-experience / burning-cost analysis attached to an RFQ.",
        isGroup = true,
        serializer = ExperienceRating.serializer(),
        fields = listOf(
            FormField("rfqRef", "RFQ", FieldKind.ENTITY_PICKER, refEntityId = "rfqs", helper = "The RFQ this experience belongs to"),
            FormField("periodLabel", "Period", FieldKind.TEXT, required = true, helper = "e.g. FY 2024-25"),
            FormField("premiumCollected", "Premium collected", FieldKind.MONEY),
            FormField("claimsPaid", "Claims paid", FieldKind.MONEY),
            FormField("claimsOutstanding", "Claims outstanding", FieldKind.MONEY),
            FormField("lossRatio", "Loss ratio", FieldKind.NUMBER, helper = "Fraction, e.g. 0.88 for 88%"),
            FormField("credibilityFactor", "Credibility factor", FieldKind.NUMBER, helper = "0..1 weight on own experience"),
            FormField("recommendedLoadingPct", "Recommended loading %", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Period" to { e: ExperienceRating -> e.periodLabel },
            "Premium" to { e -> FormCodec.fmtMoney(e.premiumCollected) },
            "Claims paid" to { e -> FormCodec.fmtMoney(e.claimsPaid) },
            "Loss ratio" to { e -> e.lossRatio.toString() },
            "Loading %" to { e -> e.recommendedLoadingPct.toString() },
            "Active" to { e -> if (e.active) "Yes" else "No" },
        ),
        factory = { ExperienceRating(periodLabel = "") },
        reader = { e ->
            mapOf(
                "rfqRef" to e.rfqRef,
                "periodLabel" to e.periodLabel,
                "premiumCollected" to FormCodec.fmtMoney(e.premiumCollected),
                "claimsPaid" to FormCodec.fmtMoney(e.claimsPaid),
                "claimsOutstanding" to FormCodec.fmtMoney(e.claimsOutstanding),
                "lossRatio" to e.lossRatio.toString(),
                "credibilityFactor" to e.credibilityFactor.toString(),
                "recommendedLoadingPct" to e.recommendedLoadingPct.toString(),
                "active" to e.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                rfqRef = FormCodec.str(b, "rfqRef", base.rfqRef),
                periodLabel = FormCodec.str(b, "periodLabel", base.periodLabel),
                premiumCollected = FormCodec.money(b, "premiumCollected", base.premiumCollected),
                claimsPaid = FormCodec.money(b, "claimsPaid", base.claimsPaid),
                claimsOutstanding = FormCodec.money(b, "claimsOutstanding", base.claimsOutstanding),
                lossRatio = FormCodec.double(b, "lossRatio", base.lossRatio),
                credibilityFactor = FormCodec.double(b, "credibilityFactor", base.credibilityFactor),
                recommendedLoadingPct = FormCodec.double(b, "recommendedLoadingPct", base.recommendedLoadingPct),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "periodLabel").isBlank()) add("Period is required")
                val cred = FormCodec.double(b, "credibilityFactor", 0.0)
                if (cred < 0.0 || cred > 1.0) add("Credibility factor must be between 0 and 1")
            }
        },
    )

    val groupSizeMatrix = TypedEntityDescriptor(
        id = "group-size-matrices",
        singular = "Group Size Matrix",
        plural = "Group Size Matrices",
        description = "Demography sizing factors applied to price by group-size band.",
        isGroup = true,
        serializer = GroupSizeMatrix.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("groupSizeRef", "Group size", FieldKind.ENTITY_PICKER, refEntityId = "group-sizes", helper = "Group-size band this factor applies to"),
            FormField("factor", "Factor", FieldKind.NUMBER, helper = "Multiplier on the price, e.g. 0.92"),
            FormField("basis", "Basis", FieldKind.TEXT, helper = "What the factor multiplies, e.g. Base premium"),
            FormField("productLine", "Product line", FieldKind.ENUM, enumNames(GroupSizeProductLine.entries.toTypedArray())),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { g: GroupSizeMatrix -> g.name },
            "Factor" to { g -> g.factor.toString() },
            "Basis" to { g -> g.basis },
            "Line" to { g -> g.productLine.name },
            "Active" to { g -> if (g.active) "Yes" else "No" },
        ),
        factory = { GroupSizeMatrix(name = "") },
        reader = { g ->
            mapOf(
                "name" to g.name,
                "groupSizeRef" to g.groupSizeRef,
                "factor" to g.factor.toString(),
                "basis" to g.basis,
                "productLine" to g.productLine.name,
                "active" to g.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                groupSizeRef = FormCodec.str(b, "groupSizeRef", base.groupSizeRef),
                factor = FormCodec.double(b, "factor", base.factor),
                basis = FormCodec.str(b, "basis", base.basis),
                productLine = FormCodec.enum(b, "productLine", base.productLine),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.double(b, "factor", 1.0) <= 0.0) add("Factor must be greater than 0")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(rfq, experienceRating, groupSizeMatrix)
}
