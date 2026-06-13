package com.rate.sdk.audit.crypto

/**
 * Pure-Kotlin SHA-256 (FIPS 180-4) — no platform crypto, works on every KMP target.
 *
 * Used by the wasmJs and iOS [sha256Hex] actuals (the JVM actual prefers the JDK's
 * native MessageDigest for speed). Operates on UTF-8 bytes; produces the same digest
 * the JDK does, so the hash chain verifies cross-platform.
 *
 * Implementation notes:
 *  - All arithmetic is on Int with explicit 32-bit masking via `and 0xFFFFFFFF.toInt()`
 *    is unnecessary because Kotlin Int is already 32-bit two's-complement and wraps on
 *    overflow; `ushr` / `shr` / `shl` and `xor`/`and`/`or` behave identically across
 *    JVM/Native/JS-wasm. Message length is tracked as a 64-bit Long bit count.
 *  - Big-endian throughout (network byte order), per the spec.
 */
object Sha256Pure {

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
        0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
        -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
        -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
        -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    private const val H0 = 0x6a09e667
    private const val H1 = -0x4498517b // 0xbb67ae85
    private const val H2 = 0x3c6ef372
    private const val H3 = -0x5ab00ac6 // 0xa54ff53a
    private const val H4 = 0x510e527f
    private const val H5 = -0x64fa9774 // 0x9b05688c
    private const val H6 = 0x1f83d9ab
    private const val H7 = 0x5be0cd19

    /** Lowercase hex SHA-256 of [input]'s UTF-8 encoding. */
    fun hex(input: String): String = hexOf(digest(input.encodeToByteArray()))

    /** Raw 32-byte SHA-256 digest of [message]. */
    fun digest(message: ByteArray): ByteArray {
        var h0 = H0; var h1 = H1; var h2 = H2; var h3 = H3
        var h4 = H4; var h5 = H5; var h6 = H6; var h7 = H7

        val padded = pad(message)
        val w = IntArray(64)

        var offset = 0
        while (offset < padded.size) {
            // Load the 16 big-endian words of this 512-bit block.
            for (i in 0 until 16) {
                val j = offset + i * 4
                w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                        ((padded[j + 1].toInt() and 0xFF) shl 16) or
                        ((padded[j + 2].toInt() and 0xFF) shl 8) or
                        (padded[j + 3].toInt() and 0xFF)
            }
            // Message schedule expansion.
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7

            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj

                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }

            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
            offset += 64
        }

        val out = ByteArray(32)
        writeBE(out, 0, h0); writeBE(out, 4, h1); writeBE(out, 8, h2); writeBE(out, 12, h3)
        writeBE(out, 16, h4); writeBE(out, 20, h5); writeBE(out, 24, h6); writeBE(out, 28, h7)
        return out
    }

    /**
     * Append the 0x80 terminator + zero padding + 64-bit big-endian bit length so the
     * total length is a multiple of 64 bytes.
     */
    private fun pad(message: ByteArray): ByteArray {
        val bitLen = message.size.toLong() * 8
        // 1 terminator byte + k zero bytes such that (size + 1 + k + 8) % 64 == 0.
        val padLen = ((56 - (message.size + 1) % 64) + 64) % 64
        val total = message.size + 1 + padLen + 8
        val out = ByteArray(total)
        message.copyInto(out)
        out[message.size] = 0x80.toByte()
        // 64-bit big-endian length in the final 8 bytes.
        for (i in 0 until 8) {
            out[total - 1 - i] = ((bitLen ushr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

    private fun writeBE(out: ByteArray, off: Int, v: Int) {
        out[off] = (v ushr 24).toByte()
        out[off + 1] = (v ushr 16).toByte()
        out[off + 2] = (v ushr 8).toByte()
        out[off + 3] = v.toByte()
    }

    private const val HEX = "0123456789abcdef"

    private fun hexOf(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}
