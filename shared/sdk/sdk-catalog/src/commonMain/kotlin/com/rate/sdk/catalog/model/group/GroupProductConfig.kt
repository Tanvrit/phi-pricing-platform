package com.rate.sdk.catalog.model.group

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Floater vs non-floater group policy structure. */
@Serializable
enum class GroupPolicyType { FLOATER, NON_FLOATER }

/**
 * Top-level GROUP product configuration (Employer-Employee / GHI). Relocated from
 * `data/Indemnity- NEW ADDITION.csv` and `Product Benefit Table EE GHI/Employer Employee.csv`
 * "Features / Boundary Conditions" rows.
 *
 * `familyConstructs` captures the "ESCP and all TCS Combinations" boundary
 * (Employee/Spouse/Children/Parents constructs). [relationsCovered] is the long relations list.
 */
@Serializable
data class GroupProductConfig(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("policyType") val policyType: GroupPolicyType = GroupPolicyType.FLOATER,
    /** Relations eligible for cover (Self, Spouse, Son, …, Partner). */
    @SerialName("relationsCovered") val relationsCovered: List<String> = emptyList(),
    /** Family-floater constructs (ESCP / TCS combinations). */
    @SerialName("familyConstructs") val familyConstructs: List<String> = emptyList(),
    @SerialName("geography") val geography: String = "India Only",
    @SerialName("availableTenures") val availableTenures: List<Int> = listOf(1, 2, 3, 4, 5),
    @SerialName("gradeRefs") val gradeRefs: List<String> = emptyList(),
    @SerialName("eligibilityRef") val eligibilityRef: String? = null,
    @SerialName("sectionRefs") val sectionRefs: List<String> = emptyList(),
    /** Lower SI bound (Indemnity "Sum Insured: Rs 10,000 to Rs 300 Lacs" → min). */
    @SerialName("minSumInsured") val minSumInsured: Money = Money.ZERO,
    /** Upper SI bound (Indemnity "Sum Insured" → max). */
    @SerialName("maxSumInsured") val maxSumInsured: Money = Money.ZERO,
    /** Premium-payment terms in years (Indemnity "Premium Payment Terms: [1/2/3/4/5] Years"). */
    @SerialName("premiumPaymentTerms") val premiumPaymentTerms: List<Int> = emptyList(),
    /** Premium-payment modes (Monthly / Quarterly / Half Yearly / Yearly / Single / Modular). */
    @SerialName("premiumPaymentModes") val premiumPaymentModes: List<String> = emptyList(),
    /** Entry-age boundary copy (Indemnity "Entry Age: 0 Years"). */
    @SerialName("entryAge") val entryAge: String = "",
    /** Exit/maximum-age boundary copy (Indemnity "Exit Age: Lifelong"). */
    @SerialName("exitAge") val exitAge: String = "",
    /** Free-text special conditions / endorsement copy (boundary-condition notes). */
    @SerialName("specialConditionsText") val specialConditionsText: String = "",
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
