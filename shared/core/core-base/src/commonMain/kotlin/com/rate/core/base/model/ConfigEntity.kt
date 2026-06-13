package com.rate.core.base.model

import kotlinx.serialization.Serializable

/** Publish lifecycle of an admin-managed config entity. */
@Serializable
enum class EntityStatus { DRAFT, PUBLISHED, RETIRED }

/**
 * Admin-manageable configuration/catalog entity. Everything an internal user can
 * create/edit/delete from the frontend implements this: plans, covers, sections,
 * tenures, pincode-zones, add-ons, discounts, rate rows, group config, etc.
 *
 * `status` + `draftOf` give safe draft/publish so an in-progress edit never breaks
 * live rating: edits land as a DRAFT (draftOf = published id) and atomically replace
 * the PUBLISHED version on publish.
 */
interface ConfigEntity : BaseDataClass {
    val status: EntityStatus
    /** When this is a DRAFT, the id of the PUBLISHED entity it derives from (else null). */
    val draftOf: String?
    val createdBy: String?
    val updatedBy: String?
}
