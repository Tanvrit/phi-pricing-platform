package com.rate.aegis.customer.buyonline.api

import com.rate.domain.model.BuyOnlineSessionState
import com.rate.domain.model.Plan
import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Shared request/response DTOs (mirror server models) ──────────────────────

@Serializable
data class OtpResponse(val success: Boolean, val message: String = "", val token: String = "")

@Serializable
data class HospitalsResponse(val pincode: String, val hospitalsNearby: Int)

@Serializable
data class EligibilityRequest(
    val mobile: String,
    val hasPED: Boolean,
    val pedMembers: List<String>,
    val hasCriticalIllness: Boolean,
    val criticalIllnessMembers: List<String>,
    val members: List<String>
)

@Serializable
data class EligibilityResponse(val coveredMembers: List<String>, val uncoveredMembers: List<String>)

@Serializable
data class PremiumRequest(
    val sumInsured: Long,
    val tier: String,
    val tenure: Int,
    val addOnIds: List<String>,
    val primaryAge: Int = 35,
    val familyType: String = "1A",
    val zone: String = "Zone 1"
)

@Serializable
data class PremiumResponse(
    val annualPremium: Double,
    val monthlyPremium: Double,
    val gstAmount: Double = 0.0,
    val totalIncludingGst: Double = 0.0,
    val basePremium: Double = 0.0,
    val totalAddons: Double = 0.0,
    val totalDiscountAmount: Double = 0.0,
    val tenureDiscountRate: Double = 0.0
)

@Serializable
data class ProposalRequest(
    val mobile: String,
    val planTier: String,
    val sumInsured: Long,
    val annualPremium: Double,
    val tenure: Int,
    val kycMethod: String,
    val bankAccountNumber: String,
    val bankName: String,
    val ifscCode: String
)

@Serializable
data class ProposalResponse(
    val proposalNumber: String,
    val status: String,
    val planTier: String,
    val sumInsured: Long,
    val annualPremium: Double
)

/**
 * Mirror of the server's `QuoteDetailResponse` (see `QuoteRoutes.kt`). Same field
 * names so the deserialiser picks them up by-name; both halves are existing
 * `:shared` types so no DTO drift to maintain.
 */
@Serializable
data class QuoteDetailResponse(val request: QuoteRequest, val result: QuoteResult)

// ── Client ────────────────────────────────────────────────────────────────────

class BuyOnlineApiClient(private val baseUrl: String = "http://localhost:9090") {

    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    // OTP
    suspend fun sendOtp(mobile: String): OtpResponse =
        http.post("$baseUrl/api/buy-online/otp/send") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("mobile" to mobile))
        }.body()

    suspend fun verifyOtp(mobile: String, otp: String): OtpResponse =
        http.post("$baseUrl/api/buy-online/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("mobile" to mobile, "otp" to otp))
        }.body()

    // Pincode → hospital count
    suspend fun getHospitalsNearPincode(pincode: String): Int =
        http.get("$baseUrl/api/buy-online/hospitals") {
            parameter("pincode", pincode)
        }.body<HospitalsResponse>().hospitalsNearby

    // Eligibility
    suspend fun checkEligibility(req: EligibilityRequest): EligibilityResponse =
        http.post("$baseUrl/api/buy-online/eligibility") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    // Premium
    suspend fun calculatePremium(req: PremiumRequest): PremiumResponse =
        http.post("$baseUrl/api/buy-online/premium") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    // KYC OTP
    suspend fun sendKycOtp(mobile: String): OtpResponse =
        http.post("$baseUrl/api/buy-online/kyc/otp/send") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("mobile" to mobile))
        }.body()

    suspend fun verifyKycOtp(mobile: String, otp: String): OtpResponse =
        http.post("$baseUrl/api/buy-online/kyc/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("mobile" to mobile, "otp" to otp))
        }.body()

    // Proposal submission
    suspend fun submitProposal(req: ProposalRequest): ProposalResponse =
        http.post("$baseUrl/api/buy-online/proposal") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    // Save+resume — fire-and-forget save, null-tolerant load. The load path
    // wraps in runCatching so a stale `?session=` link (e.g. server lost the
    // row, network blip) falls back silently to a fresh journey rather than
    // failing the whole composition.
    suspend fun saveSession(state: BuyOnlineSessionState) {
        http.post("$baseUrl/api/buy-online/session") {
            contentType(ContentType.Application.Json)
            setBody(state)
        }
    }

    suspend fun loadSession(sessionId: String): BuyOnlineSessionState? = runCatching {
        http.get("$baseUrl/api/buy-online/session/$sessionId").body<BuyOnlineSessionState>()
    }.getOrNull()

    /**
     * Shared-quote lookup. Returns null on any failure (stale link, network
     * blip, server down) so the read-only summary view can render a friendly
     * "we couldn't find that quote" message rather than throwing into the
     * Compose tree. The server route is the same `/api/quotes/{id}` the
     * operator console hits — no new endpoint.
     */
    suspend fun getQuoteById(id: String): QuoteDetailResponse? = runCatching {
        http.get("$baseUrl/api/quotes/$id").body<QuoteDetailResponse>()
    }.getOrNull()

    /**
     * Resolve a plan id → full Plan for the shared-quote summary header.
     * Null-tolerant for the same reason as [getQuoteById]; the view falls
     * back to displaying the raw plan id if the lookup fails.
     */
    suspend fun getPlan(planId: String): Plan? = runCatching {
        http.get("$baseUrl/api/plans/$planId").body<Plan>()
    }.getOrNull()

    fun close() = http.close()
}
