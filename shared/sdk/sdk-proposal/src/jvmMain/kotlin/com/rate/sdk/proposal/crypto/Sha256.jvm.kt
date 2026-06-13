package com.rate.sdk.proposal.crypto

import java.security.MessageDigest

/** JVM SHA-256 via `MessageDigest` (matches the monolith's `OtpService.sha256`). */
actual fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.encodeToByteArray())
    return bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
}
