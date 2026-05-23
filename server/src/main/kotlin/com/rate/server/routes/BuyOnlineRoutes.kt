package com.rate.server.routes

import com.rate.domain.buyonline.BUYONLINE_ADDONS
import com.rate.domain.buyonline.BuyOnlineTier
import com.rate.domain.buyonline.toPlanId
import com.rate.domain.engine.PricingEngine
import com.rate.domain.model.*
import com.rate.domain.validation.ValidationResult
import com.rate.domain.validation.Validators
import com.rate.server.audit.AuditActor
import com.rate.server.audit.AuditEventService
import com.rate.server.auth.requireScope
import com.rate.server.database.repositories.BuyOnlineSessionRepository
import com.rate.server.database.repositories.SessionWithTimestamps
import com.rate.server.email.EmailMessage
import com.rate.server.email.EmailSender
import com.rate.server.metrics.Metrics
import com.rate.server.plugins.ACTOR_SUBJECT_KEY
import com.rate.server.plugins.REQUEST_ID_KEY
import com.rate.server.plugins.withIdempotency
import com.rate.server.security.IdempotencyService
import com.rate.server.security.OtpService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

// ── Generic error / message response ─────────────────────────────────────────
@Serializable internal data class BuyOnlineErrorResponse(val errorCode: String, val message: String)
@Serializable internal data class BuyOnlineMessageResponse(val message: String)

// ── Request / Response models ─────────────────────────────────────────────────
@Serializable data class OtpRequest(val mobile: String)
@Serializable data class OtpVerifyRequest(val mobile: String, val otp: String)
@Serializable data class OtpResponse(val success: Boolean, val message: String = "", val token: String = "")
@Serializable data class HospitalsResponse(val pincode: String, val hospitalsNearby: Int)

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
    val zone: String = "Zone 1",
    val members: List<Member> = emptyList()
)

