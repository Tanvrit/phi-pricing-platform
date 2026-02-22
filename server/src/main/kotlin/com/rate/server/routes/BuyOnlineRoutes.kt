package com.rate.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.random.Random

// ── Generic error / message response ─────────────────────────────────────────

@Serializable internal data class BuyOnlineErrorResponse(val error: String)
@Serializable internal data class BuyOnlineMessageResponse(val message: String)

// ── Request / Response models ─────────────────────────────────────────────────

@Serializable data class OtpRequest(val mobile: String)
@Serializable data class OtpVerifyRequest(val mobile: String, val otp: String)
@Serializable data class OtpResponse(val success: Boolean, val message: String = "", val token: String = "")
@Serializable data class HospitalsResponse(val pincode: String, val hospitalsNearby: Int)
@Serializable data class PincodeRequest(val pincode: String)

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
    val addOnIds: List<String>
)

@Serializable
data class PremiumResponse(val annualPremium: Double, val monthlyPremium: Double)

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

@Serializable
data class ProposalTrackResponse(val proposalNumber: String, val status: String, val message: String)

// ── Route definitions ─────────────────────────────────────────────────────────

fun Route.buyOnlineRoutes() {
    route("/api/buy-online") {

        // OTP
        post("/otp/send") {
            val req = call.receive<OtpRequest>()
            if (req.mobile.length < 10) {
                call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse("Invalid mobile number"))
                return@post
            }
            call.respond(OtpResponse(success = true, message = "OTP sent to ${req.mobile}"))
        }

        post("/otp/verify") {
            val req = call.receive<OtpVerifyRequest>()
            val valid = req.otp.length == 4 && req.otp.all(Char::isDigit)
            if (!valid) {
                call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse("Invalid OTP"))
            } else {
                call.respond(OtpResponse(success = true, token = "mock-jwt-${req.mobile}"))
            }
        }

        // Pincode / hospital lookup
        get("/hospitals") {
            val pincode = call.request.queryParameters["pincode"] ?: ""
            if (pincode.length != 6) {
                call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse("Invalid pincode"))
                return@get
            }
            call.respond(HospitalsResponse(pincode = pincode, hospitalsNearby = Random.nextInt(10, 50)))
        }

        // Eligibility check
        post("/eligibility") {
            val req = call.receive<EligibilityRequest>()
            val uncovered = req.members.filter { member ->
                (req.hasPED && member in req.pedMembers) ||
                (req.hasCriticalIllness && member in req.criticalIllnessMembers)
            }
            val covered = req.members.filter { it !in uncovered }
            call.respond(EligibilityResponse(covered, uncovered))
        }

        // Premium calculation
        post("/premium") {
            val req = call.receive<PremiumRequest>()
            val base = when (req.sumInsured) {
                1_000_000L  -> 31_248.0
                2_500_000L  -> 35_248.0
                5_000_000L  -> 52_000.0
                10_000_000L -> 60_000.0
                else        -> 31_248.0
            }
            val tierMult = when (req.tier.uppercase()) {
                "PREMIER"   -> 1.0
                "SIGNATURE" -> 1.25
                "GLOBAL"    -> 1.80
                else        -> 1.0
            }
            val tenureDiscounts = mapOf(1 to 0.0, 2 to 0.075, 3 to 0.10, 4 to 0.125, 5 to 0.15)
            val discount   = tenureDiscounts[req.tenure] ?: 0.0
            val addOnCosts = mapOf("maternity" to 2000.0, "dental" to 1500.0, "vision" to 2000.0)
            val addOnTotal = req.addOnIds.sumOf { addOnCosts[it] ?: 0.0 }
            val annual     = base * tierMult * (1 - discount) + addOnTotal
            call.respond(PremiumResponse(annualPremium = annual, monthlyPremium = annual / 12))
        }

        // KYC OTP
        post("/kyc/otp/send") {
            val req = call.receive<OtpRequest>()
            call.respond(OtpResponse(success = true, message = "KYC OTP sent to XXXXXX${req.mobile.takeLast(4)}"))
        }

        post("/kyc/otp/verify") {
            val req = call.receive<OtpVerifyRequest>()
            val valid = req.otp.length == 6 && req.otp.all(Char::isDigit)
            call.respond(OtpResponse(success = valid, message = if (valid) "Verified" else "Invalid OTP"))
        }

        // Submit proposal
        post("/proposal") {
            val req = call.receive<ProposalRequest>()
            val proposalNumber = "PHI${Random.nextInt(1_000_000, 9_999_999)}"
            call.respond(
                HttpStatusCode.Created,
                ProposalResponse(
                    proposalNumber = proposalNumber,
                    status         = "Under Review",
                    planTier       = req.planTier,
                    sumInsured     = req.sumInsured,
                    annualPremium  = req.annualPremium
                )
            )
        }

        // Track proposal
        get("/proposal/{proposalNumber}") {
            val num = call.parameters["proposalNumber"] ?: throw IllegalArgumentException("Missing proposal number")
            call.respond(ProposalTrackResponse(proposalNumber = num, status = "Under Review",
                message = "Your proposal is being reviewed. We'll contact you within 2 business days."))
        }
    }
}
