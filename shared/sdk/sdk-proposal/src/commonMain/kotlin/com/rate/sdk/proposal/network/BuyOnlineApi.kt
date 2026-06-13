package com.rate.sdk.proposal.network

import com.rate.core.base.json.AppJson
import com.rate.sdk.proposal.model.BuyOnlineSessionState
import com.rate.sdk.proposal.model.journey.EligibilityRequest
import com.rate.sdk.proposal.model.journey.EligibilityResult
import com.rate.sdk.proposal.model.journey.HospitalLookupResult
import com.rate.sdk.proposal.model.journey.PremiumRequest
import com.rate.sdk.proposal.model.journey.PremiumResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * Ktor-client surface for the buy-online journey. The WASM customer journey + the JVM operator
 * console drive the whole flow (OTP → hospitals → eligibility → premium → KYC → proposal →
 * track + save/resume) through this — never the Mongo-backed repositories or the concrete engine
 * (those are server-only). All mutations are handled server-side by the handlers in
 * [com.rate.sdk.proposal.handler].
 *
 * The `/api/buy-online/...` paths are PRESERVED verbatim from the monolith's `BuyOnlineRoutes` so
 * the existing wire contract (and the WASM app already pointing at them) is unchanged.
 *
 * Pure-KMP: takes an [HttpClient] (engine supplied per platform by the app shell) + a base URL.
 */
class BuyOnlineApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    // ── OTP (login) ────────────────────────────────────────────────────────────
    suspend fun sendOtp(mobile: String): OtpResponse =
        client.post(url("/api/buy-online/otp/send")) {
            contentType(ContentType.Application.Json)
            setBody(OtpSendRequest(mobile))
        }.body()

    suspend fun verifyOtp(mobile: String, otp: String): OtpResponse =
        client.post(url("/api/buy-online/otp/verify")) {
            contentType(ContentType.Application.Json)
            setBody(OtpVerifyRequest(mobile, otp))
        }.body()

    // ── Pincode / hospital lookup ────────────────────────────────────────────────
    suspend fun hospitals(pincode: String): HospitalLookupResult =
        client.get(url("/api/buy-online/hospitals")) { parameter("pincode", pincode) }.body()

    // ── Eligibility ──────────────────────────────────────────────────────────────
    suspend fun eligibility(request: EligibilityRequest): EligibilityResult =
        client.post(url("/api/buy-online/eligibility")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // ── Premium (delegates server-side to the quoting engine) ─────────────────────
    suspend fun premium(request: PremiumRequest): PremiumResult =
        client.post(url("/api/buy-online/premium")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // ── KYC OTP (E-KYC / DigiLocker step) ─────────────────────────────────────────
    suspend fun sendKycOtp(mobile: String): OtpResponse =
        client.post(url("/api/buy-online/kyc/otp/send")) {
            contentType(ContentType.Application.Json)
            setBody(OtpSendRequest(mobile))
        }.body()

    suspend fun verifyKycOtp(mobile: String, otp: String): OtpResponse =
        client.post(url("/api/buy-online/kyc/otp/verify")) {
            contentType(ContentType.Application.Json)
            setBody(OtpVerifyRequest(mobile, otp))
        }.body()

    // ── Proposal submit + track ────────────────────────────────────────────────
    suspend fun submitProposal(request: ProposalSubmitRequest): ProposalSubmitResponse =
        client.post(url("/api/buy-online/proposal")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun trackProposal(proposalNumber: String): ProposalTrackResponse =
        client.get(url("/api/buy-online/proposal/$proposalNumber")).body()

    // ── Save + resume session ──────────────────────────────────────────────────
    suspend fun saveSession(state: BuyOnlineSessionState): BuyOnlineMessageResponse =
        client.post(url("/api/buy-online/session")) {
            contentType(ContentType.Application.Json)
            setBody(state)
        }.body()

    suspend fun loadSession(sessionId: String): BuyOnlineSessionState =
        client.get(url("/api/buy-online/session/$sessionId")).body()

    companion object {
        /** Install the frozen [AppJson] config on an HttpClientConfig's ContentNegotiation. */
        fun configureJson(config: HttpClientConfig<*>) {
            config.install(ContentNegotiation) { json(AppJson.json) }
        }
    }
}
