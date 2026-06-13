package com.rate.sdk.ingestion.network

import com.rate.core.rating.ports.model.ProductLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the ingestion client surface. [com.rate.sdk.ingestion.model.RateMeta] and
 * [com.rate.sdk.ingestion.model.ImportSummary] are already wire-serializable (they ARE the JSON
 * contract); these DTOs wrap the request bodies that don't map 1:1 to one persisted type.
 */

/** Request to promote a previously-imported rate version to active. */
@Serializable
data class ActivateVersionRequest(
    @SerialName("version") val version: String,
    @SerialName("productLine") val productLine: ProductLine = ProductLine.RETAIL,
)

/**
 * Request to (re-)seed catalog entities from raw CSV source texts. Each field is the CSV content of
 * one `/data` file (null = not provided). The server parses + seeds; [force] re-imports an already
 * populated collection (CSV becomes authoritative), else it's a first-boot-only seed.
 *
 * Mirrors [com.rate.sdk.ingestion.handler.CatalogSourceBundle] but as a serializable wire body.
 *
 * [sectionBenefitCsvs] carries the per-section benefit files (D1-PA / D1-CI / D1-HC / D1-Loan EMI /
 * D2-OPD / PBT-PA) whose rows become [com.rate.sdk.catalog.model.Cover]s linked to the named Section.
 */
@Serializable
data class SeedCatalogRequest(
    @SerialName("productLine") val productLine: ProductLine = ProductLine.GROUP,
    @SerialName("force") val force: Boolean = false,
    @SerialName("pbtIndexCsv") val pbtIndexCsv: String? = null,
    @SerialName("groupProductCsv") val groupProductCsv: String? = null,
    @SerialName("baseBenefitScheduleCsv") val baseBenefitScheduleCsv: String? = null,
    @SerialName("flatCiListCsv") val flatCiListCsv: String? = null,
    @SerialName("flatCiListCode") val flatCiListCode: String = "CI_101",
    @SerialName("tieredCiListCsv") val tieredCiListCsv: String? = null,
    @SerialName("waitingPeriodsCsv") val waitingPeriodsCsv: String? = null,
    @SerialName("eligibilityCsv") val eligibilityCsv: String? = null,
    @SerialName("ppdPtdCsv") val ppdPtdCsv: String? = null,
    @SerialName("dayCareCsv") val dayCareCsv: String? = null,
    @SerialName("consumablesCsv") val consumablesCsv: String? = null,
    @SerialName("healthCheckupCsv") val healthCheckupCsv: String? = null,
    @SerialName("annexureCsv") val annexureCsv: String? = null,
    @SerialName("chronicOpdCsv") val chronicOpdCsv: String? = null,
    /** New Vendor Registration / KYC form (`New_Vendor_KYC_Form/KYC.csv`) → one global [Vendor]. */
    @SerialName("vendorKycCsv") val vendorKycCsv: String? = null,
    /** GROUP EE annexure "Sublimits …" table → [com.rate.sdk.catalog.model.group.SurgicalSublimit]s. */
    @SerialName("surgicalSublimitCsv") val surgicalSublimitCsv: String? = null,
    /** GROUP EE annexure "Adult Vaccination List" → a [com.rate.sdk.catalog.model.group.VaccinationCatalog]. */
    @SerialName("vaccinationCatalogCsv") val vaccinationCatalogCsv: String? = null,
    /** GROUP EE annexure "List of Monitoring / Medical Devices" → a [com.rate.sdk.catalog.model.group.MedicalDeviceCatalog]. */
    @SerialName("medicalDeviceCatalogCsv") val medicalDeviceCatalogCsv: String? = null,
    @SerialName("sectionBenefitCsvs") val sectionBenefitCsvs: List<SectionBenefitInput> = emptyList(),
)

/**
 * One per-section benefit CSV file. [sectionNumber] is the PBT section number ("1", "2", "4", "9"…)
 * the parsed covers are linked to; [name] is the human label; [csv] is the raw file text.
 */
@Serializable
data class SectionBenefitInput(
    @SerialName("sectionNumber") val sectionNumber: String,
    @SerialName("name") val name: String,
    @SerialName("csv") val csv: String,
)
