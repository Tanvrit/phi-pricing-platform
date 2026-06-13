package com.rate.sdk.proposal.handler

import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpRecord
import com.rate.core.auth.otp.OtpResult
import com.rate.core.auth.otp.OtpStatus
import com.rate.core.auth.otp.OtpStore
import com.rate.core.auth.rbac.AuthRole
import com.rate.core.auth.token.JwtClaims
import com.rate.core.auth.token.TokenSigner
import com.rate.core.base.time.Now
import com.rate.sdk.proposal.crypto.sha256Hex
import com.rate.sdk.proposal.event.ProposalEvent
import com.rate.sdk.proposal.event.ProposalEventSink
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * OTP send/verify policy + algorithm for the buy-online journey.
 *
 * RELOCATED from the server's `OtpService` (in-memory `ConcurrentHashMap` + crypto + policy all
 * tangled in one JVM class). The re-arch splits the concerns cleanly:
 *  - STORAGE → the core [OtpStore] PORT (Mongo `otp_records` actual in server-persistence);
 *  - TOKEN MINTING → the core [TokenSigner] PORT (the HMAC/JWT secret lives ONLY in the app);
 *  - HASHING → the pure-KMP [sha256Hex] (expect/actual);
 *  - POLICY (TTL, max attempts, rate limit, constant-time compare, code length per purpose) →
 *    THIS handler, pure-KMP so it runs identically on JVM/iOS/WASM and is unit-testable against
 *    a fake [OtpStore].
 *
 * Security properties preserved from the monolith:
 *  - codes are purpose-length (4-digit LOGIN, 6-digit KYC) with a leading non-zero digit;
 *  - only the SHA-256 [OtpRecord.codeHash] is ever stored — the plaintext never persists;
 *  - 5-minute TTL, max 5 verify attempts (record discarded after), per-mobile rate limit;
 *  - constant-time hash comparison;
 *  - on success the [TokenSigner] mints a short-lived signed bearer (the monolith's forgeable
 *    `"mock-jwt-${mobile}"` / HMAC-nonce is replaced by the clean signer PORT).
 *
 * [randomCode] is injectable so tests get deterministic codes; production passes a secure source
 * via DI (the app supplies `Random.Default` or a CSPRNG-backed lambda).
 */
class OtpHandler(
    private val store: OtpStore,
    private val tokenSigner: TokenSigner,
    private val events: ProposalEventSink = ProposalEventSink.NOOP,
    private val config: OtpConfig = OtpConfig(),
    private val randomCode: (OtpPurpose) -> String = ::defaultRandomCode,
) {

    /**
     * Mint, hash and store a fresh OTP for [mobile]/[purpose], enforcing the per-mobile rolling
     * rate limit. Returns [OtpStatus.RATE_LIMITED] when the window is full, else [OtpStatus.OK].
     *
     * [devProfile] (dev/test only) surfaces the plaintext code in [OtpResult.devCode] so
     * automated flows can read it — NEVER pass true in production.
     */
    suspend fun send(mobile: String, purpose: OtpPurpose, devProfile: Boolean = false): OtpResult {
        val now = Now.instant()
        val windowStart = now - config.rateLimitWindow
        val sends = store.countSendsSince(mobile, purpose, windowStart)
        if (sends >= config.maxSendsPerWindow) {
            return OtpResult(status = OtpStatus.RATE_LIMITED)
        }

        val code = randomCode(purpose)
        store.put(
            OtpRecord(
                mobile = mobile,
                purpose = purpose,
                codeHash = sha256Hex(code),
                expiresAt = now + config.codeTtl,
                attempts = 0,
            ),
        )
        store.recordSend(mobile, purpose, now)
        events.emit(ProposalEvent.OtpSent(mobile, purpose.name, now))
        return OtpResult(
            status = OtpStatus.OK,
            devCode = if (devProfile) code else null,
        )
    }

    /**
     * Verify a typed [code] against the stored hash for [mobile]/[purpose]. Bumps the attempt
     * counter on mismatch, discards the record on success / expiry / attempt-exhaustion, and on
     * success mints a short-lived signed bearer via the [TokenSigner].
     */
    suspend fun verify(mobile: String, code: String, purpose: OtpPurpose): OtpResult {
        val now = Now.instant()
        val record = store.get(mobile, purpose)
            ?: return OtpResult(status = OtpStatus.NO_ACTIVE_CODE)

        if (record.isExpired(now)) {
            store.delete(mobile, purpose)
            return OtpResult(status = OtpStatus.EXPIRED)
        }
        if (record.attempts >= config.maxVerifyAttempts) {
            store.delete(mobile, purpose)
            return OtpResult(status = OtpStatus.TOO_MANY_ATTEMPTS)
        }

        val matches = constantTimeEquals(sha256Hex(code), record.codeHash)
        if (!matches) {
            val bumped = record.copy(attempts = record.attempts + 1)
            store.update(bumped)
            val remaining = (config.maxVerifyAttempts - bumped.attempts).coerceAtLeast(0)
            return OtpResult(status = OtpStatus.MISMATCH, attemptsRemaining = remaining)
        }

        // Success — drop the record so a code can't be replayed.
        store.delete(mobile, purpose)
        val token = mintToken(mobile, purpose, now)
        events.emit(ProposalEvent.OtpVerified(mobile, purpose.name, now))
        return OtpResult(status = OtpStatus.OK, token = token)
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * Mint a short-lived signed bearer attesting the verification. The subject is the mobile; the
     * role is [AuthRole.CUSTOMER]; a purpose-specific scope lets downstream calls (KYC / proposal)
     * check the bearer attests the right step.
     */
    private fun mintToken(mobile: String, purpose: OtpPurpose, now: Instant): String {
        val exp = (now + config.tokenTtl).epochSeconds
        val scope = when (purpose) {
            OtpPurpose.LOGIN -> "buyonline.login"
            OtpPurpose.KYC -> "buyonline.kyc"
        }
        return tokenSigner.sign(
            JwtClaims(sub = mobile, role = AuthRole.CUSTOMER, scopes = setOf(scope), exp = exp),
        )
    }

    /** Length-safe constant-time string compare (same algorithm as the monolith). */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

/**
 * OTP policy knobs — defaults match the monolith's `OtpService` companion (5-min code TTL,
 * 15-min post-verify token, 5 verify attempts, 5 sends/hour).
 */
data class OtpConfig(
    val codeTtl: Duration = 5.minutes,
    val tokenTtl: Duration = 15.minutes,
    val rateLimitWindow: Duration = 1.hours,
    val maxVerifyAttempts: Int = 5,
    val maxSendsPerWindow: Int = 5,
)

/**
 * Default code generator: a purpose-length numeric code with a guaranteed leading non-zero digit
 * (LOGIN = 4 digits, KYC = 6 digits). Uses [Random.Default]; the app may inject a CSPRNG-backed
 * lambda for production.
 */
internal fun defaultRandomCode(purpose: OtpPurpose): String {
    val min = purpose.minCodeInclusive
    val max = purpose.maxCodeExclusive
    return Random.nextInt(min, max).toString()
}
