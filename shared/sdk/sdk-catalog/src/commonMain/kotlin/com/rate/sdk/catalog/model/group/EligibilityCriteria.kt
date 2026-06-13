package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An occupational risk class for a group product (e.g. "Class I - Administrative",
 * "Class IV - Hazardous"). Captures the occupations mapped to the class, an optional
 * premium-loading factor and any class-specific exclusions.
 */
@Serializable
data class RiskClass(
    @SerialName("name") val name: String,
    @SerialName("occupations") val occupations: List<String> = emptyList(),
    /** Multiplicative premium loading for this class (e.g. 1.25 = +25%); null = no loading. */
    @SerialName("premiumLoadingFactor") val premiumLoadingFactor: Double? = null,
    @SerialName("exclusions") val exclusions: List<String> = emptyList(),
)

/**
 * Eligibility criteria for a group product. Relocated from
 * `PBT Employer Employee Benefit (PA CI)/Eligibility.csv`.
 *
 * Ages are stored as free text labels ("Day1", "Lifelong") because the PBT expresses them
 * non-numerically; [minEntryAgeYears]/[maxEntryAgeYears] hold the numeric form when known.
 */
@Serializable
data class EligibilityCriteria(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("groupProductRef") val groupProductRef: String = "",
    @SerialName("minEntryAge") val minEntryAge: String = "Day1",
    @SerialName("maxEntryAge") val maxEntryAge: String = "Lifelong",
    @SerialName("renewalAge") val renewalAge: String = "Lifelong",
    @SerialName("minEntryAgeYears") val minEntryAgeYears: Int? = null,
    @SerialName("maxEntryAgeYears") val maxEntryAgeYears: Int? = null,
    /** "NA- Corporate", "Individual", etc. */
    @SerialName("proposerType") val proposerType: String = "",
    @SerialName("coverType") val coverType: String = "",
    @SerialName("policyType") val policyType: String = "",
    /** Occupational risk classes and their loadings/exclusions. */
    @SerialName("riskClasses") val riskClasses: List<RiskClass> = emptyList(),
    @SerialName("notes") val notes: String = "",
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity
