package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.partnerorg.Channel
import com.rate.sdk.catalog.model.partnerorg.ChannelType
import com.rate.sdk.catalog.model.partnerorg.Insurer
import com.rate.sdk.catalog.model.partnerorg.InsurerType
import com.rate.sdk.catalog.model.partnerorg.Intermediary
import com.rate.sdk.catalog.model.partnerorg.IntermediaryType
import com.rate.sdk.catalog.model.partnerorg.Tpa
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the PARTNERS domain surfaced in the operator console:
 *  - `insurers` — risk carriers / reinsurers the programme places business with.
 *  - `channels` — distribution channels with their commission-cap policy.
 *  - `intermediaries` — IRDAI-licensed brokers / agents / POSPs (linked to a channel).
 *  - `tpas` — third-party administrators servicing group health policies.
 *
 * Each rides the same generic ConfigEntityScreen as catalog entities. Descriptor ids equal the
 * admin route path segment (e.g. id "insurers" -> /api/admin/insurers).
 */
internal object PartnerOrgDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val insurer = TypedEntityDescriptor(
        id = "insurers",
        singular = "Insurer",
        plural = "Insurers",
        description = "Risk carriers and reinsurers — IRDAI identity, line of business and primary contact.",
        isGroup = false,
        serializer = Insurer.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true, helper = "Legal / trading name of the carrier"),
            FormField("irdaiRegNo", "IRDAI reg. no.", FieldKind.TEXT, helper = "Registration number on the certificate"),
            FormField("type", "Type", FieldKind.ENUM, enumNames(InsurerType.entries.toTypedArray())),
            FormField("contactPerson", "Contact person", FieldKind.TEXT),
            FormField("email", "Email", FieldKind.TEXT),
            FormField("phone", "Phone", FieldKind.TEXT),
            FormField("address", "Address", FieldKind.MULTILINE),
            FormField("rating", "Rating", FieldKind.TEXT, helper = "Claims-paying / financial-strength note"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { i: Insurer -> i.name },
            "Type" to { i -> i.type.name },
            "IRDAI" to { i -> i.irdaiRegNo.ifBlank { "—" } },
            "Contact" to { i -> i.contactPerson.ifBlank { "—" } },
            "Active" to { i -> if (i.active) "Yes" else "No" },
        ),
        factory = { Insurer(name = "") },
        reader = { i ->
            mapOf(
                "name" to i.name,
                "irdaiRegNo" to i.irdaiRegNo,
                "type" to i.type.name,
                "contactPerson" to i.contactPerson,
                "email" to i.email,
                "phone" to i.phone,
                "address" to i.address,
                "rating" to i.rating,
                "active" to i.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                irdaiRegNo = FormCodec.str(b, "irdaiRegNo", base.irdaiRegNo),
                type = FormCodec.enum(b, "type", base.type),
                contactPerson = FormCodec.str(b, "contactPerson", base.contactPerson),
                email = FormCodec.str(b, "email", base.email),
                phone = FormCodec.str(b, "phone", base.phone),
                address = FormCodec.str(b, "address", base.address),
                rating = FormCodec.str(b, "rating", base.rating),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                val email = FormCodec.str(b, "email")
                if (email.isNotBlank() && !email.contains('@')) add("Email looks invalid")
            }
        },
    )

    val channel = TypedEntityDescriptor(
        id = "channels",
        singular = "Channel",
        plural = "Channels",
        description = "Distribution channels and their commission-cap policy for sourced business.",
        isGroup = false,
        serializer = Channel.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Short uppercase code (e.g. BROKER)"),
            FormField("name", "Name", FieldKind.TEXT),
            FormField("type", "Type", FieldKind.ENUM, enumNames(ChannelType.entries.toTypedArray())),
            FormField("commissionCapPct", "Commission cap %", FieldKind.NUMBER, helper = "Max payable commission percentage"),
            FormField("defaultCommissionPct", "Default commission %", FieldKind.NUMBER),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { c: Channel -> c.code },
            "Name" to { c -> c.name.ifBlank { "—" } },
            "Type" to { c -> c.type.name },
            "Cap %" to { c -> c.commissionCapPct.toString() },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { Channel(code = "") },
        reader = { c ->
            mapOf(
                "code" to c.code,
                "name" to c.name,
                "type" to c.type.name,
                "commissionCapPct" to c.commissionCapPct.toString(),
                "defaultCommissionPct" to c.defaultCommissionPct.toString(),
                "sortOrder" to c.sortOrder.toString(),
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                type = FormCodec.enum(b, "type", base.type),
                commissionCapPct = FormCodec.double(b, "commissionCapPct", base.commissionCapPct),
                defaultCommissionPct = FormCodec.double(b, "defaultCommissionPct", base.defaultCommissionPct),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                val cap = FormCodec.double(b, "commissionCapPct", -1.0)
                if (cap < 0.0) add("Commission cap % cannot be negative")
            }
        },
    )

    val intermediary = TypedEntityDescriptor(
        id = "intermediaries",
        singular = "Intermediary",
        plural = "Intermediaries",
        description = "IRDAI-licensed brokers, agents and POSPs that place business through a channel.",
        isGroup = false,
        serializer = Intermediary.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("irdaiLicenseNo", "IRDAI licence no.", FieldKind.TEXT),
            FormField("channelRef", "Channel", FieldKind.ENTITY_PICKER, refEntityId = "channels"),
            FormField("type", "Type", FieldKind.ENUM, enumNames(IntermediaryType.entries.toTypedArray())),
            FormField("commissionPct", "Commission %", FieldKind.NUMBER, helper = "Must be within the channel cap"),
            FormField("email", "Email", FieldKind.TEXT),
            FormField("phone", "Phone", FieldKind.TEXT),
            FormField("validFrom", "Valid from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("validTo", "Valid to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { m: Intermediary -> m.name },
            "Type" to { m -> m.type.name },
            "Licence" to { m -> m.irdaiLicenseNo.ifBlank { "—" } },
            "Comm %" to { m -> m.commissionPct.toString() },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { Intermediary(name = "") },
        reader = { m ->
            mapOf(
                "name" to m.name,
                "irdaiLicenseNo" to m.irdaiLicenseNo,
                "channelRef" to m.channelRef,
                "type" to m.type.name,
                "commissionPct" to m.commissionPct.toString(),
                "email" to m.email,
                "phone" to m.phone,
                "validFrom" to m.validFrom,
                "validTo" to m.validTo,
                "active" to m.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                irdaiLicenseNo = FormCodec.str(b, "irdaiLicenseNo", base.irdaiLicenseNo),
                channelRef = FormCodec.str(b, "channelRef", base.channelRef),
                type = FormCodec.enum(b, "type", base.type),
                commissionPct = FormCodec.double(b, "commissionPct", base.commissionPct),
                email = FormCodec.str(b, "email", base.email),
                phone = FormCodec.str(b, "phone", base.phone),
                validFrom = FormCodec.str(b, "validFrom", base.validFrom),
                validTo = FormCodec.str(b, "validTo", base.validTo),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                val email = FormCodec.str(b, "email")
                if (email.isNotBlank() && !email.contains('@')) add("Email looks invalid")
                val comm = FormCodec.double(b, "commissionPct", -1.0)
                if (comm < 0.0) add("Commission % cannot be negative")
            }
        },
    )

    val tpa = TypedEntityDescriptor(
        id = "tpas",
        singular = "TPA",
        plural = "TPAs",
        description = "Third-party administrators servicing group health policies — IRDAI identity and contact.",
        isGroup = false,
        serializer = Tpa.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("irdaiRegNo", "IRDAI reg. no.", FieldKind.TEXT),
            FormField("contactPerson", "Contact person", FieldKind.TEXT),
            FormField("email", "Email", FieldKind.TEXT),
            FormField("phone", "Phone", FieldKind.TEXT),
            FormField("tollFree", "Toll-free", FieldKind.TEXT, helper = "24x7 cashless helpline"),
            FormField("address", "Address", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { t: Tpa -> t.name },
            "IRDAI" to { t -> t.irdaiRegNo.ifBlank { "—" } },
            "Contact" to { t -> t.contactPerson.ifBlank { "—" } },
            "Toll-free" to { t -> t.tollFree.ifBlank { "—" } },
            "Active" to { t -> if (t.active) "Yes" else "No" },
        ),
        factory = { Tpa(name = "") },
        reader = { t ->
            mapOf(
                "name" to t.name,
                "irdaiRegNo" to t.irdaiRegNo,
                "contactPerson" to t.contactPerson,
                "email" to t.email,
                "phone" to t.phone,
                "tollFree" to t.tollFree,
                "address" to t.address,
                "active" to t.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                irdaiRegNo = FormCodec.str(b, "irdaiRegNo", base.irdaiRegNo),
                contactPerson = FormCodec.str(b, "contactPerson", base.contactPerson),
                email = FormCodec.str(b, "email", base.email),
                phone = FormCodec.str(b, "phone", base.phone),
                tollFree = FormCodec.str(b, "tollFree", base.tollFree),
                address = FormCodec.str(b, "address", base.address),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                val email = FormCodec.str(b, "email")
                if (email.isNotBlank() && !email.contains('@')) add("Email looks invalid")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(insurer, channel, intermediary, tpa)
}
