package com.rate.server.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-layer salted password hashing for console [com.rate.sdk.catalog.model.User] credentials. The
 * pure core/sdk layers carry no crypto, so this — like [JwtTokenSigner] — is a server-only ACTUAL.
 *
 * Algorithm: PBKDF2-WithHmacSHA256, a random per-password salt, a configurable iteration count.
 * The encoded form is a single self-describing string so the iteration count + salt travel with
 * the hash and a future cost bump is verifiable against old hashes:
 *
 *     pbkdf2-sha256:<iterations>:<saltBase64>:<hashBase64>
 *
 * [verify] is constant-time over the derived hash bytes and never throws on a malformed stored hash
 * (returns false), so a corrupted/legacy value degrades to "wrong password" rather than a 500.
 */
class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val saltBytes: Int = DEFAULT_SALT_BYTES,
    private val keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
) {
    private val rng = SecureRandom()

    /** Hash a plaintext [password] with a fresh random salt; returns the encoded self-describing form. */
    fun hash(password: String): String {
        val salt = ByteArray(saltBytes).also(rng::nextBytes)
        val derived = pbkdf2(password, salt, iterations, keyLengthBits)
        return listOf(
            ALGORITHM_TAG,
            iterations.toString(),
            B64_ENCODER.encodeToString(salt),
            B64_ENCODER.encodeToString(derived),
        ).joinToString(SEP)
    }

    /**
     * Verify [password] against a previously [hash]ed value. Re-derives with the salt + iterations
     * embedded in [stored] and compares in constant time. Returns false for any unparseable or
     * mismatched stored hash (never throws).
     */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(SEP)
        if (parts.size != 4 || parts[0] != ALGORITHM_TAG) return false
        val iters = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { B64_DECODER.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { B64_DECODER.decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iters, expected.size * 8)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray, iters: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iters, keyBits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        private const val ALGORITHM_TAG = "pbkdf2-sha256"
        private const val SEP = ":"
        private const val DEFAULT_ITERATIONS = 120_000
        private const val DEFAULT_SALT_BYTES = 16
        private const val DEFAULT_KEY_LENGTH_BITS = 256
        private val B64_ENCODER: Base64.Encoder = Base64.getEncoder()
        private val B64_DECODER: Base64.Decoder = Base64.getDecoder()
    }
}
