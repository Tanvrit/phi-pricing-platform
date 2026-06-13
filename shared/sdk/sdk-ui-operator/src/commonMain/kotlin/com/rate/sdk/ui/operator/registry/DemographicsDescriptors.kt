package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.demographics.AgeBand
import com.rate.sdk.catalog.model.demographics.FamilyRelation
import com.rate.sdk.catalog.model.demographics.Gender
import com.rate.sdk.catalog.model.demographics.MemberCategory
import com.rate.sdk.catalog.model.demographics.MemberType
import com.rate.sdk.catalog.model.demographics.PaymentFrequency
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the DEMOGRAPHICS master entities surfaced in the operator console:
 *  - age-bands           — age brackets driving eligibility + rate lookups.
 *  - genders             — gender options on member/proposer forms.
 *  - payment-frequencies — premium instalment cadence + loading.
 *  - member-types        — broad covered-person categories (self/spouse/child/parent).
 *  - family-relations    — specific relationships, each rolling up to a member type.
 *
 * These ride the same generic ConfigEntityScreen as catalog entities; the entityId equals the REST
 * resource path segment the integrator wires (e.g. "age-bands" -> /api/admin/age-bands).
 */
internal object DemographicsDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val ageBand = TypedEntityDescriptor(
        id = "age-bands",
        singular = "Age Band",
        plural = "Age Bands",
        description = "Age brackets that drive eligibility and rate-table lookups.",
        isGroup = false,
        serializer = AgeBand.serializer(),
        fields = listOf(
            FormField("label", "Label", FieldKind.TEXT, required = true, helper = "Display label, e.g. 26-35"),
            FormField("minAge", "Min age", FieldKind.NUMBER, helper = "Inclusive lower bound"),
            FormField("maxAge", "Max age", FieldKind.NUMBER, helper = "Inclusive upper bound"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Label" to { a: AgeBand -> a.label },
            "Range" to { a -> "${a.minAge}-${a.maxAge}" },
            "Order" to { a -> a.sortOrder.toString() },
            "Active" to { a -> if (a.active) "Yes" else "No" },
        ),
        factory = { AgeBand(label = "") },
        reader = { a ->
            mapOf(
                "label" to a.label,
                "minAge" to a.minAge.toString(),
                "maxAge" to a.maxAge.toString(),
                "sortOrder" to a.sortOrder.toString(),
                "active" to a.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                label = FormCodec.str(b, "label", base.label),
                minAge = FormCodec.int(b, "minAge", base.minAge),
                maxAge = FormCodec.int(b, "maxAge", base.maxAge),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "minAge") > FormCodec.int(b, "maxAge")) add("Min age must be <= max age")
            }
        },
    )

    val gender = TypedEntityDescriptor(
        id = "genders",
        singular = "Gender",
        plural = "Genders",
        description = "Gender options offered on member and proposer forms.",
        isGroup = false,
        serializer = Gender.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable machine token, e.g. MALE"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { g: Gender -> g.code },
            "Label" to { g -> g.label },
            "Order" to { g -> g.sortOrder.toString() },
            "Active" to { g -> if (g.active) "Yes" else "No" },
        ),
        factory = { Gender(code = "", label = "") },
        reader = { g ->
            mapOf(
                "code" to g.code,
                "label" to g.label,
                "sortOrder" to g.sortOrder.toString(),
                "active" to g.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
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

    val paymentFrequency = TypedEntityDescriptor(
        id = "payment-frequencies",
        singular = "Payment Frequency",
        plural = "Payment Frequencies",
        description = "Premium instalment cadence and the loading applied for paying in parts.",
        isGroup = false,
        serializer = PaymentFrequency.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable token, e.g. MONTHLY"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("installmentsPerYear", "Installments / year", FieldKind.NUMBER, helper = "Single/Annual = 1, Monthly = 12"),
            FormField("loadingRate", "Loading rate", FieldKind.NUMBER, helper = "Fraction, e.g. 0.03 for +3%"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { p: PaymentFrequency -> p.code },
            "Label" to { p -> p.label },
            "Installments" to { p -> p.installmentsPerYear.toString() },
            "Loading" to { p -> p.loadingRate.toString() },
            "Active" to { p -> if (p.active) "Yes" else "No" },
        ),
        factory = { PaymentFrequency(code = "", label = "") },
        reader = { p ->
            mapOf(
                "code" to p.code,
                "label" to p.label,
                "installmentsPerYear" to p.installmentsPerYear.toString(),
                "loadingRate" to p.loadingRate.toString(),
                "sortOrder" to p.sortOrder.toString(),
                "active" to p.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                installmentsPerYear = FormCodec.int(b, "installmentsPerYear", base.installmentsPerYear),
                loadingRate = FormCodec.double(b, "loadingRate", base.loadingRate),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "installmentsPerYear", 1) < 1) add("Installments per year must be at least 1")
            }
        },
    )

    val memberType = TypedEntityDescriptor(
        id = "member-types",
        singular = "Member Type",
        plural = "Member Types",
        description = "Broad categories of covered people (self, spouse, child, parent).",
        isGroup = false,
        serializer = MemberType.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable token, e.g. SPOUSE"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("isAdult", "Is adult", FieldKind.BOOL, helper = "Children are non-adult dependents"),
            FormField("category", "Category", FieldKind.ENUM, enumNames(MemberCategory.entries.toTypedArray())),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { m: MemberType -> m.code },
            "Label" to { m -> m.label },
            "Adult" to { m -> if (m.isAdult) "Yes" else "No" },
            "Category" to { m -> m.category.name },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { MemberType(code = "", label = "") },
        reader = { m ->
            mapOf(
                "code" to m.code,
                "label" to m.label,
                "isAdult" to m.isAdult.toString(),
                "category" to m.category.name,
                "sortOrder" to m.sortOrder.toString(),
                "active" to m.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                isAdult = FormCodec.bool(b, "isAdult", base.isAdult),
                category = FormCodec.enum(b, "category", base.category),
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

    val familyRelation = TypedEntityDescriptor(
        id = "family-relations",
        singular = "Family Relation",
        plural = "Family Relations",
        description = "Specific relationships to the proposer, each rolling up to a member type.",
        isGroup = false,
        serializer = FamilyRelation.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Stable token, e.g. SON"),
            FormField("label", "Label", FieldKind.TEXT, required = true),
            FormField("memberTypeRef", "Member type", FieldKind.ENTITY_PICKER, refEntityId = "member-types", helper = "Broad bucket this relation maps to"),
            FormField("maxCount", "Max count", FieldKind.NUMBER, helper = "Max allowed on one policy"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { r: FamilyRelation -> r.code },
            "Label" to { r -> r.label },
            "Max" to { r -> r.maxCount.toString() },
            "Active" to { r -> if (r.active) "Yes" else "No" },
        ),
        factory = { FamilyRelation(code = "", label = "") },
        reader = { r ->
            mapOf(
                "code" to r.code,
                "label" to r.label,
                "memberTypeRef" to r.memberTypeRef,
                "maxCount" to r.maxCount.toString(),
                "sortOrder" to r.sortOrder.toString(),
                "active" to r.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                label = FormCodec.str(b, "label", base.label),
                memberTypeRef = FormCodec.str(b, "memberTypeRef", base.memberTypeRef),
                maxCount = FormCodec.int(b, "maxCount", base.maxCount),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "code").isBlank()) add("Code is required")
                if (FormCodec.str(b, "label").isBlank()) add("Label is required")
                if (FormCodec.int(b, "maxCount", 1) < 1) add("Max count must be at least 1")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(ageBand, gender, paymentFrequency, memberType, familyRelation)
}
