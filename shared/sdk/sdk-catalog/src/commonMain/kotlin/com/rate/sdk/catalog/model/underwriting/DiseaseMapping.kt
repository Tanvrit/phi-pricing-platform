package com.rate.sdk.catalog.model.underwriting

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Maps a clinical diagnosis (ICD-10 coded) to its [TreatmentCategory], its pre-existing-disease
 * (PED) treatment, its waiting period and any exclusion note. This is the Dorian "slide 02"
 * disease-mapping master that lets underwriting decide whether a declared condition is a PED, how
 * long it must serve a waiting period, and which specialty rules apply.
 *
 * The category link is stored as the referenced [TreatmentCategory] id string (ENTITY_PICKER) so
 * this model never imports another domain's type and the link survives renames.
 */
@Serializable
data class DiseaseMapping(
    @SerialName("_id") override val id: String = newId(),
    /** ICD-10 code (e.g. "E11" for Type-2 Diabetes). */
    @SerialName("icdCode") val icdCode: String,
    /** Human-readable diagnosis name. */
    @SerialName("diseaseName") val diseaseName: String,
    /** Id of the owning [TreatmentCategory] (ENTITY_PICKER -> "treatment-categories"). */
    @SerialName("treatmentCategoryRef") val treatmentCategoryRef: String = "",
    /** True if a declaration of this disease counts as a pre-existing disease. */
    @SerialName("pedFlag") val pedFlag: Boolean = false,
    /** Months the condition must wait before it is covered (0 = covered immediately). */
    @SerialName("waitingPeriodMonths") val waitingPeriodMonths: Int = 0,
    /** Optional exclusion / special-condition note shown to underwriters. */
    @SerialName("exclusionNote") val exclusionNote: String = "",
    /** Whether this mapping is currently in force for underwriting. */
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
        fun defaults(): List<DiseaseMapping> = listOf(
            DiseaseMapping(icdCode = "E11", diseaseName = "Type 2 Diabetes Mellitus", pedFlag = true, waitingPeriodMonths = 36, exclusionNote = "Standard PED waiting period applies."),
            DiseaseMapping(icdCode = "I10", diseaseName = "Essential (primary) Hypertension", pedFlag = true, waitingPeriodMonths = 36),
            DiseaseMapping(icdCode = "I25", diseaseName = "Ischaemic Heart Disease", pedFlag = true, waitingPeriodMonths = 48, exclusionNote = "Cardiac PED; loading may apply."),
            DiseaseMapping(icdCode = "C50", diseaseName = "Malignant Neoplasm of Breast", pedFlag = true, waitingPeriodMonths = 48),
            DiseaseMapping(icdCode = "N18", diseaseName = "Chronic Kidney Disease", pedFlag = true, waitingPeriodMonths = 48, exclusionNote = "Dialysis sub-limit may apply."),
            DiseaseMapping(icdCode = "J45", diseaseName = "Asthma", pedFlag = true, waitingPeriodMonths = 24),
            DiseaseMapping(icdCode = "K80", diseaseName = "Cholelithiasis (Gallstones)", pedFlag = false, waitingPeriodMonths = 24, exclusionNote = "Specific-disease 24-month waiting period."),
            DiseaseMapping(icdCode = "M17", diseaseName = "Osteoarthritis of Knee", pedFlag = true, waitingPeriodMonths = 36),
            DiseaseMapping(icdCode = "O80", diseaseName = "Single Spontaneous Delivery", pedFlag = false, waitingPeriodMonths = 9, exclusionNote = "Maternity waiting period."),
            DiseaseMapping(icdCode = "H25", diseaseName = "Senile Cataract", pedFlag = false, waitingPeriodMonths = 24),
        )
    }
}
