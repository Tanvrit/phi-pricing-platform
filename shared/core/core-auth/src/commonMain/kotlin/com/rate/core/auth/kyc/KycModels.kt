package com.rate.core.auth.kyc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire request to initiate / submit KYC. Only the fields relevant to [method] are
 * populated:
 *  - C_KYC  → [pan] + [dob]
 *  - E_KYC  → [aadhaarLast4] then a 6-digit OTP step (verified separately)
 *  - MANUAL → [documentRefs] (uploaded-document handles)
 *
 * SECURITY: full Aadhaar numbers never travel here — only the last 4 digits for
 * reconciliation; the regulated channel carries the full number out-of-band.
 */
@Serializable
data class KycRequest(
    @SerialName("subject") val subject: String,
    @SerialName("method") val method: KycMethod,
    @SerialName("pan") val pan: String? = null,
    @SerialName("dob") val dob: String? = null,
    @SerialName("aadhaarLast4") val aadhaarLast4: String? = null,
    @SerialName("documentRefs") val documentRefs: List<String> = emptyList(),
)

/** Wire request to verify the E-KYC OTP step of a DigiLocker flow. */
@Serializable
data class KycOtpVerifyRequest(
    @SerialName("subject") val subject: String,
    @SerialName("code") val code: String,
)

/**
 * Wire result of a KYC submission / verification. [referenceId] is the channel's
 * tracking id (CKYC number, DigiLocker txn id, or upload batch id); [token] is an
 * optional short-lived bearer attesting the KYC was completed, to be presented on the
 * proposal-finalise call.
 */
@Serializable
data class KycResult(
    @SerialName("subject") val subject: String,
    @SerialName("method") val method: KycMethod,
    @SerialName("status") val status: KycStatus,
    @SerialName("referenceId") val referenceId: String? = null,
    @SerialName("token") val token: String? = null,
    @SerialName("message") val message: String? = null,
)
