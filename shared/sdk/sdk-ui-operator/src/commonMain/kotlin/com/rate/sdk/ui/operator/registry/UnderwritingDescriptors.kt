package com.rate.sdk.ui.operator.registry

import com.rate.sdk.catalog.model.underwriting.DiseaseMapping
import com.rate.sdk.catalog.model.underwriting.RatingDimension
import com.rate.sdk.catalog.model.underwriting.RatingParameter
import com.rate.sdk.catalog.model.underwriting.TreatmentCategory
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormField

/**
 * Admin-CRUD descriptors for the UNDERWRITING masters surfaced in the operator console:
 *  - "treatment-categories" — clinical specialty buckets (Cardiology, Oncology, ...).
 *  - "disease-mappings" — ICD-10 disease to category + PED/waiting-period mapping (Dorian slide 02).
 *  - "rating-parameters" — the nine-dimension U-factor rating grid (Dorian slide 03).
 *
 * All ride the same generic ConfigEntityScreen as catalog entities. Each descriptor id doubles as
 * the admin REST resource path segment (id "disease-mappings" -> /api/admin/disease-mappings).
 */
internal object UnderwritingDescriptors {

    private fun enumNames(values: Array<out Enum<*>>) = values.map { it.name }

    val treatmentCategory = TypedEntityDescriptor(
        id = "treatment-categories",
        singular = "Treatment Category",
        plural = "Treatment Categories",
        description = "Clinical specialty buckets that group diseases and drive specialty-level rules.",
        isGroup = false,
        serializer = TreatmentCategory.serializer(),
        fields = listOf(
            FormField("code", "Code", FieldKind.TEXT, required = true, helper = "Short stable code, e.g. CARD"),
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("bodySystem", "Body system", FieldKind.TEXT, helper = "e.g. Cardiovascular"),
            FormField("description", "Description", FieldKind.MULTILINE),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Code" to { c: TreatmentCategory -> c.code },
            "Name" to { c -> c.name },
            "Body system" to { c -> c.bodySystem.ifBlank { "—" } },
            "Active" to { c -> if (c.active) "Yes" else "No" },
        ),
        factory = { TreatmentCategory(code = "", name = "") },
        reader = { c ->
            mapOf(
                "code" to c.code,
                "name" to c.name,
                "bodySystem" to c.bodySystem,
                "description" to c.description,
                "sortOrder" to c.sortOrder.toString(),
                "active" to c.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                code = FormCodec.str(b, "code", base.code),
                name = FormCodec.str(b, "name", base.name),
                bodySystem = FormCodec.str(b, "bodySystem", base.bodySystem),
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

    val diseaseMapping = TypedEntityDescriptor(
        id = "disease-mappings",
        singular = "Disease Mapping",
        plural = "Disease Mappings",
        description = "ICD-10 disease to treatment category, PED flag and waiting period (Dorian slide 02).",
        isGroup = false,
        serializer = DiseaseMapping.serializer(),
        fields = listOf(
            FormField("icdCode", "ICD-10 code", FieldKind.TEXT, required = true, helper = "e.g. E11"),
            FormField("diseaseName", "Disease name", FieldKind.TEXT, required = true),
            FormField("treatmentCategoryRef", "Treatment category", FieldKind.ENTITY_PICKER, refEntityId = "treatment-categories"),
            FormField("pedFlag", "Pre-existing disease", FieldKind.BOOL),
            FormField("waitingPeriodMonths", "Waiting period (months)", FieldKind.NUMBER),
            FormField("exclusionNote", "Exclusion note", FieldKind.MULTILINE),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "ICD-10" to { m: DiseaseMapping -> m.icdCode },
            "Disease" to { m -> m.diseaseName },
            "PED" to { m -> if (m.pedFlag) "Yes" else "No" },
            "Waiting (mo)" to { m -> m.waitingPeriodMonths.toString() },
            "Active" to { m -> if (m.active) "Yes" else "No" },
        ),
        factory = { DiseaseMapping(icdCode = "", diseaseName = "") },
        reader = { m ->
            mapOf(
                "icdCode" to m.icdCode,
                "diseaseName" to m.diseaseName,
                "treatmentCategoryRef" to m.treatmentCategoryRef,
                "pedFlag" to m.pedFlag.toString(),
                "waitingPeriodMonths" to m.waitingPeriodMonths.toString(),
                "exclusionNote" to m.exclusionNote,
                "active" to m.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                icdCode = FormCodec.str(b, "icdCode", base.icdCode),
                diseaseName = FormCodec.str(b, "diseaseName", base.diseaseName),
                treatmentCategoryRef = FormCodec.str(b, "treatmentCategoryRef", base.treatmentCategoryRef),
                pedFlag = FormCodec.bool(b, "pedFlag", base.pedFlag),
                waitingPeriodMonths = FormCodec.int(b, "waitingPeriodMonths", base.waitingPeriodMonths),
                exclusionNote = FormCodec.str(b, "exclusionNote", base.exclusionNote),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "icdCode").isBlank()) add("ICD-10 code is required")
                if (FormCodec.str(b, "diseaseName").isBlank()) add("Disease name is required")
                if (FormCodec.int(b, "waitingPeriodMonths", 0) < 0) add("Waiting period cannot be negative")
            }
        },
    )

    val ratingParameter = TypedEntityDescriptor(
        id = "rating-parameters",
        singular = "Rating Parameter",
        plural = "Rating Parameters",
        description = "The nine-dimension U-factor rating grid — one multiplicative factor per dimension key (Dorian slide 03).",
        isGroup = false,
        serializer = RatingParameter.serializer(),
        fields = listOf(
            FormField("name", "Name", FieldKind.TEXT, required = true),
            FormField("dimension", "Dimension", FieldKind.ENUM, enumNames(RatingDimension.entries.toTypedArray())),
            FormField("paramKey", "Param key", FieldKind.TEXT, required = true, helper = "Matched value, e.g. 46-55 / ZONE_A / MANUFACTURING"),
            FormField("factorValue", "Factor value", FieldKind.NUMBER, helper = "Multiplier, 1.0 = neutral"),
            FormField("productLine", "Product line", FieldKind.ENUM, listOf("RETAIL", "GROUP")),
            FormField("effectiveFrom", "Effective from", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD"),
            FormField("effectiveTo", "Effective to", FieldKind.TEXT, helper = "ISO date YYYY-MM-DD (blank = open)"),
            FormField("sortOrder", "Sort order", FieldKind.NUMBER),
            FormField("active", "Active", FieldKind.BOOL),
        ),
        columns = listOf(
            "Name" to { p: RatingParameter -> p.name },
            "Dimension" to { p -> p.dimension.name },
            "Key" to { p -> p.paramKey },
            "Factor" to { p -> p.factorValue.toString() },
            "Line" to { p -> p.productLine },
            "Active" to { p -> if (p.active) "Yes" else "No" },
        ),
        factory = { RatingParameter(name = "", paramKey = "") },
        reader = { p ->
            mapOf(
                "name" to p.name,
                "dimension" to p.dimension.name,
                "paramKey" to p.paramKey,
                "factorValue" to p.factorValue.toString(),
                "productLine" to p.productLine,
                "effectiveFrom" to p.effectiveFrom,
                "effectiveTo" to p.effectiveTo,
                "sortOrder" to p.sortOrder.toString(),
                "active" to p.active.toString(),
            )
        },
        editor = { base, b ->
            base.copy(
                name = FormCodec.str(b, "name", base.name),
                dimension = FormCodec.enum(b, "dimension", base.dimension),
                paramKey = FormCodec.str(b, "paramKey", base.paramKey),
                factorValue = FormCodec.double(b, "factorValue", base.factorValue),
                productLine = FormCodec.str(b, "productLine", base.productLine),
                effectiveFrom = FormCodec.str(b, "effectiveFrom", base.effectiveFrom),
                effectiveTo = FormCodec.str(b, "effectiveTo", base.effectiveTo),
                sortOrder = FormCodec.int(b, "sortOrder", base.sortOrder),
                active = FormCodec.bool(b, "active", base.active),
            )
        },
        validator = { b ->
            buildList {
                if (FormCodec.str(b, "name").isBlank()) add("Name is required")
                if (FormCodec.str(b, "paramKey").isBlank()) add("Param key is required")
                if (FormCodec.double(b, "factorValue", 1.0) <= 0.0) add("Factor value must be greater than 0")
            }
        },
    )

    val all: List<TypedEntityDescriptor<*>> = listOf(treatmentCategory, diseaseMapping, ratingParameter)
}
