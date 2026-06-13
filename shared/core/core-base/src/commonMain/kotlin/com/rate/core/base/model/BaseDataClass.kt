package com.rate.core.base.model

import kotlinx.datetime.Instant

/**
 * Wire/persistence envelope shared by every persisted or transmitted model.
 *
 * The `@SerialName` string of each field IS the MongoDB field name AND the JSON
 * field name — never rename one without a migration. Concrete entities are
 * `@Serializable data class`es that implement this interface and expose the five
 * fields with `@SerialName`:
 *
 * ```
 * @Serializable
 * data class Foo(
 *     @SerialName("_id") override val id: String = newId(),
 *     ...
 *     @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
 *     @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
 *     @SerialName("v") override val v: Long = 1,
 *     @SerialName("isDeleted") override val isDeleted: Boolean = false,
 * ) : BaseDataClass
 * ```
 *
 * Timestamp/version stamping on write is performed generically at the BSON-document
 * level by `server-persistence` (the only place the Mongo driver lives), so entities
 * carry no per-class boilerplate beyond these fields.
 */
interface BaseDataClass {
    /** Stable primary key (ULID-ish, app-generated). Maps to Mongo `_id`. */
    val id: String

    /** First-write instant. Immutable across updates. */
    val createdAt: Instant

    /** Last-write instant. Advanced on every upsert. */
    val updatedAt: Instant

    /** Optimistic-concurrency version. Starts at 1, bumped on every upsert. */
    val v: Long

    /** Soft-delete flag. Default queries filter `isDeleted == false`. */
    val isDeleted: Boolean
}
