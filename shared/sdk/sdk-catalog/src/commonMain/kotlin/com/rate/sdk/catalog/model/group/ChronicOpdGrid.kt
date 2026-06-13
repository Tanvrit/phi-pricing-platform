package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A row of the Chronic-Management OPD grid: per chronic condition, the consults/tests/
 * frequency/coaching entitlements. Relocated from
 * `Product Benefit Table EE GHI/Annexure.csv` ("Chronic Management OPD Grid").
 *
 * e.g. condition="Hypertension", category="Single", gpConsults=2, imConsults=1,
 * superSpecialist="Cardiologist (1)", keyTests=[FPG, Lipid profile, …], testFrequency="Annual",
 * healthCoaching=[Nutritionist, Physiotherapist, Psychologist].
 */
@Serializable
data class ChronicOpdGrid(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("condition") val condition: String,
    /** "Single" / "Double" / "Triple" comorbidity category. */
    @SerialName("category") val category: String = "Single",
    /** GP (tele) consults entitlement. */
    @SerialName("gpConsults") val gpConsults: Int = 0,
    /** Internal-medicine consults entitlement. */
    @SerialName("imConsults") val imConsults: Int = 0,
    @SerialName("superSpecialist") val superSpecialist: String = "",
    @SerialName("keyTests") val keyTests: List<String> = emptyList(),
    @SerialName("testFrequency") val testFrequency: String = "Annual",
    @SerialName("healthCoaching") val healthCoaching: List<String> = emptyList(),
    @SerialName("displayOrder") val displayOrder: Int = 0,
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
