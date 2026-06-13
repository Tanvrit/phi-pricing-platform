package com.rate.server.routes

import com.rate.core.auth.kyc.KycRequest
import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpResult
import com.rate.core.auth.otp.OtpStatus
import com.rate.core.base.json.AppJson
import com.rate.server.audit.ServerAuditService
import com.rate.server.auth.auditActor
import com.rate.server.auth.requireScope
import com.rate.server.email.EmailMessage
import com.rate.server.email.EmailSender
import com.rate.server.metrics.Metrics
import com.rate.server.plugins.withIdempotency
import com.rate.server.security.AppSecrets
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.core.auth.rbac.Scope
import com.rate.sdk.audit.repository.IdempotencyStore
import com.rate.sdk.proposal.handler.EligibilityHandler
import com.rate.sdk.proposal.handler.KycHandler
import com.rate.sdk.proposal.handler.OtpHandler
import com.rate.sdk.proposal.handler.PremiumHandler
import com.rate.sdk.proposal.handler.ProposalHandler
import com.rate.sdk.proposal.handler.ProposalSubmission
import com.rate.sdk.proposal.model.BuyOnlineSessionState
import com.rate.sdk.proposal.model.journey.EligibilityRequest
import com.rate.sdk.proposal.model.journey.HospitalLookupResult
import com.rate.sdk.proposal.model.journey.PremiumRequest
import com.rate.sdk.proposal.repository.SessionRepository
import com.rate.core.base.error.AppResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

@Serializable internal data class OtpSendBody(val mobile: String)
@Serializable internal data class OtpVerifyBody(val mobile: String, val otp: String)
@Serializable internal data class OtpResponse(val success: Boolean, val message: String = "", val token: String = "")
@Serializable internal data class KycOtpVerifyBody(val mobile: String, val otp: String, val aadhaarLast4: String? = null)
@Serializable internal data class SessionEmailBody(val email: String, val url: String)
@Serializable internal data class SessionEmailResponse(val status: String, val queuedTo: String)
@Serializable internal data class MessageResponse(val message: String)

/**
 * Wire DTO for the create-proposal endpoint — preserves the monolith's `ProposalRequest` shape so
 * existing journey clients keep working. Mapped to the richer [ProposalSubmission] before handing
 * to [ProposalHandler].
 */
@Serializable
internal data class ProposalBody(
    val mobile: String,
    val planTier: String,
    val sumInsured: Long,
    val annualPremium: Double,
    val totalIncludingGst: Double = 0.0,
    val tenure: Int = 1,
    val planRef: String = "",
    val quoteRef: String? = null,
)

@Serializable
internal data class ProposalResponse(
    val proposalNumber: String,
    val status: String,
    val planTier: String,
    val sumInsured: Long,
    val annualPremium: Double,
)

@Serializable
internal data class ProposalTrackResponse(val proposalNumber: String, val status: String, val message: String)

private val log = LoggerFactory.getLogger("com.rate.server.routes.BuyOnline")

/**
 * Buy-online customer journey — backward-compatible `/api/buy-online/...` paths from the monolith,
 * now mounting the pure sdk-proposal HANDLERS (OTP / eligibility / premium / KYC / proposal) over
 * the core auth ports + sdk-quoting's pricing. The route layer keeps ONLY transport + redaction.
 */
