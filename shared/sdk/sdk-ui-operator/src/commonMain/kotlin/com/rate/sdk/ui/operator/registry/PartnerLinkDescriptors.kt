package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.partnerlink.BusinessType
import com.rate.sdk.catalog.model.partnerlink.ClientLocation
import com.rate.sdk.catalog.model.partnerlink.IdentifierType
import com.rate.sdk.catalog.model.partnerlink.RelatedParty
import com.rate.sdk.catalog.model.partnerlink.RelationType
import com.rate.sdk.catalog.model.partnerlink.RemunerationType
import com.rate.sdk.catalog.model.partnerlink.SalesIntermediaryMapping
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the PARTNERS partner-linkage masters:
 *  - `sales-mappings`   — intermediary x insurer x channel commission mapping (relational).
 *  - `client-locations` — physical client sites pinned to rating zones.
 *  - `business-types`   — nature-of-business classification (NB / Renewal / Portability / …).
 *  - `related-parties`  — corporate relationships of the group client.
 *
 * Each rides the same generic ConfigEntityScreen; the entity id equals the route path segment
 * (e.g. `client-locations` -> /api/admin/client-locations). Cross-entity references are stored as
 * the sibling entity's id string via ENTITY_PICKER.
 */
internal object PartnerLinkDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val salesMapping = TypedEntityDescriptor(
        id = "sales-mappings",
        singular = "Sales Mapping",
        plural = "Sales Mappings",
        description = "Links a sales intermediary to an insurer and channel with the agreed commission and effective window.",
        isGroup = true,
        serializer = SalesIntermediaryMapping.serializer(),
        fields = listOf(
            FormField("intermediaryRef", "Intermediary", FieldKind.ENTITY_PICKER, refEntityId = "intermediaries", required = true),
            FormField("insurerRef", "Insurer", FieldKind.ENTITY_PICKER, refEntityId = "insurers", required = true),
            FormField("channelRef", "Channel", FieldKind.ENTITY_PICKER, refEntityId = "channels"),
            FormField("remunerationType", "Remuneration type", FieldKind.ENUM, enumNames(RemunerationType.entries.toTypedArray())),
            FormField("commissionPct", "Commission %", FieldKind.NUMBER, helper = "Percent of premium, e.g. 7.5 = 7.5%"),
            FormField("agreementNo", "Agreement no", FieldKind.TEXT),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD; blank = open-ended"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Intermediary" to { m: SalesIntermediaryMapping -> m.intermediaryRef.ifBlank { "—" } },
            "Insurer" to { m -> m.insurerRef.ifBlank { "—" } },
            "Channel" to { m -> m.channelRef.ifBlank { "—" } },
            "Type" to { m -> m.remunerationType.name },
            "Commission" to { m -> "${m.commissionPct}%" },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { SalesIntermediaryMapping() },
        reader = { m ->
            mapOf(
                "intermediaryRef" to m.intermediaryRef,
                "insurerRef" to m.insurerRef,
                "channelRef" to m.channelRef,
                "remunerationType" to m.remunerationType.name,
                "commissionPct" to FormCodec.fmt(m.commissionPct),
                "agreementNo" to m.agreementNo,
                "effectiveFrom" to m.effectiveFrom,
                "effectiveTo" to m.effectiveTo,
                "active" to FormCodec.fmtBool(m.active),
            )
        },
        editor = { base, b ->
            base.copy(
                intermediaryRef = FormCodec.str(b, "intermediaryRef", base.intermediaryRef),
                insurerRef = FormCodec.str(b, "insurerRef", base.insurerRef),
                channelRef = FormCodec.str(b, "channelRef", base.channelRef),
                remunerationType = FormCodec.enum(b, "remunerationType", base.remunerationType),
                commissionPct = FormCodec.double(b, "commissionPct", base.commissionPct),
                agreementNo = FormCodec.str(b, "agreementNo", base.agreementNo),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "intermediaryRef").isBlank()) add("Intermediary is required")
                if (FormCodec.str(b, "insurerRef").isBlank()) add("Insurer is required")
                if (FormCodec.double(b, "commissionPct") < 0.0) add("Commission % cannot be negative")
            }
        },
    )

    val clientLocation = TypedEntityDescriptor(
        id = "client-locations",
        singular = "Client Location",
        plural = "Client Locations",
        description = "Physical client sites / branches pinned to a rating zone — drives zone loadings and the census split.",
        isGroup = true,
        serializer = ClientLocation.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("addressLine", "Address", FieldKind.MULTILINE),
            FormField("city", "City", FieldKind.TEXT),
            FormField("stateName", "State", FieldKind.TEXT),
            FormField("pincode", "Pincode", FieldKind.TEXT),
            FormField("zoneCode", "Zone code", FieldKind.TEXT, helper = "e.g. ZONE_A / ZONE_B / ZONE_C"),
            FormField("livesCount", "Lives count", FieldKind.NUMBER),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { l: ClientLocation -> l.name },
            "City" to { l -> l.city.ifBlank { "—" } },
            "State" to { l -> l.stateName.ifBlank { "—" } },
            "Pincode" to { l -> l.pincode.ifBlank { "—" } },
            "Zone" to { l -> l.zoneCode.ifBlank { "—" } },
            "Lives" to { l -> l.livesCount.toString() },
            "Active" to { l -> if (l.active) "Yes" else "No" },
        ),
        factory = { ClientLocation(name = "") },
        reader = { l ->
            mapOf(
                "name" to l.name,
                "addressLine" to l.addressLine,
                "city" to l.city,
                "stateName" to l.stateName,
                "pincode" to l.pincode,
                "zoneCode" to l.zoneCode,
                "livesCount" to l.livesCount.toString(),
                "sortOrder" to l.sortOrder.toString(),
                "active" to FormCodec.fmtBool(l.active),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                addressLine = FormCodec.str(b, "addressLine", base.addressLine),
                city = FormCodec.str(b, "city", base.city),
                stateName = FormCodec.str(b, "stateName", base.stateName),
                pincode = FormCodec.str(b, "pincode", base.pincode),
                zoneCode = FormCodec.str(b, "zoneCode", base.zoneCode),
                livesCount = FormCodec.int(b, "livesCount", base.livesCount),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") } },
    )

    val businessType = TypedEntityDescriptor(
        id = "business-types",
        singular = "Business Type",
        plural = "Business Types",
        description = "Nature-of-business classification (New Business / Renewal / Portability / …) used for commission and MIS bucketing.",
        isGroup = true,
        serializer = BusinessType.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Short stable code, e.g. NB / REN"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { t: BusinessType -> t.code },
            "Label" to { t -> t.label },
            "Description" to { t -> t.description.ifBlank { "—" } },
            "Active" to { t -> if (t.active) "Yes" else "No" },
        ),
        factory = { BusinessType(code = "", label = "") },
        reader = { t ->
            mapOf(
                "code" to t.code,
                "label" to t.label,
                "description" to t.description,
                "sortOrder" to t.sortOrder.toString(),
                "active" to FormCodec.fmtBool(t.active),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                description = FormCodec.str(b, "description", base.description),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
            }
        },
    )

    val relatedParty = TypedEntityDescriptor(
        id = "related-parties",
        singular = "Related Party",
        plural = "Related Parties",
        description = "Corporate entities related to the group client (promoter / subsidiary / holding / …) for consolidated billing and KYC.",
        isGroup = true,
        serializer = RelatedParty.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("relationType", "Relation type", FieldKind.ENUM, enumNames(RelationType.entries.toTypedArray())),
            FormField("identifierType", "Identifier type", FieldKind.ENUM, enumNames(IdentifierType.entries.toTypedArray())),
            FormField("identifierNo", "Identifier no", FieldKind.TEXT),
            FormField("holdingPct", "Holding %", FieldKind.NUMBER, helper = "Ownership %, e.g. 51.0"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { p: RelatedParty -> p.name },
            "Relation" to { p -> p.relationType.name },
            "ID type" to { p -> p.identifierType.name },
            "ID no" to { p -> p.identifierNo.ifBlank { "—" } },
            "Active" to { p -> if (p.active) "Yes" else "No" },
        ),
        factory = { RelatedParty(name = "") },
        reader = { p ->
            mapOf(
                "name" to p.name,
                "relationType" to p.relationType.name,
                "identifierType" to p.identifierType.name,
                "identifierNo" to p.identifierNo,
                "holdingPct" to FormCodec.fmt(p.holdingPct),
                "active" to FormCodec.fmtBool(p.active),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                relationType = FormCodec.enum(b, "relationType", base.relationType),
                identifierType = FormCodec.enum(b, "identifierType", base.identifierType),
                identifierNo = FormCodec.str(b, "identifierNo", base.identifierNo),
                holdingPct = FormCodec.double(b, "holdingPct", base.holdingPct),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b -> buildList { if (FormCodec.str(b, "name").isBlank()) add("Name is required") } },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(salesMapping, clientLocation, businessType, relatedParty)
}
