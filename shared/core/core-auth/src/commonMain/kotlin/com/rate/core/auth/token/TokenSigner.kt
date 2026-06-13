package com.rate.core.auth.token

/**
 * PORT for minting and verifying signed bearer tokens. The ONLY implementation lives
 * in the server app, where the HMAC/JWT secret and key material are configured — this
 * pure module deliberately holds neither the secret nor a JWT dependency.
 *
 * Relocated from server `OtpService.issueShortLivedToken` / `verifyToken`: the
 * forgeable HMAC-nonce scheme is generalised here to a clean sign/verify boundary so
 * the app can swap in a real JWT signer without touching any caller.
 */
interface TokenSigner {

    /** Encode + sign [claims] into a compact bearer token string. */
    fun sign(claims: JwtClaims): String

    /**
     * Verify signature + expiry of [token] and return its [JwtClaims], or null if the
     * token is malformed, tampered, signed with an unknown key, or expired. Verifiers
     * MUST compare signatures in constant time.
     */
    fun verify(token: String): JwtClaims?
}
