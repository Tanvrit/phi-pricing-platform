package com.rate.sdk.proposal.network

import com.rate.core.rating.ports.model.Member
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the buy-online journey client surface. RELOCATED from the monolith's
 * `BuyOnlineRoutes` request/response data classes, with the JSON field names preserved so the
 * existing client/server wire contract is unchanged (the WASM journey + operator console keep
 * working). Where a journey contract in [com.rate.sdk.proposal.model.journey] already IS the wire
 * shape, the API uses it directly; these DTOs cover the request/response views that don't map 1:1.
 */

// ── OTP ──────────────────────────────────────────────────────────────────────

/** POST /api/buy-online/otp/send and /kyc/otp/send body. */
@Serializable
data class OtpSendRequest(
    @SerialName("mobile") val mobile: String,
)

/** POST /api/buy-online/otp/verify and /kyc/otp/verify body. */
@Serializable
data class OtpVerifyRequest(
    @SerialName("mobile") val mobile: String,
    @SerialName("otp") val otp: String,
)

/** Response of the OTP send/verify endpoints (monolith's `OtpResponse`). */
@Serializable
data class OtpResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String = "",
    @SerialName("token") val token: String = "",
)

// ── Proposal ───────────────────────────────────────────────────────────────────

/**
 * POST /api/buy-online/proposal body (monolith's `ProposalRequest`). Field names preserved; the
 * server maps this onto [com.rate.sdk.proposal.handler.ProposalSubmission] before persisting.
 */
@Serializable
data class ProposalSubmitRequest(
    @SerialName("mobile") val mobile: String,
    @SerialName("planTier") val planTier: String,
    @SerialName("sumInsured") val sumInsured: Long,
    @SerialName("annualPremium") val annualPremium: Double,
    @SerialName("totalIncludingGst") val totalIncludingGst: Double = 0.0,
    @SerialName("tenure") val tenure: Int = 1,
    @SerialName("quoteRef") val quoteRef: String? = null,
    @SerialName("kycMethod") val kycMethod: String = "",
    @SerialName("bankAccountNumber") val bankAccountNumber: String = "",
    @SerialName("bankName") val bankName: String = "",
    @SerialName("ifscCode") val ifscCode: String = "",
    @SerialName("accountHolderName") val accountHolderName: String = "",
    @SerialName("selectedAddOnIds") val selectedAddOnIds: List<String> = emptyList(),
    @SerialName("members") val members: List<Member> = emptyList(),
)

/** Response of POST /api/buy-online/proposal (monolith's `ProposalResponse`). */
@Serializable
data class ProposalSubmitResponse(
    @SerialName("proposalNumber") val proposalNumber: String,
    @SerialName("status") val status: String,
    @SerialName("planTier") val planTier: String,
    @SerialName("sumInsured") val sumInsured: Long,
    @SerialName("annualPremium") val annualPremium: Double,
)

/** Response of GET /api/buy-online/proposal/{proposalNumber} (monolith's `ProposalTrackResponse`). */
@Serializable
data class ProposalTrackResponse(
    @SerialName("proposalNumber") val proposalNumber: String,
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
)

// ── Generic error / message envelopes (monolith parity) ────────────────────────

@Serializable
data class BuyOnlineErrorResponse(
    @SerialName("errorCode") val errorCode: String,
    @SerialName("message") val message: String,
)

@Serializable
data class BuyOnlineMessageResponse(
    @SerialName("message") val message: String,
)