fun Route.buyOnlineRoutes(
    otp: OtpHandler,
    eligibility: EligibilityHandler,
    premium: PremiumHandler,
    kyc: KycHandler,
    proposal: ProposalHandler,
    sessions: SessionRepository,
    audit: ServerAuditService,
    idempotency: IdempotencyStore,
    hasher: HmacIdempotencyHasher,
    emailSender: EmailSender,
    secrets: AppSecrets,
) {
    val json = AppJson.json

    route("/api/buy-online") {

        // ── OTP (login) ──────────────────────────────────────────────────────
        post("/otp/send") {
            val req = call.receive<OtpSendBody>()
            respondOtpSend(otp.send(req.mobile, OtpPurpose.LOGIN, devProfile = secrets.devProfile), "login")
        }
        post("/otp/verify") {
            val req = call.receive<OtpVerifyBody>()
            respondOtpVerify(otp.verify(req.mobile, req.otp, OtpPurpose.LOGIN), "login")
        }

        // ── KYC OTP ────────────────────────────────────────────────────────
        post("/kyc/otp/send") {
            val req = call.receive<OtpSendBody>()
            respondOtpSend(otp.send(req.mobile, OtpPurpose.KYC, devProfile = secrets.devProfile), "kyc")
        }
        post("/kyc/otp/verify") {
            val req = call.receive<KycOtpVerifyBody>()
            when (val r = kyc.verifyEKycOtp(req.mobile, req.otp, req.aadhaarLast4)) {
                is AppResult.Ok -> {
                    Metrics.recordOtpVerified("kyc", "ok")
                    call.respond(kyc.toResult(req.mobile, r.value))
                }
                is AppResult.Err -> {
                    Metrics.recordOtpVerified("kyc", "fail")
                    r.orThrow()
                }
            }
        }

        // ── KYC submit (C-KYC / E-KYC initiate / Manual) ─────────────────────
        post("/kyc") {
            val req = call.receive<KycRequest>()
            when (val r = kyc.submit(req, devProfile = secrets.devProfile)) {
                is AppResult.Ok -> call.respond(kyc.toResult(req.subject, r.value))
                is AppResult.Err -> r.orThrow()
            }
        }

        // ── Pincode / hospital lookup ────────────────────────────────────────
        get("/hospitals") {
            val pincode = call.request.queryParameters["pincode"] ?: ""
            if (pincode.length != 6 || !pincode.all(Char::isDigit)) {
                throw IllegalArgumentException("Pincode must be 6 digits")
            }
            // Deterministic count per pincode (Phase-2: real network-hospital lookup service).
            val seed = pincode.toLong() % 50
            call.respond(HospitalLookupResult(pincode = pincode, hospitalsNearby = (10 + seed).toInt()))
        }

        // ── Eligibility check ────────────────────────────────────────────────
        post("/eligibility") {
            val req = call.receive<EligibilityRequest>()
            respondResult(eligibility.evaluate(req))
        }

        // ── Premium calculation (real PricingEngine via QuoteHandler) ────────
        post("/premium") {
            val req = call.receive<PremiumRequest>()
            respondResult(premium.quote(req))
        }

        // ── Submit proposal (idempotent on Idempotency-Key) ──────────────────
        post("/proposal") {
            withIdempotency(idempotency, hasher, scope = "POST /api/buy-online/proposal") { rawBody ->
                val body = json.decodeFromString(ProposalBody.serializer(), rawBody)
                val submission = ProposalSubmission(
                    mobile = body.mobile,
                    planTier = body.planTier,
                    planRef = body.planRef,
                    quoteRef = body.quoteRef,
                    sumInsured = body.sumInsured,
                    tenure = body.tenure,
                    annualPremium = body.annualPremium,
                    totalIncludingGst = if (body.totalIncludingGst > 0.0) body.totalIncludingGst else body.annualPremium,
                )
                when (val r = proposal.create(submission, call.auditActor().subject)) {
                    is AppResult.Ok -> {
                        val p = r.value
                        audit.record(
                            action = "proposal.created",
                            entity = "proposal",
                            entityId = p.proposalNumber,
                            payloadJson = buildJsonObject {
                                put("planTier", p.planTier)
                                put("sumInsured", p.sumInsured)
                                put("annualPremium", body.annualPremium)
                                put("tenure", body.tenure)
                            }.toString(),
                            actor = call.auditActor(),
                        )
                        HttpStatusCode.Created to json.encodeToString(
                            ProposalResponse.serializer(),
                            ProposalResponse(
                                proposalNumber = p.proposalNumber,
                                status = p.status.label,
                                planTier = p.planTier,
                                sumInsured = p.sumInsured,
                                annualPremium = body.annualPremium,
                            ),
                        )
                    }
                    is AppResult.Err -> {
                        val (status, code, details) = com.rate.server.plugins.mapDomainError(r.error)
                        status to json.encodeToString(
                            com.rate.server.plugins.ErrorResponse.serializer(),
                            com.rate.server.plugins.ErrorResponse(code, r.error.msg, details = details),
                        )
                    }
                }
            }
        }

        // ── Track proposal ─────────────────────────────────────────────────
        get("/proposal/{proposalNumber}") {
            val num = call.requireParam("proposalNumber")
            when (val r = proposal.track(num)) {
                is AppResult.Ok -> call.respond(
                    ProposalTrackResponse(
                        proposalNumber = r.value.proposalNumber,
                        status = r.value.status.label,
                        message = "Your proposal is being reviewed. We'll contact you within 2 business days.",
                    ),
                )
                is AppResult.Err -> r.orThrow()
            }
        }

        // ── Save+resume session snapshot (session id IS the bearer; no auth) ──
        post("/session") {
            val state = call.receive<BuyOnlineSessionState>()
            sessions.save(state)
            call.respond(HttpStatusCode.OK, MessageResponse("saved"))
        }
        get("/session/{id}") {
            val id = call.requireParam("id")
            val state = sessions.load(id) ?: throw NoSuchElementException("Session '$id' not found")
            call.respond(state)
        }

        // ── Aggregated sessions list (operator funnel; scope-gated + redacted) ─
        get("/sessions") {
            if (!requireScope(Scope.PROPOSAL_READ_ALL)) return@get
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 2000)
            call.respond(sessions.listSessions(limit).map { it.redact() })
        }

        // ── "Email me the resume link" (Phase-1 mock outbox) ─────────────────
        post("/session/email") {
            val req = call.receive<SessionEmailBody>()
            audit.record(
                action = "session.email_requested",
                entity = "session",
                entityId = req.url.substringAfter("session=", missingDelimiterValue = ""),
                payloadJson = buildJsonObject { put("email", req.email); put("url", req.url) }.toString(),
                actor = call.auditActor(),
            )
            log.info("buyonline.email_requested: email={} url={}", req.email, req.url)
            val safeUrl = escHtml(req.url)
            val sent = emailSender.send(
                EmailMessage(
                    to = req.email,
                    subject = "Resume your PRUHealth application",
                    bodyText = "Hi,\n\nWe saved your progress. Resume your application here:\n${req.url}\n\nThis link is valid for 30 days.\n\n— PRUHealth",
                    bodyHtml = "<p>Hi,</p><p>We saved your progress. Resume your application here:</p><p><a href=\"$safeUrl\">$safeUrl</a></p><p>This link is valid for 30 days.</p><p>— PRUHealth</p>",
                ),
            )
            call.respond(
                HttpStatusCode.OK,
                SessionEmailResponse(status = if (sent) "queued" else "failed", queuedTo = req.email),
            )
        }
    }
}

