package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.regulatory.CoverageLogic
import com.rate.core.regulatory.PayoutType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a cover's premium is derived by the rating engine. Relocated from the monolith's
 * implicit per-row classification (Excel rows 7-70) into explicit catalog metadata so the
 * engine can dispatch on data, not hardcoded row numbers.
 *
 * - [PERCENT_MULTIPLIER] accumulating-% cover: rate × an accumulation base (rows 7-30, 52-58).
 * - [FLAT] fixed rupee amount per policy/year (e.g. Air Ambulance ₹433).
 * - [MEMBER_LEVEL] rate looked up per member age-band (e.g. Personal Accident, Critical Illness).
 * - [POST] post-calculation cover referencing forward rows (Prudential Healthy, Premium Return).
 * - [UW_LOADING] underwriting loading applied over an accumulated base (row 59).
 * - [DISCOUNT] reduces premium (Smart Select, Co-Pay, deductibles, NRI/employee discounts).
 */
@Serializable
enum class RateKind { PERCENT_MULTIPLIER, FLAT, MEMBER_LEVEL, POST, UW_LOADING, DISCOUNT }

/**
 * How a [CoverOption]'s value is interpreted when the operator/customer picks it.
 *
 * - [FIXED_AMOUNT] a literal rupee figure (use [CoverOption.minLimit]/[CoverOption.maxLimit]).
 * - [PERCENT_OF_SI] a percentage of the Sum Insured (e.g. PBT "x% of SI" templated rates).
 * - [COUNT] a plain integer (days, months, members, sessions…).
 */
@Serializable
enum class CoverParamType { FIXED_AMOUNT, PERCENT_OF_SI, COUNT }

/**
 * A selectable parameter option for a cover (the customer/operator picks one). Mirrors the
 * Excel/PBT "options" and the buy-online ParamDef.options — but typed with regulatory
 * [CoverageLogic] and optional rupee limits.
 */
@Serializable
data class CoverOption(
    @SerialName("label") val label: String,
    @SerialName("minLimit") val minLimit: Money? = null,
    @SerialName("maxLimit") val maxLimit: Money? = null,
    @SerialName("details") val details: String = "",
    @SerialName("logic") val logic: CoverageLogic = CoverageLogic.PART_OF_BASE_SI,
    /** Engine param key (param1) this option maps to, when distinct from [label]. */
    @SerialName("paramKey") val paramKey: String? = null,
    /** How this option's value is interpreted (amount / % of SI / plain count). */
    @SerialName("paramType") val paramType: CoverParamType = CoverParamType.FIXED_AMOUNT,
    /** Free-text range / wording when limits are not a single Money (e.g. "₹1L - ₹5L"). */
    @SerialName("rangeText") val rangeText: String? = null,
)

/**
 * A categorised group of [CoverOption]s for covers whose options come in sets
 * (e.g. waiting-period categories, room-rent tiers). [exclusive] = pick at most one.
 */
@Serializable
data class CoverOptionGroup(
    @SerialName("category") val category: String,
    @SerialName("exclusive") val exclusive: Boolean = true,
    @SerialName("options") val options: List<CoverOption> = emptyList(),
)

/**
 * A named sub-limit on a cover (e.g. EMI "upto 12 months", room-rent cap, modern-treatment cap).
 * Relocated from the D1-Loan EMI "Sublimit/ Options" column and HC max-payable-duration options.
 */
@Serializable
data class Sublimit(
    @SerialName("label") val label: String,
    @SerialName("limit") val limit: Money? = null,
    @SerialName("details") val details: String = "",
)

/**
 * A Cover / Benefit — the atomic admin-CRUD unit of the catalog and the engine's rate-row.
 *
 * Relocated from the monolith's `CoverDefinitions.CoverIds` + `CoverCatalog.CoverMeta`
 * (display copy) + the implicit Excel row classification (rate behaviour). [code] is the
 * stable engine id (e.g. "personal_accident") referenced by [com.rate.core.rating.ports.model.CoverSelection].
 */
