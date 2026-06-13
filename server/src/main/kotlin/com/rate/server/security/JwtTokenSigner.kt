package com.rate.server.security

import com.rate.core.auth.token.JwtClaims
import com.rate.core.auth.token.TokenSigner
import com.rate.core.base.json.AppJson
import com.rate.core.base.time.Now
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Real HS256 JWT [TokenSigner] — the single app-layer ACTUAL behind the pure core PORT. The
 * signing secret lives in [AppSecrets] (env-loaded) and NEVER leaks into the core/sdk layers.
 *
 * RELOCATED from the monolith's `OtpService.issueShortLivedToken` / `verifyToken`, whose
 * `<mobile>.<purpose>.<expiryMs>.<base64HmacSha256>` nonce was forgeable in spirit (no role /
 * scope binding, ad-hoc format). This emits a standard compact JWT
 * `base64url(header).base64url(payload).base64url(sig)` over the canonical [JwtClaims], so a
 * stock JWT decoder reads it and the claims (sub/role/scopes/exp) are first-class.
 *
 * Security properties:
 *  - HMAC-SHA256 over `header.payload` with the env secret;
 *  - constant-time signature comparison on [verify];
 *  - expiry enforced against [Now] (epoch SECONDS, JWT `exp` convention);
 *  - tampering, unknown algorithm, malformed segments, or expiry → null (never throws).
 */
class JwtTokenSigner(
    private val signingSecret: String,
    /** Optional key id stamped into the header + claims for future rotation. */
    private val keyId: String? = null,
) : TokenSigner {

    private val secretBytes = signingSecret.toByteArray(Charsets.UTF_8)

    override fun sign(claims: JwtClaims): String {
        val header = Header(alg = ALG, typ = "JWT", kid = keyId)
        val stamped = if (keyId != null && claims.keyId == null) claims.copy(keyId = keyId) else claims
        val headerB64 = b64Url(AppJson.json.encodeToString(Header.serializer(), header).toByteArray(Charsets.UTF_8))
        val payloadB64 = b64Url(AppJson.json.encodeToString(JwtClaims.serializer(), stamped).toByteArray(Charsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64"
        val sig = b64Url(hmacSha256(signingInput.toByteArray(Charsets.UTF_8)))
        return "$signingInput.$sig"
    }

    override fun verify(token: String): JwtClaims? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val (headerB64, payloadB64, sigB64) = parts
        val header = runCatching {
            AppJson.json.decodeFromString(Header.serializer(), decodeB64ToString(headerB64))
        }.getOrNull() ?: return null
        if (!header.alg.equals(ALG, ignoreCase = true)) return null

        val expectedSig = b64Url(hmacSha256("$headerB64.$payloadB64".toByteArray(Charsets.UTF_8)))
        if (!constantTimeEquals(expectedSig, sigB64)) return null

        val claims = runCatching {
            AppJson.json.decodeFromString(JwtClaims.serializer(), decodeB64ToString(payloadB64))
        }.getOrNull() ?: return null

        if (claims.isExpired(Now.instant().epochSeconds)) return null
        return claims
    }

    // ── crypto helpers ─────────────────────────────────────────────────────────

    private fun hmacSha256(input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        return mac.doFinal(input)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    @kotlinx.serialization.Serializable
    private data class Header(
        val alg: String,
        val typ: String = "JWT",
        val kid: String? = null,
    )

    companion object {
        private const val ALG = "HS256"

        private fun b64Url(bytes: ByteArray): String =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun decodeB64ToString(s: String): String =
            String(java.util.Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
    }
}
