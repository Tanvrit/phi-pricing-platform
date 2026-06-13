package com.rate.core.auth.kyc

import kotlinx.serialization.Serializable

/**
 * KYC channel chosen by the customer. Relocated from the buy-online `KycMethod`
 * (CKYC/EKYC/MANUAL) into the auth core with the canonical re-arch names.
 *
 *  - [C_KYC]: Central KYC lookup via PAN + DOB.
 *  - [E_KYC]: Aadhaar + DigiLocker OTP (uses an [com.rate.core.auth.otp.OtpPurpose.KYC]
 *    6-digit OTP).
 *  - [MANUAL]: customer uploads identity + address proof documents for review.
 */
@Serializable
enum class KycMethod(val displayName: String, val description: String) {
    C_KYC("C-KYC", "PAN and DOB based central-KYC verification"),
    E_KYC("E-KYC", "Aadhaar and OTP based KYC via DigiLocker"),
    MANUAL("Manual KYC", "Upload identity and address proof documents"),
}

/** Lifecycle of a KYC submission. */
@Serializable
enum class KycStatus { PENDING, VERIFIED, REJECTED }