// ── OTP response mapping (shared login/kyc) ──────────────────────────────────
// Typed responds (no `Any`) so kotlinx-serialization resolves the right serializer.

private suspend fun RoutingContext.respondOtpSend(r: OtpResult, purpose: String) {
    when (r.status) {
        OtpStatus.OK -> {
            Metrics.recordOtpSent(purpose)
            call.respond(HttpStatusCode.OK, OtpResponse(success = true, message = "OTP sent", token = r.devCode ?: ""))
        }
        OtpStatus.RATE_LIMITED -> {
            Metrics.recordOtpRateLimited()
            call.respond(
                HttpStatusCode.TooManyRequests,
                com.rate.server.plugins.ErrorResponse("OTP_RATE_LIMIT", "Too many OTP requests; try again in 1 hour."),
            )
        }
        else -> call.respond(HttpStatusCode.OK, OtpResponse(success = true))
    }
}

private suspend fun RoutingContext.respondOtpVerify(r: OtpResult, purpose: String) {
    when (r.status) {
        OtpStatus.OK -> {
            Metrics.recordOtpVerified(purpose, "ok")
            call.respond(HttpStatusCode.OK, OtpResponse(success = true, token = r.token ?: ""))
        }
        OtpStatus.MISMATCH -> {
            Metrics.recordOtpVerified(purpose, "mismatch")
            call.respond(
                HttpStatusCode.Unauthorized,
                com.rate.server.plugins.ErrorResponse("OTP_INVALID", "Invalid OTP. ${r.attemptsRemaining ?: 0} attempts remaining."),
            )
        }
        OtpStatus.EXPIRED -> {
            Metrics.recordOtpVerified(purpose, "expired")
            call.respond(HttpStatusCode.Gone, com.rate.server.plugins.ErrorResponse("OTP_EXPIRED", "OTP expired. Please request a new one."))
        }
        OtpStatus.TOO_MANY_ATTEMPTS -> {
            Metrics.recordOtpVerified(purpose, "locked")
            call.respond(HttpStatusCode.TooManyRequests, com.rate.server.plugins.ErrorResponse("OTP_LOCKED", "Too many attempts. Please request a new OTP."))
        }
        OtpStatus.NO_ACTIVE_CODE -> {
            Metrics.recordOtpVerified(purpose, "not_found")
            call.respond(HttpStatusCode.NotFound, com.rate.server.plugins.ErrorResponse("OTP_NOT_FOUND", "No OTP active for this mobile."))
        }
        OtpStatus.RATE_LIMITED -> {
            Metrics.recordOtpRateLimited()
            call.respond(HttpStatusCode.TooManyRequests, com.rate.server.plugins.ErrorResponse("OTP_RATE_LIMIT", "Too many OTP requests."))
        }
    }
}

// ── Redacted session DTO (PII mitigation, operator funnel) ───────────────────
@Serializable
internal data class RedactedSession(
    val sessionId: String,
    val currentScreen: String,
    val mobileMasked: String,
    val pincodePrefix: String,
    val eldestAge: String,
    val kidsCount: Int,
    val hasPED: Boolean,
    val hasCriticalIllness: Boolean,
    val pedMemberCount: Int,
    val criticalIllnessMemberCount: Int,
    val selectedTier: String,
    val selectedSumInsured: Long,
    val selectedTenure: Int,
    val selectedAddOnIds: List<String>,
    val createdAtIso: String,
    val updatedAtIso: String,
)

private fun BuyOnlineSessionState.redact(): RedactedSession = RedactedSession(
    sessionId = sessionId.take(8) + (if (sessionId.length > 8) "…" else ""),
    currentScreen = currentScreen,
    mobileMasked = if (mobile.length >= 4) "X".repeat(mobile.length - 4) + mobile.takeLast(4) else "XXXX",
    pincodePrefix = if (pincode.length >= 3) pincode.take(3) + "XXX" else "XXX",
    eldestAge = eldestAge,
    kidsCount = kidsCount,
    hasPED = hasPED,
    hasCriticalIllness = hasCriticalIllness,
    pedMemberCount = pedMembers.size,
    criticalIllnessMemberCount = criticalIllnessMembers.size,
    selectedTier = selectedTier,
    selectedSumInsured = selectedSumInsured,
    selectedTenure = selectedTenure,
    selectedAddOnIds = selectedAddOnIds,
    createdAtIso = createdAt.toString(),
    updatedAtIso = updatedAt.toString(),
)

private fun escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
