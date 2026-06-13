package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a registered vendor supplies. */
@Serializable
enum class VendorCategory { SERVICE, PRODUCT, BOTH }

/** Documents collected during vendor KYC (from the New Vendor Registration form). */
@Serializable
enum class VendorDocType { PAN_CARD, GST_CERT, MSME_CERT, CANCELLED_CHEQUE, BANKERS_CERT }

/** Bank account details captured for vendor payouts (verified via cancelled cheque / banker's cert). */
@Serializable
data class VendorBank(
    @SerialName("accountNo") val accountNo: String = "",
    @SerialName("bankName") val bankName: String = "",
    @SerialName("bankAddress") val bankAddress: String = "",
    @SerialName("ifscCode") val ifscCode: String = "",
)

/**
 * A registered supplier/vendor — the GLOBAL (no productLine) entity behind the New Vendor
 * Registration / KYC form (sourced from New_Vendor_KYC_Form/KYC.csv).
 *
 * Captures the vendor's identity, contact, tax/registration ids, payout bank details and the
 * set of KYC documents required for onboarding. Unlike catalog entities this carries no
 * [com.rate.core.rating.ports.model.ProductLine] — vendors are shared across RETAIL and GROUP.
 */
@Serializable
data class Vendor(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("vendorName") val vendorName: String,
    @SerialName("officeAddress") val officeAddress: String = "",
    @SerialName("contactPerson") val contactPerson: String = "",
    @SerialName("contactPhoneOffice") val contactPhoneOffice: String = "",
    @SerialName("contactPhoneMobile") val contactPhoneMobile: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("website") val website: String = "",
    @SerialName("category") val category: VendorCategory = VendorCategory.SERVICE,
    @SerialName("panNumber") val panNumber: String = "",
    @SerialName("gstNumber") val gstNumber: String = "",
    /** Optional — only present when the vendor is MSME-registered. */
    @SerialName("msmeNumber") val msmeNumber: String? = null,
    @SerialName("bank") val bank: VendorBank = VendorBank(),
    /** KYC documents required/collected for this vendor's onboarding. */
    @SerialName("requiredDocuments") val requiredDocuments: List<VendorDocType> = emptyList(),
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
