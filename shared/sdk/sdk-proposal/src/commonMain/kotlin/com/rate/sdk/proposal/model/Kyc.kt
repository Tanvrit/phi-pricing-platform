package com.rate.sdk.proposal.model

import com.rate.core.auth.kyc.KycMethod
import com.rate.core.auth.kyc.KycStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reference to a KYC document the customer uploaded (MANUAL flow) or that the regulated
 * channel returned (C-KYC / E-KYC). Only handles/identifiers are stored — never the raw
 * document bytes (those live in object storage, referenced by [storageRef]).
 *
 * SECURITY: full Aadhaar / PAN numbers never land here; identity-proof documents are
 * referenced by an opaque [storageRef] and the regulated channel carries the sensitive
 * payload out-of-band.
 */
@Serializable
data class KycDocRef(
    /** Document kind, e.g. "PAN", "AADHAAR", "ADDRESS_PROOF", "PHOTO". */
    @SerialName("docType") val docType: String,
    /** Opaque object-storage handle for the uploaded file (null for channel-returned refs). */
    @SerialName("storageRef") val storageRef: String? = null,
    /** Original filename for operator display (MANUAL uploads). */
    @SerialName("fileName") val fileName: String? = null,
    @SerialName("uploadedAt") val uploadedAt: Instant? = null,
)

/**
 * The KYC state captured on a proposal. Wraps the chosen [com.rate.core.auth.kyc.KycMethod]
 * (C-KYC / E-KYC / MANUAL) and its outcome ([KycStatus]) together with the non-sensitive
 * collected identifiers and any uploaded document refs.
 *
 * Relocated/repackaged from the buy-online journey's per-method KYC handling (the monolith
 * had `KycMethod` as a buy-online enum and scattered fields). The full Aadhaar number is
 * never stored — only [aadhaarLast4] for reconciliation; the regulated E-KYC channel
 * carries the full number out-of-band.
 *
 *  - C_KYC  → [pan] + [dob] resolve a [ckycNumber] (the referenceId).
 *  - E_KYC  → [aadhaarLast4] + a 6-digit OTP step (verified via [com.rate.sdk.proposal.handler.OtpHandler]).
 *  - MANUAL → [documents] (uploaded identity + address proof handles).
 */
@Serializable
data class KycState(
    @SerialName("method") val method: KycMethod = KycMethod.C_KYC,
    @SerialName("status") val status: KycStatus = KycStatus.PENDING,
    @SerialName("pan") val pan: String? = null,
    /** YYYY-MM-DD string (kept as String for wire parity; validated, not parsed, here). */
    @SerialName("dob") val dob: String? = null,
    @SerialName("aadhaarLast4") val aadhaarLast4: String? = null,
    /** Channel tracking id — CKYC number, DigiLocker txn id, or manual upload batch id. */
    @SerialName("referenceId") val referenceId: String? = null,
    @SerialName("documents") val documents: List<KycDocRef> = emptyList(),
    @SerialName("verifiedAt") val verifiedAt: Instant? = null,
) {
    val isVerified: Boolean get() = status == KycStatus.VERIFIED
}
