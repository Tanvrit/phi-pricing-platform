package com.rate.sdk.audit.crypto

/** iOS: pure-Kotlin SHA-256 (avoids the CryptoKit interop surface; same digest). */
actual fun sha256Hex(input: String): String = Sha256Pure.hex(input)
