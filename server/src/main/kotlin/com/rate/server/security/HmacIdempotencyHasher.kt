package com.rate.server.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Computes the request-body hash that the idempotency layer stores against an Idempotency-Key, so
 * a retried request with the SAME key but a DIFFERENT body is rejected (409) instead of replaying
 * the wrong response.
 *
 * RELOCATED from the monolith's `IdempotencyService.hashRequest` (a bare SHA-256). Here it is
 * keyed via HMAC-SHA256 with the app's [AppSecrets] secret so the stored hash cannot be
 * precomputed/forged by a caller who only controls the body — the server-secret keys the digest.
 * The plaintext body never lands in storage; only this opaque hex digest does.
 *
 * Lives in the app layer (it needs the secret + a JVM `Mac`); the pure idempotency CONTRACT is
 * [com.rate.sdk.audit.repository.IdempotencyStore].
 */
class HmacIdempotencyHasher(secret: String) {

    private val keyBytes = secret.toByteArray(Charsets.UTF_8)

    /** Keyed HMAC-SHA256 hex of the canonical request [body]. */
    fun hash(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).toHex()
    }

    /** Keyed HMAC-SHA256 hex of raw [bytes] (used for multipart file uploads). */
    fun hash(bytes: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(bytes).toHex()
    }

    companion object {
        /** Unkeyed SHA-256 hex — kept for file-content dedupe where a stable, secret-free digest is wanted. */
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
