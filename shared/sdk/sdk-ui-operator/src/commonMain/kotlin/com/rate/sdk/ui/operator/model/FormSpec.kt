package com.rate.sdk.ui.operator.model

/**
 * Metadata-driven config-form description.
 *
 * The operator console renders + edits MANY admin entities through ONE generic screen
 * ([com.rate.sdk.ui.operator.screen.ConfigEntityScreen]). Rather than hand-write a form per
 * entity, each entity describes its editable fields as a list of [FormField]s; the screen reads
 * the spec to paint inputs and the [EntityDescriptor.applyEdit] / [EntityDescriptor.read]
 * closures move values between the form buffer and the typed entity.
 *
 * Why a buffer of strings rather than typed bindings? It keeps the generic editor truly generic:
 * every field round-trips through a `Map<String, String>` ([FormBuffer]); the per-entity
 * descriptor owns the (typed) parse/format. Numbers, enums and rupee amounts are parsed by the
 * descriptor with the field's [FieldKind] as a hint to the renderer (keyboard, dropdown, etc.).
 */

/** The renderer hint for a [FormField]. The descriptor still owns the actual typed parse. */
enum class FieldKind {
    TEXT,
    MULTILINE,
    NUMBER,
    MONEY,
    BOOL,
    /** A single choice out of [FormField.options]. */
    ENUM,
    /** Comma-separated list rendered as an editable text field of chips. */
    LIST,
    /**
     * A repeating table of structured rows (a `List<SomeObject>`). The row shape is described by
     * [FormField.rowSpec]; the descriptor serialises the list to a JSON string in the buffer and
     * parses it back. The renderer paints an add/remove/reorder row editor whose columns are the
     * [RowSpec.columns].
     */
    TABLE,

    /**
     * A key→value(s) map (e.g. `Map<String, List<String>>`). [FormField.rowSpec] (with its first
     * column the key and second the value-list) describes the editable rows. Serialised as JSON in
     * the buffer like [TABLE].
     */
    KEY_VALUE,

    /**
     * A picker of one id from a sibling entity collection (resolved by the renderer against the
     * entity named in [FormField.refEntityId]). Falls back to a free-text id when the picker can't
     * resolve. Stored as a single id string.
     */
    ENTITY_PICKER,

    /**
     * A multi-select of ids from a sibling entity collection ([FormField.refEntityId]). Stored as a
     * comma-separated id list (same buffer encoding as [LIST]).
     */
    MULTI_SELECT,

    /**
     * A single calendar date stored as an ISO string "YYYY-MM-DD" in the buffer (pure string — no
     * new serialization). The renderer paints a validated text field
     * ([com.rate.sdk.ui.kit.components.AegisDateInput]); the descriptor still owns any typed parse.
     */
    DATE,

    /**
     * An inclusive date range stored as a single ISO string "YYYY-MM-DD..YYYY-MM-DD" in the buffer
     * (either side may be blank for an open-ended range). Rendered as two linked date inputs
     * ([com.rate.sdk.ui.kit.components.AegisDateRangeInput]); still a plain string in the buffer.
     */
    DATE_RANGE,

    /** Read-only identity/system field (id, version, timestamps) — shown but never edited. */
    READONLY,
}

/**
 * One sub-field (column) of a [FieldKind.TABLE] / [FieldKind.KEY_VALUE] row. Reuses [FieldKind] as
 * the per-cell renderer hint; only the scalar kinds (TEXT/MULTILINE/NUMBER/MONEY/BOOL/ENUM/LIST)
 * are meaningful for a cell.
 */
data class RowColumn(
    /** JSON property name of this cell within the row object. */
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<String> = emptyList(),
    val helper: String? = null,
)

/** Describes the row shape of a [FieldKind.TABLE] / [FieldKind.KEY_VALUE] field. */
data class RowSpec(
    val columns: List<RowColumn>,
    /** Label of the "add row" affordance (e.g. "Add option"). */
    val addLabel: String = "Add row",
)

/**
 * One editable (or read-only) field of an admin entity.
 *
 * @param key          form-buffer key (typically the entity property name).
 * @param label        human label shown above the input.
 * @param kind         renderer hint.
 * @param options      enum choices (for [FieldKind.ENUM]).
 * @param helper       optional helper copy under the input.
 * @param required     empty-on-create blocks the save when true.
 * @param rowSpec      row shape for [FieldKind.TABLE] / [FieldKind.KEY_VALUE].
 * @param refEntityId  sibling entity id (registry id) for [FieldKind.ENTITY_PICKER] / [MULTI_SELECT].
 */
data class FormField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<String> = emptyList(),
    val helper: String? = null,
    val required: Boolean = false,
    val rowSpec: RowSpec? = null,
    val refEntityId: String? = null,
)

/** The mutable string-keyed working buffer the generic form edits. */
typealias FormBuffer = Map<String, String>

/**
 * Everything the generic CRUD machinery needs to manage ONE admin entity type, decoupled from
 * the concrete `T`. A [ConfigEntityRegistry] holds one descriptor per entity; the screen and the
 * ViewModel only ever see the descriptor, so the same code drives all ~25 entity types.
 *
 * The descriptor is intentionally NOT generic in `T` at the use site — it captures `T` internally
 * (the registry builds it from the typed serializer + closures) and exposes only `Any`-typed
 * boundaries, so a `List<EntityDescriptor>` can hold heterogeneous entity types in one registry.
 */
interface EntityDescriptor {
    /** Stable nav/registry id (e.g. "plans"). Also the admin REST resource path segment. */
    val id: String
    /** Singular display label (e.g. "Plan"). */
    val singular: String
    /** Plural display label (e.g. "Plans"). */
    val plural: String
    /** Short one-line description shown atop the surface. */
    val description: String
    /** Whether this entity belongs to the GROUP product line (for nav grouping). */
    val isGroup: Boolean

    /** Editable form fields (in display order). */
    val fields: List<FormField>

    /** Columns shown in the list table: pairs of (header, value-extractor over the entity). */
    val listColumns: List<Pair<String, (Any) -> String>>

    /** Stable id of an entity instance (its `_id`). */
    fun idOf(entity: Any): String
    /** Display title of an entity instance for the table/drawer header. */
    fun titleOf(entity: Any): String
    /** Optimistic-concurrency version of an entity instance. */
    fun versionOf(entity: Any): Long
    /** Lifecycle status name ("DRAFT"/"PUBLISHED"/"RETIRED") for the status pill. */
    fun statusOf(entity: Any): String
    /** Whether the instance is soft-deleted (drives the restore affordance). */
    fun isDeleted(entity: Any): Boolean

    /** A fresh default entity for the "create new" form. */
    fun newInstance(): Any
    /** Hydrate a form buffer from an entity instance (edit path). */
    fun read(entity: Any): FormBuffer
    /**
     * Apply the form buffer onto a base entity, returning the typed entity to persist.
     * Throws [IllegalArgumentException] with a friendly message on a parse error.
     */
    fun applyEdit(base: Any, buffer: FormBuffer): Any

    /** Per-instance pure validation (empty = valid). Runs before save. */
    fun validate(buffer: FormBuffer): List<String>
}
