package com.rate.sdk.audit.crypto

import java.security.MessageDigest

/** JVM: native JDK SHA-256. Output matches [Sha256Pure] byte-for-byte. */
actual fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.encodeToByteArray())
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return sb.toString()
}

private const val HEX = "0123456789abcdef"