@Serializable
data class PremiumResponse(
    val annualPremium: Double,
    val monthlyPremium: Double,
    val gstAmount: Double,
    val totalIncludingGst: Double,
    val basePremium: Double,
    val totalAddons: Double,
    val totalDiscountAmount: Double,
    val tenureDiscountRate: Double
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

@Serializable
data class ProposalTrackResponse(val proposalNumber: String, val status: String, val message: String)

// ── Redacted session DTO (Phase-2 PII mitigation) ────────────────────────────
// Wire-only projection of BuyOnlineSessionState. PII fields (mobile, pincode,
// pedMembers, criticalIllnessMembers) are irreversibly truncated/collapsed so
// the operator funnel still works without leaking customer-identifying data.
@Serializable
data class RedactedSession(
    val sessionId: String,           // truncated to first 8 chars + "…"
    val currentScreen: String,
    val mobileMasked: String,        // "XXXXXX1234" — last 4 only
    val pincodePrefix: String,       // first 3 digits + "XXX"
    val eldestAge: String,
    val kidsCount: Int,
    val hasPED: Boolean,
    val hasCriticalIllness: Boolean,
    // health-member lists redacted to counts only
    val pedMemberCount: Int,
    val criticalIllnessMemberCount: Int,
    val selectedTier: String,
    val selectedSumInsured: Long,
    val selectedTenure: Int,
    val selectedAddOnIds: List<String>,
    // DB-side timestamps used by Reports to compute time-to-completion.
    // `updatedAtIso` here is the server's `updated_at` column (NOT the
    // client-saved `state.updatedAtIso`), so funnel math sees a consistent
    // timeline even when client clocks drift.
    val createdAtIso: String,
    val updatedAtIso: String
)

private fun SessionWithTimestamps.redact(): RedactedSession {
    val s = state
    return RedactedSession(
        sessionId = s.sessionId.take(8) + (if (s.sessionId.length > 8) "…" else ""),
        currentScreen = s.currentScreen,
        mobileMasked = if (s.mobile.length >= 4) "X".repeat(s.mobile.length - 4) + s.mobile.takeLast(4) else "XXXX",
        pincodePrefix = if (s.pincode.length >= 3) s.pincode.take(3) + "XXX" else "XXX",
        eldestAge = s.eldestAge,
        kidsCount = s.kidsCount,
        hasPED = s.hasPED,
        hasCriticalIllness = s.hasCriticalIllness,
        pedMemberCount = s.pedMembers.size,
        criticalIllnessMemberCount = s.criticalIllnessMembers.size,
        selectedTier = s.selectedTier,
        selectedSumInsured = s.selectedSumInsured,
        selectedTenure = s.selectedTenure,
        selectedAddOnIds = s.selectedAddOnIds,
        createdAtIso = createdAt,
        updatedAtIso = updatedAt,
    )
}

// ── Email-self-link request (Phase-1 mock) ───────────────────────────────────
@Serializable internal data class SessionEmailRequest(val email: String, val url: String)

// ── Route definitions ─────────────────────────────────────────────────────────

private val buyOnlineJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private val log = LoggerFactory.getLogger("com.rate.server.routes.BuyOnline")

fun Route.buyOnlineRoutes(
    otpService: OtpService,
    engine: PricingEngine,
    auditService: AuditEventService,
    idempotencyService: IdempotencyService,
    sessionRepo: BuyOnlineSessionRepository,
    emailSender: EmailSender
) {
    route("/api/buy-online") {

        // ── OTP ──────────────────────────────────────────────────────────────
        post("/otp/send") {
            val req = call.receive<OtpRequest>()
            when (val r = Validators.mobile(req.mobile)) {
                is ValidationResult.Invalid -> {
                    call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse(r.errorCode, r.message))
                    return@post
                }
                ValidationResult.Ok -> {}
            }
            when (otpService.sendOtp(req.mobile, OtpService.Purpose.LOGIN)) {
                is OtpService.SendResult.Ok -> {
                    Metrics.recordOtpSent("login")
                    call.respond(OtpResponse(success = true, message = "OTP sent to ${maskMobile(req.mobile)}"))
                }
                OtpService.SendResult.RateLimited -> call.respond(
                    HttpStatusCode.TooManyRequests,
                    BuyOnlineErrorResponse("OTP_RATE_LIMIT", "Too many OTP requests; try again in 1 hour.")
                )
            }
        }

        post("/otp/verify") {
            val req = call.receive<OtpVerifyRequest>()
            if (Validators.mobile(req.mobile) is ValidationResult.Invalid) {
                call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse("MOBILE_INVALID", "Invalid mobile"))
                return@post
            }
            when (val r = otpService.verifyOtp(req.mobile, req.otp, OtpService.Purpose.LOGIN)) {
                is OtpService.VerifyResult.Ok -> {
                    Metrics.recordOtpVerified("login", "ok")
                    call.respond(OtpResponse(success = true, token = r.token))
                }
                is OtpService.VerifyResult.Mismatch -> {
                    Metrics.recordOtpVerified("login", "mismatch")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        BuyOnlineErrorResponse("OTP_INVALID", "Invalid OTP. ${r.remaining} attempts remaining.")
                    )
                }
                OtpService.VerifyResult.Expired -> {
                    Metrics.recordOtpVerified("login", "expired")
                    call.respond(
                        HttpStatusCode.Gone,
                        BuyOnlineErrorResponse("OTP_EXPIRED", "OTP expired. Please request a new one.")
                    )
                }
                OtpService.VerifyResult.TooManyAttempts -> {
                    Metrics.recordOtpVerified("login", "locked")
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        BuyOnlineErrorResponse("OTP_LOCKED", "Too many attempts. Please request a new OTP.")
                    )
                }
                OtpService.VerifyResult.NoActiveCode -> {
                    Metrics.recordOtpVerified("login", "not_found")
                    call.respond(
                        HttpStatusCode.NotFound,
                        BuyOnlineErrorResponse("OTP_NOT_FOUND", "No OTP active for this mobile.")
                    )
                }
            }
        }

        // ── Pincode / hospital lookup ────────────────────────────────────────
        get("/hospitals") {
            val pincode = call.request.queryParameters["pincode"] ?: ""
            when (val r = Validators.pincode(pincode)) {
                is ValidationResult.Invalid -> call.respond(
                    HttpStatusCode.BadRequest,
                    BuyOnlineErrorResponse(r.errorCode, r.message)
                )
                ValidationResult.Ok -> {
                    // Stable, deterministic count per pincode (no Random.nextInt anymore).
                    // Phase 2 replaces this with a real network-hospital lookup service.
                    val seed = pincode.toLong() % 50
                    call.respond(HospitalsResponse(pincode = pincode, hospitalsNearby = (10 + seed).toInt()))
                }
            }
        }

        // ── Eligibility check ────────────────────────────────────────────────
        post("/eligibility") {
            val req = call.receive<EligibilityRequest>()
            if (req.members.size > 20) {
                call.respond(HttpStatusCode.BadRequest,
                    BuyOnlineErrorResponse("MEMBERS_TOO_MANY", "Cannot evaluate more than 20 members."))
                return@post
            }
            val uncovered = req.members.filter { member ->
                (req.hasPED && member in req.pedMembers) ||
                (req.hasCriticalIllness && member in req.criticalIllnessMembers)
            }
            val covered = req.members.filter { it !in uncovered }
            call.respond(EligibilityResponse(covered, uncovered))
        }

        // ── Premium calculation (calls the REAL PricingEngine) ───────────────
        post("/premium") {
            val req = call.receive<PremiumRequest>()
            val tier = runCatching { BuyOnlineTier.valueOf(req.tier.uppercase()) }.getOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest,
                        BuyOnlineErrorResponse("TIER_INVALID",
                            "Tier must be one of ${BuyOnlineTier.entries.joinToString { it.name }}"))
                    return@post
                }
            val tenure = runCatching { Tenure.fromYears(req.tenure) }.getOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest,
                        BuyOnlineErrorResponse("TENURE_INVALID", "Tenure must be 1..5 years"))
                    return@post
                }
            if (req.sumInsured <= 0 || req.sumInsured > 100_000_000L) {
                call.respond(HttpStatusCode.BadRequest,
                    BuyOnlineErrorResponse("SI_INVALID", "Sum insured out of allowed range"))
                return@post
            }
            if (req.addOnIds.size > 50) {
                call.respond(HttpStatusCode.BadRequest,
                    BuyOnlineErrorResponse("ADDONS_TOO_MANY", "Too many add-ons selected"))
                return@post
            }

            // Map BuyOnline tier to actuarial plan + map add-on IDs to CoverSelections.
            val planId = tier.toPlanId()
            val addOnCovers = req.addOnIds.mapNotNull { id ->
                BUYONLINE_ADDONS.firstOrNull { it.id == id }?.let {
                    CoverSelection(it.coverId, it.defaultParam)
                }
            }

            val members = req.members.ifEmpty {
                listOf(Member(memberId = 1, age = req.primaryAge, relationship = "Self"))
            }
            val familyType = req.familyType.ifBlank { "1A" }

            // Tenure discount applies for SINGLE_PREMIUM only (per Excel rules). For ANNUAL
            // mode the engine ignores DISC_TENURE, so we don't include it here.
            val paymentMode = PaymentMode.ANNUAL
            val discounts = emptyList<DiscountSelection>()

            val quoteRequest = QuoteRequest(
                planId = planId,
                primaryAge = req.primaryAge,
                sumInsured = req.sumInsured,
                familyType = familyType,
                zone = req.zone,
                tenure = tenure,
                paymentMode = paymentMode,
                paymentTenure = tenure,
                members = members,
                selectedCovers = addOnCovers,
                selectedDiscounts = discounts,
                uwLoadingFactor = 0.0,
                maxDiscountCap = 0.30
            )

            val result = engine.calculate(quoteRequest)
            if (!result.isValid) {
                call.respond(HttpStatusCode.UnprocessableEntity, mapOf(
                    "errorCode" to "QUOTE_INVALID",
                    "validationErrors" to result.validationErrors,
                ))
                return@post
            }

            // Annual premium = totalAfterDiscount (single-year tenure for simplicity in BuyOnline).
            val annual = result.totalAfterDiscount
            call.respond(PremiumResponse(
                annualPremium = annual,
                monthlyPremium = annual / 12.0,
                gstAmount = result.gstAmount,
                totalIncludingGst = result.totalIncludingGst,
                basePremium = result.basePremiumTotal,
                totalAddons = result.totalAddons,
                totalDiscountAmount = result.totalDiscountAmount,
                tenureDiscountRate = 0.0
            ))
        }

        // ── KYC OTP ──────────────────────────────────────────────────────────
        post("/kyc/otp/send") {
            val req = call.receive<OtpRequest>()
            if (Validators.mobile(req.mobile) is ValidationResult.Invalid) {
                call.respond(HttpStatusCode.BadRequest, BuyOnlineErrorResponse("MOBILE_INVALID", "Invalid mobile"))
                return@post
            }
            when (otpService.sendOtp(req.mobile, OtpService.Purpose.KYC)) {
                is OtpService.SendResult.Ok -> {
                    Metrics.recordOtpSent("kyc")
                    call.respond(OtpResponse(success = true, message = "KYC OTP sent to ${maskMobile(req.mobile)}"))
                }
                OtpService.SendResult.RateLimited -> call.respond(
                    HttpStatusCode.TooManyRequests,
                    BuyOnlineErrorResponse("OTP_RATE_LIMIT", "Too many KYC OTP requests; try again in 1 hour.")
                )
            }
        }

        post("/kyc/otp/verify") {
            val req = call.receive<OtpVerifyRequest>()
            when (val r = otpService.verifyOtp(req.mobile, req.otp, OtpService.Purpose.KYC)) {
                is OtpService.VerifyResult.Ok -> {
                    Metrics.recordOtpVerified("kyc", "ok")
                    call.respond(OtpResponse(success = true, token = r.token))
                }
                is OtpService.VerifyResult.Mismatch -> {
                    Metrics.recordOtpVerified("kyc", "mismatch")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        BuyOnlineErrorResponse("OTP_INVALID", "Invalid KYC OTP. ${r.remaining} attempts remaining.")
                    )
                }
                OtpService.VerifyResult.Expired -> {
                    Metrics.recordOtpVerified("kyc", "expired")
                    call.respond(
                        HttpStatusCode.Gone,
                        BuyOnlineErrorResponse("OTP_EXPIRED", "KYC OTP expired.")
                    )
                }
                OtpService.VerifyResult.TooManyAttempts -> {
                    Metrics.recordOtpVerified("kyc", "locked")
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        BuyOnlineErrorResponse("OTP_LOCKED", "Too many KYC OTP attempts.")
                    )
                }
                OtpService.VerifyResult.NoActiveCode -> {
                    Metrics.recordOtpVerified("kyc", "not_found")
                    call.respond(
                        HttpStatusCode.NotFound,
                        BuyOnlineErrorResponse("OTP_NOT_FOUND", "No KYC OTP active.")
                    )
                }
            }
        }

        // ── Submit proposal (idempotent on Idempotency-Key header) ───────────
        post("/proposal") {
            withIdempotency(idempotencyService, routeKey = "POST /api/buy-online/proposal") { rawBody ->
                val req = buyOnlineJson.decodeFromString<ProposalRequest>(rawBody)
                val errors = mutableListOf<String>()
                (Validators.mobile(req.mobile) as? ValidationResult.Invalid)?.let { errors += it.message }
                (Validators.ifsc(req.ifscCode) as? ValidationResult.Invalid)?.let { errors += it.message }
                (Validators.accountNumber(req.bankAccountNumber) as? ValidationResult.Invalid)?.let { errors += it.message }
                if (errors.isNotEmpty()) {
                    @kotlinx.serialization.Serializable
                    data class V(val errorCode: String, val validationErrors: List<String>)
                    HttpStatusCode.BadRequest to buyOnlineJson.encodeToString(
                        V("PROPOSAL_INVALID", errors)
                    )
                } else {
                    val proposalNumber = "PHI-" + generateProposalSuffix()
                    val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
                    val actor = call.attributes.getOrNull(ACTOR_SUBJECT_KEY)
                        ?.let { AuditActor(subject = it) } ?: AuditActor.unknown()
                    auditService.record(
                        action = "proposal.created",
                        resourceType = "proposal",
                        resourceId = proposalNumber,
                        payload = JsonObject(mapOf(
                            "planTier" to JsonPrimitive(req.planTier),
                            "sumInsured" to JsonPrimitive(req.sumInsured),
                            "annualPremium" to JsonPrimitive(req.annualPremium),
                            "tenure" to JsonPrimitive(req.tenure),
                            "kycMethod" to JsonPrimitive(req.kycMethod)
                        )),
                        actor = actor,
                        requestId = rid
                    )
                    HttpStatusCode.Created to buyOnlineJson.encodeToString(
                        ProposalResponse(
                            proposalNumber = proposalNumber,
                            status         = "Under Review",
                            planTier       = req.planTier,
                            sumInsured     = req.sumInsured,
                            annualPremium  = req.annualPremium
                        )
                    )
                }
            }
        }

        // ── Save+resume session snapshot ─────────────────────────────────────
        // Client persists an opaque session id (sent via `?session=` URL param) and
        // POSTs the latest snapshot here on every meaningful state change. There's
        // no auth on these endpoints intentionally — the session id IS the bearer
        // (mirrors how payment-aggregator resume links work).
        post("/session") {
            val state = call.receive<BuyOnlineSessionState>()
            sessionRepo.save(state)
            call.respond(HttpStatusCode.OK, BuyOnlineMessageResponse("saved"))
        }
        get("/session/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing session id")
            val state = sessionRepo.load(id)
            if (state == null) call.respond(HttpStatusCode.NotFound, BuyOnlineErrorResponse("SESSION_NOT_FOUND", "No session"))
            else call.respond(state)
        }

        // ── Aggregated sessions list (operator analytics) ────────────────────
        // Feeds the Aegis Reports "Customer journey" funnel. Gated by the
        // `sessions.read` scope (operator-only data) and redacted server-side
        // via [RedactedSession] — see DTO above.
        //
        // Phase-2 redaction is in place. Historical context: the original TODO
        // proposed (a) hashing mobile with a per-deployment salt, (b) returning
        // a projected DTO with PII stripped, or (c) requiring an explicit
        // `pii.read` scope; we picked (b) + scope-gate, since irreversible
        // truncation is the right contract for the funnel use case.
        get("/sessions") {
            if (!requireScope("sessions.read")) return@get
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 2000)
            val sessions = sessionRepo.listSessions(limit).map { it.redact() }
            call.respond(sessions)
        }

        // ── Track proposal ───────────────────────────────────────────────────
        get("/proposal/{proposalNumber}") {
            val num = call.parameters["proposalNumber"] ?: throw IllegalArgumentException("Missing proposal number")
            call.respond(ProposalTrackResponse(
                proposalNumber = num,
                status = "Under Review",
                message = "Your proposal is being reviewed. We'll contact you within 2 business days."
            ))
        }

        // ── "Email me the resume link" (Phase-1 mock SMTP) ───────────────────
        // PHASE 1: audit-record + write the message to ~/.aegis/outbox/*.eml
        // via [EmailSender]. PHASE 2 will swap the implementation for real
        // SMTP / SES / Postmark behind the same interface — no route change
        // required. NB: the Phase-1 audit payload deliberately contains the
        // customer email and the full resume URL (PII) so the operator can
        // manually follow up if needed. Once a real send is wired, the audit
        // payload MUST be redacted (mask email local-part, drop the URL query
        // string) to match the Phase-2 PII contract used elsewhere.
        post("/session/email") {
            val req = call.receive<SessionEmailRequest>()
            val rid = call.attributes.getOrNull(REQUEST_ID_KEY)
            val actor = AuditActor.unknown()
            auditService.record(
                action = "session.email_requested",
                resourceType = "session",
                resourceId = req.url.substringAfter("session=", missingDelimiterValue = ""),
                payload = JsonObject(mapOf(
                    "email" to JsonPrimitive(req.email),
                    "url" to JsonPrimitive(req.url)
                )),
                actor = actor,
                requestId = rid
            )
            // Body contents are NOT logged (PII). Outbox writer logs file path
            // + recipient at INFO; that's the operator-visible breadcrumb.
            log.info("buyonline.email_requested: email={} url={}", req.email, req.url)
            val safeUrl = escHtml(req.url)
            val sent = emailSender.send(EmailMessage(
                to = req.email,
                subject = "Resume your PRUHealth application",
                bodyText = """
                    Hi,

                    We saved your progress. Resume your application here:
                    ${req.url}

                    This link is valid for 30 days.

                    — PRUHealth
                """.trimIndent(),
                bodyHtml = """
                    <p>Hi,</p>
                    <p>We saved your progress. Resume your application here:</p>
                    <p><a href="$safeUrl">$safeUrl</a></p>
                    <p>This link is valid for 30 days.</p>
                    <p>— PRUHealth</p>
                """.trimIndent()
            ))
            @kotlinx.serialization.Serializable
            data class SessionEmailResponse(val status: String, val queuedTo: String)
            call.respond(
                HttpStatusCode.OK,
                SessionEmailResponse(
                    status = if (sent) "queued" else "failed",
                    queuedTo = req.email
                )
            )
        }
    }
}

private fun maskMobile(mobile: String): String =
    if (mobile.length < 4) "XXXXXX" else "XXXXXX" + mobile.takeLast(4)

// Local HTML escape — ProspectusRoutes has a private `esc(...)`; we keep our
// own copy here so this route doesn't reach across files just for four
// `replace` calls. Used for the resume-link anchor in the Phase-1 mock email.
private fun escHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun generateProposalSuffix(): String {
    val ms = System.currentTimeMillis()
    val rand = (0..0xFFFFFF).random()
    return ms.toString(36).uppercase() + "-" + rand.toString(36).uppercase().padStart(5, '0')
}
