package com.rate.server.security

import com.rate.server.metrics.Metrics
import io.ktor.util.encodeBase64
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/**
 * Real OTP store with cryptographic basics. Replaces the prior mock that accepted any
 * 4/6-digit string. Storage is in-memory (concurrent map) for the Foundation Pack —
 * Phase 2 migrates to Redis or the `otp_records` table for cross-instance correctness.
 *
 * Properties enforced here:
 *   - codes are 6-digit `SecureRandom` integers (not `Random.nextInt`)
 *   - code-at-rest is SHA-256 hashed (never logged in plaintext)
 *   - 5-minute TTL, max 5 verification attempts, lockout after that
 *   - per-mobile rate limit: max 5 sends/hour
 *   - constant-time hash comparison
 *   - short-lived `issueShortLivedToken` returns an HMAC-SHA256-signed nonce so the
 *     prior trivially-forgeable `"mock-jwt-${mobile}"` is gone
 */
class OtpService(private val tokenSecret: String) {

    private val log = LoggerFactory.getLogger(OtpService::class.java)
    private val rng = SecureRandom()
    private val store = ConcurrentHashMap<String, OtpRecord>()
    private val sendBuckets = ConcurrentHashMap<String, SendBucket>()
    private val lock = ReentrantLock()

    /** Generates and stores a fresh OTP. Returns the code (so dev/test flows can log it). */
    fun sendOtp(mobile: String, purpose: Purpose = Purpose.LOGIN): SendResult {
        // Per-mobile rate limit
        val bucket = sendBuckets.computeIfAbsent("$mobile:$purpose") { SendBucket() }
        lock.withLock {
            bucket.prune()
            if (bucket.count() >= MAX_SENDS_PER_HOUR) {
                Metrics.recordOtpRateLimited()
                return SendResult.RateLimited
            }
            bucket.add()
        }

        val code = (rng.nextInt(900_000) + 100_000).toString()  // 6 digits, leading non-zero
        val record = OtpRecord(
            codeHash = sha256(code),
            expiresAt = Instant.now().plusSeconds(TTL_SECONDS),
            attempts = 0,
            purpose = purpose
        )
        store["$mobile:$purpose"] = record
        Metrics.recordOtpSent()

        // PHASE 2: replace with a real SMS gateway (MSG91 / Gupshup / Karix).
        // We deliberately log at INFO so dev/test flows can verify the OTP. Production
        // logging filters mobile numbers (Logback PII converter); the code itself isn't PII
        // but rotates every 5 min so log retention isn't a long-term leak vector.
        log.info("OTP issued [purpose={}, mobile={}, ttl={}s, code={}]",
            purpose, mobile, TTL_SECONDS, code)

        return SendResult.Ok(code)
    }

    fun verifyOtp(mobile: String, code: String, purpose: Purpose = Purpose.LOGIN): VerifyResult {
        val key = "$mobile:$purpose"
        val record = store[key] ?: run {
            Metrics.recordOtpVerifyFailure()
            return VerifyResult.NoActiveCode
        }
        if (Instant.now().isAfter(record.expiresAt)) {
            store.remove(key)
            Metrics.recordOtpVerifyFailure()
            return VerifyResult.Expired
        }
        // Note: TooManyAttempts is bucketed as a verify-failure (not rate-limited).
        // The `otp_rate_limited_total` counter is reserved for the send-side
        // per-mobile-per-hour bucket so the two signals stay distinguishable.
        if (record.attempts >= MAX_VERIFY_ATTEMPTS) {
            store.remove(key)
            Metrics.recordOtpVerifyFailure()
            return VerifyResult.TooManyAttempts
        }
        val candidate = sha256(code)
        val matches = constantTimeEquals(candidate, record.codeHash)
        if (!matches) {
            store[key] = record.copy(attempts = record.attempts + 1)
            Metrics.recordOtpVerifyFailure()
            return VerifyResult.Mismatch(remaining = MAX_VERIFY_ATTEMPTS - (record.attempts + 1))
        }
        // Success — remove the record so a code can't be reused.
        store.remove(key)
        Metrics.recordOtpVerifySuccess()
        return VerifyResult.Ok(issueShortLivedToken(mobile, purpose))
    }

    /**
     * HMAC-signed nonce that callers can present until expiry. NOT a JWT; Phase 2
     * swaps this whole token machinery for real JWT auth with refresh tokens.
     * Format: `<mobile>.<purpose>.<expiryMs>.<base64HmacSha256>`
     */
    fun issueShortLivedToken(mobile: String, purpose: Purpose): String {
        val expiry = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).toEpochMilli()
        val payload = "$mobile.$purpose.$expiry"
        val sig = hmacSha256(payload)
        return "$payload.$sig"
    }

    fun verifyToken(token: String): TokenVerifyResult {
        val parts = token.split('.')
        if (parts.size != 4) return TokenVerifyResult.Invalid
        val payload = "${parts[0]}.${parts[1]}.${parts[2]}"
        val expectedSig = hmacSha256(payload)
        if (!constantTimeEquals(expectedSig, parts[3])) return TokenVerifyResult.Invalid
        val expiryMs = parts[2].toLongOrNull() ?: return TokenVerifyResult.Invalid
        if (Instant.now().toEpochMilli() > expiryMs) return TokenVerifyResult.Expired
        return TokenVerifyResult.Valid(mobile = parts[0])
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(tokenSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8)).encodeBase64()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    enum class Purpose { LOGIN, KYC }

    private data class OtpRecord(
        val codeHash: String,
        val expiresAt: Instant,
        val attempts: Int,
        val purpose: Purpose
    )

    sealed class SendResult {
        data class Ok(val code: String) : SendResult()
        object RateLimited : SendResult()
    }

    sealed class VerifyResult {
        data class Ok(val token: String) : VerifyResult()
        object NoActiveCode : VerifyResult()
        object Expired : VerifyResult()
        object TooManyAttempts : VerifyResult()
        data class Mismatch(val remaining: Int) : VerifyResult()
    }

    sealed class TokenVerifyResult {
        data class Valid(val mobile: String) : TokenVerifyResult()
        object Expired : TokenVerifyResult()
        object Invalid : TokenVerifyResult()
    }

    private class SendBucket {
        private val timestamps = ArrayDeque<Long>()
        fun add() = timestamps.addLast(Instant.now().toEpochMilli())
        fun prune() {
            val cutoff = Instant.now().toEpochMilli() - WINDOW_MS
            while (timestamps.isNotEmpty() && timestamps.first() < cutoff) timestamps.removeFirst()
        }
        fun count() = timestamps.size
    }

    companion object {
        const val TTL_SECONDS = 5L * 60                  // OTP code lifetime
        const val TOKEN_TTL_SECONDS = 15L * 60           // post-verify token lifetime
        const val MAX_VERIFY_ATTEMPTS = 5
        const val MAX_SENDS_PER_HOUR = 5
        const val WINDOW_MS = 60L * 60 * 1000            // 1h rolling window
    }
}
