package com.rate.sdk.proposal.crypto

/**
 * iOS SHA-256 — the pure-Kotlin implementation. Avoids the CommonCrypto cinterop ceremony for a
 * short, non-secret-key hash; correctness is identical to the JVM `MessageDigest` path.
 */
actual fun sha256Hex(input: String): String = sha256HexPure(input)
