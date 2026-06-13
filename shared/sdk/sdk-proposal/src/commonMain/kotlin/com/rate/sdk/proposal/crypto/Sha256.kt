package com.rate.sdk.proposal.crypto

/**
 * SHA-256 of a UTF-8 string as lowercase hex. Used by [com.rate.sdk.proposal.handler.OtpHandler]
 * to hash OTP codes before they touch the [com.rate.core.auth.otp.OtpStore] — the plaintext code
 * never persists (security property relocated from the monolith's `OtpService.sha256`).
 *
 * `expect`/`actual` because KMP commonMain has no crypto: JVM uses `MessageDigest`, iOS uses
 * CommonCrypto via the Foundation interop, and WASM/JS uses a pure-Kotlin SHA-256 (no
 * Web-Crypto dependency, since hashing here is synchronous + non-secret-key).
 */
expect fun sha256Hex(input: String): String
