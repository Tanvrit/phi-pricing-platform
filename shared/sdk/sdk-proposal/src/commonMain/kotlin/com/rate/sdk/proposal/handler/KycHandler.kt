package com.rate.sdk.proposal.handler

import com.rate.core.auth.kyc.KycMethod
import com.rate.core.auth.kyc.KycRequest
import com.rate.core.auth.kyc.KycResult
import com.rate.core.auth.kyc.KycStatus
import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpStatus
import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.time.Now
import com.rate.core.base.util.ValidationResult
import com.rate.core.base.util.Validators
import com.rate.sdk.proposal.event.ProposalEvent
import com.rate.sdk.proposal.event.ProposalEventSink
import com.rate.sdk.proposal.model.KycDocRef
import com.rate.sdk.proposal.model.KycState

/**
 * KYC orchestration for the buy-online journey across the three regulated channels.
 *
 * RELOCATED from the monolith's split KYC handling (the `/kyc/otp/send` + `/kyc/otp/verify`
 * routes covered only the E-KYC OTP step; C-KYC/MANUAL were handled client-side with no server
 * contract). This handler unifies all three methods over the core [KycMethod]/[KycRequest]/
 * [KycResult] contract:
 *
 *  - C_KYC  → validate PAN + DOB, "resolve" a CKYC number (Phase-2 wires the real central-KYC
 *    registry; for now a deterministic reference is minted so the flow is end-to-end);
 *  - E_KYC  → validate the Aadhaar last-4, send a 6-digit [OtpPurpose.KYC] OTP via the injected
 *    [OtpHandler], and verify it via [verifyEKycOtp];
 *  - MANUAL → require at least one uploaded document ref, mark PENDING for operator review.
 *
 * Pure orchestration over the injected [OtpHandler] (which itself uses the core OtpStore/
 * TokenSigner PORTs) — no secrets, no platform IO. Validation uses the relocated core [Validators].
 */
class KycHandler(
    private val otpHandler: OtpHandler,
    private val events: ProposalEventSink = ProposalEventSink.NOOP,
) {

    /**
     * Initiate / submit KYC for the chosen method. For E-KYC this only sends the OTP — the caller
     * must follow with [verifyEKycOtp]; the returned state is PENDING. For C-KYC and MANUAL the
     * returned state reflects the (synchronous) outcome.
     */
    suspend fun submit(request: KycRequest, devProfile: Boolean = false): AppResult<KycState> =
        when (request.method) {
            KycMethod.C_KYC -> submitCKyc(request)
            KycMethod.E_KYC -> initiateEKyc(request, devProfile)
            KycMethod.MANUAL -> submitManual(request)
        }

    // ── C-KYC: PAN + DOB → CKYC number ─────────────────────────────────────────
    private suspend fun submitCKyc(request: KycRequest): AppResult<KycState> {
        val errors = buildList {
            addError(Validators.pan(request.pan))
            if (request.dob.isNullOrBlank()) add("Date of birth is required for C-KYC")
        }
        if (errors.isNotEmpty()) return AppResult.Err(DomainError.Validation(errors))

        // Phase-2: replace with a real central-KYC lookup. A deterministic reference keeps the
        // journey end-to-end and idempotent for the same PAN.
        val ckycNumber = "CKYC-" + (request.pan ?: "").uppercase()
        val state = KycState(
            method = KycMethod.C_KYC,
            status = KycStatus.VERIFIED,
            pan = request.pan?.uppercase(),
            dob = request.dob,
            referenceId = ckycNumber,
            verifiedAt = Now.instant(),
        )
        emitSubmitted(request.subject, state)
        return AppResult.Ok(state)
    }

    // ── E-KYC: Aadhaar last-4 + 6-digit OTP via DigiLocker ─────────────────────
    private suspend fun initiateEKyc(request: KycRequest, devProfile: Boolean): AppResult<KycState> {
        val last4 = request.aadhaarLast4
        if (last4 == null || last4.length != 4 || !last4.all(Char::isDigit)) {
            return AppResult.Err(DomainError.Validation(listOf("Aadhaar last-4 must be 4 digits")))
        }
        when (otpHandler.send(request.subject, OtpPurpose.KYC, devProfile).status) {
            OtpStatus.RATE_LIMITED ->
                return AppResult.Err(DomainError.Conflict("Too many KYC OTP requests; try again later."))
            else -> Unit
        }
        // PENDING until the OTP step completes (verifyEKycOtp).
        val state = KycState(
            method = KycMethod.E_KYC,
            status = KycStatus.PENDING,
            aadhaarLast4 = last4,
        )
        return AppResult.Ok(state)
    }

    /**
     * Complete the E-KYC OTP step. On a matching 6-digit code the state becomes VERIFIED with the
     * issued bearer recorded as the [KycState.referenceId] (DigiLocker txn stand-in for Phase-1).
     */
    suspend fun verifyEKycOtp(subject: String, code: String, aadhaarLast4: String?): AppResult<KycState> {
        val r = otpHandler.verify(subject, code, OtpPurpose.KYC)
        return when (r.status) {
            OtpStatus.OK -> {
                val state = KycState(
                    method = KycMethod.E_KYC,
                    status = KycStatus.VERIFIED,
                    aadhaarLast4 = aadhaarLast4,
                    referenceId = r.token,
                    verifiedAt = Now.instant(),
                )
                emitSubmitted(subject, state)
                AppResult.Ok(state)
            }
            OtpStatus.MISMATCH ->
                AppResult.Err(DomainError.Validation(listOf("Invalid KYC OTP. ${r.attemptsRemaining ?: 0} attempts remaining.")))
            OtpStatus.EXPIRED ->
                AppResult.Err(DomainError.Validation(listOf("KYC OTP expired. Please request a new one.")))
            OtpStatus.TOO_MANY_ATTEMPTS ->
                AppResult.Err(DomainError.Conflict("Too many KYC OTP attempts. Please request a new one."))
            OtpStatus.NO_ACTIVE_CODE ->
                AppResult.Err(DomainError.NotFound("KYC OTP", subject))
            OtpStatus.RATE_LIMITED ->
                AppResult.Err(DomainError.Conflict("Too many KYC OTP requests; try again later."))
        }
    }

    // ── MANUAL: uploaded document refs → operator review ───────────────────────
    private suspend fun submitManual(request: KycRequest): AppResult<KycState> {
        if (request.documentRefs.isEmpty()) {
            return AppResult.Err(DomainError.Validation(listOf("At least one document is required for manual KYC")))
        }
        val state = KycState(
            method = KycMethod.MANUAL,
            status = KycStatus.PENDING,
            documents = request.documentRefs.map { KycDocRef(docType = "UPLOAD", storageRef = it) },
        )
        emitSubmitted(request.subject, state)
        return AppResult.Ok(state)
    }

    /** Adapt a [KycState] into the core wire [KycResult] for the network layer. */
    fun toResult(subject: String, state: KycState): KycResult = KycResult(
        subject = subject,
        method = state.method,
        status = state.status,
        referenceId = state.referenceId,
        message = when (state.status) {
            KycStatus.VERIFIED -> "KYC verified"
            KycStatus.PENDING -> "KYC pending"
            KycStatus.REJECTED -> "KYC rejected"
        },
    )

    private suspend fun emitSubmitted(subject: String, state: KycState) {
        events.emit(
            ProposalEvent.KycSubmitted(subject, state.method.name, state.status.name, Now.instant()),
        )
    }

    private fun MutableList<String>.addError(r: ValidationResult) {
        (r as? ValidationResult.Invalid)?.let { add(it.message) }
    }
}
