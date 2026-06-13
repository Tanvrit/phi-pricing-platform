package com.rate.sdk.catalog.model.underwriting

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A clinical treatment category (a coarse specialty bucket such as Cardiology or Oncology) used to
 * group [DiseaseMapping] rows and to drive specialty-level rules (sub-limits, waiting periods,
 * exclusions). It is the spine of the Dorian "slide 02" disease-to-category mapping: every mapped
 * disease points at exactly one category so underwriting and rating can reason per body system.
 */
@Serializable
data class TreatmentCategory(
    @SerialName("_id") override val id: String = newId(),
    /** Short stable code (e.g. "CARD") used in rules and imports. */
    @SerialName("code") val code: String,
    /** Operator-facing display name (e.g. "Cardiology"). */
    @SerialName("name") val name: String,
    /** Free-text description of what the category covers. */
    @SerialName("description") val description: String = "",
    /** The broad body system this specialty treats (e.g. "Cardiovascular"). */
    @SerialName("bodySystem") val bodySystem: String = "",
    /** Display/priority ordering within category pickers. */
    @SerialName("sortOrder") val sortOrder: Int = 0,
    /** Whether this category is selectable for new mappings. */
    @SerialName("active") val active: Boolean = true,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity {
    companion object {
        fun defaults(): List<TreatmentCategory> = listOf(
            TreatmentCategory(code = "CARD", name = "Cardiology", bodySystem = "Cardiovascular", description = "Heart and circulatory conditions.", sortOrder = 10),
            TreatmentCategory(code = "ONCO", name = "Oncology", bodySystem = "Systemic", description = "Cancers and malignancies.", sortOrder = 20),
            TreatmentCategory(code = "ORTH", name = "Orthopaedics", bodySystem = "Musculoskeletal", description = "Bones, joints and spine.", sortOrder = 30),
            TreatmentCategory(code = "NEPH", name = "Nephrology", bodySystem = "Renal", description = "Kidney and dialysis.", sortOrder = 40),
            TreatmentCategory(code = "NEUR", name = "Neurology", bodySystem = "Nervous", description = "Brain, spine and nerves.", sortOrder = 50),
            TreatmentCategory(code = "GAST", name = "Gastroenterology", bodySystem = "Digestive", description = "Stomach, liver and intestines.", sortOrder = 60),
            TreatmentCategory(code = "MATR", name = "Maternity", bodySystem = "Reproductive", description = "Pregnancy and childbirth.", sortOrder = 70),
            TreatmentCategory(code = "OPHT", name = "Ophthalmology", bodySystem = "Eye", description = "Eye and vision conditions.", sortOrder = 80),
            TreatmentCategory(code = "ENT", name = "ENT", bodySystem = "Ear/Nose/Throat", description = "Ear, nose and throat.", sortOrder = 90),
            TreatmentCategory(code = "DENT", name = "Dental", bodySystem = "Oral", description = "Teeth and oral surgery.", sortOrder = 100),
        )
    }
}