@Serializable
data class Cover(
    @SerialName("_id") override val id: String = newId(),
    /** Stable engine/business code — the CoverSelection.coverId the engine looks up. */
    @SerialName("code") val code: String,
    /** Owning [Section] id. */
    @SerialName("sectionRef") val sectionRef: String = "",
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    @SerialName("name") val name: String,
    /** Short benefit description (customer-facing copy). */
    @SerialName("description") val description: String = "",
    /** What triggers the payout (e.g. "Hospitalisation", "Diagnosis of CI", "Accident"). */
    @SerialName("trigger") val trigger: String = "",
    /** Full IRDAI coverage wording / cover-description text. */
    @SerialName("coverageText") val coverageText: String = "",
    @SerialName("minSumInsured") val minSumInsured: Money = Money.ZERO,
    @SerialName("maxSumInsured") val maxSumInsured: Money = Money.ZERO,
    @SerialName("payoutType") val payoutType: PayoutType = PayoutType.INDEMNITY,
    @SerialName("rateKind") val rateKind: RateKind = RateKind.PERCENT_MULTIPLIER,
    @SerialName("options") val options: List<CoverOption> = emptyList(),
    /** Optional second-parameter options (e.g. maternity waiting-period). */
    @SerialName("optionsParam2") val optionsParam2: List<CoverOption> = emptyList(),
    /** Reference to a [CriticalIllnessList] when this cover is CI-list driven. */
    @SerialName("criticalIllnessListRef") val criticalIllnessListRef: String? = null,
    /** Referenced [Annexure] ids (day-care list, consumables list, exclusions…). */
    @SerialName("annexureRefs") val annexureRefs: List<String> = emptyList(),
    /** Ordered cover ids whose year-premium sums to this cover's multiplication base
     *  (PERCENT_MULTIPLIER / UW_LOADING / POST only). Relocated COVER_ACCUM_BASES. */
    @SerialName("accumBase") val accumBase: List<String> = emptyList(),
    /** True for discount-style covers (premium reducers). */
    @SerialName("isDiscount") val isDiscount: Boolean = false,
    @SerialName("displayOrder") val displayOrder: Int = 0,
    /** Excel display-name variants used by the ingestion importer to match a sheet row. */
    @SerialName("importAliases") val importAliases: List<String> = emptyList(),
    /** "Percentage of SI" (PBT PA col 5) — e.g. 0.5 means "0.5% of SI"; 0.0 if not %-of-SI driven. */
    @SerialName("percentOfSI") val percentOfSI: Double = 0.0,
    /** "Change from last TCS signed version" audit note (D1/D2 last column): "New Addition", "No Change", "Value Change …". */
    @SerialName("changeNote") val changeNote: String = "",
    /** CI survival-period options ("7 days", "15 days", …, "Waived"), extracted from CI coverage text. */
    @SerialName("survivalPeriod") val survivalPeriod: List<String> = emptyList(),
    /** Named sub-limits (D1-Loan EMI "Sublimit/ Options", HC max-payable, modern-treatment caps). */
    @SerialName("sublimits") val sublimits: List<Sublimit> = emptyList(),
    /** Hospital-Cash deductible-day options ("0", "1", "2", "3", "5" days). */
    @SerialName("deductibleOptions") val deductibleOptions: List<String> = emptyList(),
    /** Hospital-Cash max-payable-duration options ("5", "10", "30", "60", "90", "180" days). */
    @SerialName("maxPayableDuration") val maxPayableDuration: List<String> = emptyList(),
    /** Categorised option groups (waiting-period categories, room-rent tiers, etc.). */
    @SerialName("optionGroups") val optionGroups: List<CoverOptionGroup> = emptyList(),
    /** Initial waiting-period options ("30 days", "Nil", …). */
    @SerialName("initialWaitingOptions") val initialWaitingOptions: List<String> = emptyList(),
    /** Specific/named-ailment waiting-period options ("1 year", "2 years", …). */
    @SerialName("specificWaitingOptions") val specificWaitingOptions: List<String> = emptyList(),
    /** Pre-existing-disease waiting-period options ("2 years", "3 years", "4 years", …). */
    @SerialName("pedWaitingOptions") val pedWaitingOptions: List<String> = emptyList(),
    /** Maternity waiting-period options ("9 months", "2 years", …). */
    @SerialName("maternityWaitingOptions") val maternityWaitingOptions: List<String> = emptyList(),
    /** Daily-hospitalization-cash limit options ("₹1000", "₹2000", …). */
    @SerialName("dailyHospitalizationLimitOptions") val dailyHospitalizationLimitOptions: List<String> = emptyList(),
    /** ICU daily-cash multiplier options ("1x", "2x", …). */
    @SerialName("icuMultiplierOptions") val icuMultiplierOptions: List<String> = emptyList(),
    /** Franchise (waiting-day) options before a daily-cash benefit triggers. */
    @SerialName("franchiseOptions") val franchiseOptions: List<String> = emptyList(),
    /** Payout-linkage bases ("per day", "per claim", "per policy", "per member"). */
    @SerialName("payoutLinkageBases") val payoutLinkageBases: List<String> = emptyList(),
    /** EMI-payment options for EMI-linked benefits ("6 months", "12 months", …). */
    @SerialName("emiPaymentOptions") val emiPaymentOptions: List<String> = emptyList(),
    /** Member categories this cover applies to ("Employee", "Spouse", "Child", "Parent", …). */
    @SerialName("coveredMemberCategories") val coveredMemberCategories: List<String> = emptyList(),
    /** Reference to a PPD/PTD (permanent partial/total disablement) payout table, when applicable. */
    @SerialName("ppdPtdTableRef") val ppdPtdTableRef: String? = null,
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

/** Alias — the PBT/GROUP world calls these "Benefits"; same entity. */
typealias Benefit = Cover
