package com.rate.sdk.ingestion.model

import com.rate.core.rating.ports.model.ProductLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire result of an ingestion run — returned by the parse/seed handlers and the
 * [com.rate.sdk.ingestion.network.IngestionApi] upload endpoints.
 *
 * Carries per-entity / per-rate-kind counts, any non-fatal [warnings] (a row that could not be
 * mapped is skipped, not failed), fatal [errors] (which set [ok] = false), and — when a rate
 * batch was written — the resulting [version] + [sourceFileSha256] so the caller can confirm
 * activation or detect a dedupe no-op via [deduped].
 *
 * Relocated from the monolith's `server/import` pipeline, which printed counts to stdout and
 * returned ad-hoc maps; here the result is a typed, serializable contract.
 */
@Serializable
data class ImportSummary(
    @SerialName("ok") val ok: Boolean = true,
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
    /** Rate-batch version this run produced/targeted (blank for catalog-only runs). */
    @SerialName("version") val version: String = "",
    @SerialName("sourceFileName") val sourceFileName: String = "",
    @SerialName("sourceFileSha256") val sourceFileSha256: String = "",
    /** True when an identical file was already imported and the run short-circuited. */
    @SerialName("deduped") val deduped: Boolean = false,
    /** Rows/entities written, keyed by kind (see [Counts]). */
    @SerialName("counts") val counts: Map<String, Int> = emptyMap(),
    /** Rows/entities skipped (unmappable but non-fatal), keyed by kind. */
    @SerialName("skipped") val skipped: Map<String, Int> = emptyMap(),
    @SerialName("warnings") val warnings: List<String> = emptyList(),
    @SerialName("errors") val errors: List<String> = emptyList(),
) {
    /** Total entities/rows written across all kinds. */
    val totalWritten: Int get() = counts.values.sum()

    companion object {
        /** Stable count keys — kept here so producers/consumers agree on the map keys. */
        object Counts {
            const val PRODUCTS = "products"
            const val SECTIONS = "sections"
            const val COVERS = "covers"
            const val CRITICAL_ILLNESS_LISTS = "criticalIllnessLists"
            const val ANNEXURES = "annexures"
            const val ADD_ONS = "addOns"
            const val GROUP_PRODUCT_CONFIGS = "groupProductConfigs"
            const val GROUP_GRADES = "groupGrades"
            const val BENEFIT_SCHEDULES = "benefitSchedules"
            const val WAITING_PERIODS = "waitingPeriods"
            const val ELIGIBILITY_CRITERIA = "eligibilityCriteria"
            const val PPD_PTD_TABLES = "ppdPtdTables"
            const val DAY_CARE_PROCEDURES = "dayCareProcedures"
            const val CONSUMABLES_LISTS = "consumablesLists"
            const val HEALTH_CHECKUP_PACKAGES = "healthCheckupPackages"
            const val CHRONIC_OPD_GRIDS = "chronicOpdGrids"

            // Rate-row kinds.
            const val BASE_RATES = "baseRates"
            const val COVER_RATES = "coverRates"
            const val MEMBER_LEVEL_RATES = "memberLevelRates"
            const val DISCOUNT_RATES = "discountRates"
            const val INSTALMENT_CONFIG = "instalmentConfig"
            const val COVER_AVAILABILITY = "coverAvailability"
        }

        fun failed(productLine: ProductLine, vararg errors: String): ImportSummary =
            ImportSummary(ok = false, productLine = productLine, errors = errors.toList())

        fun dedupedNoOp(
            productLine: ProductLine,
            version: String,
            sha: String,
            fileName: String,
        ): ImportSummary = ImportSummary(
            ok = true,
            productLine = productLine,
            version = version,
            sourceFileName = fileName,
            sourceFileSha256 = sha,
            deduped = true,
        )
    }
}
