package com.rate.sdk.ingestion.handler

/**
 * Content hashing for re-upload dedupe.
 *
 * [RateMeta.sourceFileSha256][com.rate.sdk.ingestion.model.RateMeta.sourceFileSha256] stores the
 * lowercase-hex SHA-256 of the uploaded file's bytes so that re-importing a byte-identical file
 * is a no-op (the [com.rate.sdk.ingestion.repository.RateMetaRepository.findBySha] short-circuit).
 *
 * SHA-256 is implemented in pure Kotlin (FIPS 180-4) so it is PURE-KMP — byte-identical on JVM,
 * wasmJs and iOS without any platform crypto dependency or expect/actual. (The same approach the
 * audit log uses for cross-platform hash-chain verification.)
 */
object SourceHash {

    /** Lowercase-hex SHA-256 of [content]'s UTF-8 encoding. */
    fun ofText(content: String): String = ofBytes(content.encodeToByteArray())

    /** Lowercase-hex SHA-256 of [bytes]. */
    fun ofBytes(bytes: ByteArray): String = hexOf(digest(bytes))

    // ── FIPS 180-4 SHA-256 ─────────────────────────────────────────────────
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

    private fun digest(message: ByteArray): ByteArray {
        var h0 = 0x6a09e667; var h1 = -0x4498517b; var h2 = 0x3c6ef372; var h3 = -0x5ab00ac6
        var h4 = 0x510e527f; var h5 = -0x64fa9774; var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19

        val padded = pad(message)
        val w = IntArray(64)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val j = offset + i * 4
                w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                    ((padded[j + 1].toInt() and 0xFF) shl 16) or
                    ((padded[j + 2].toInt() and 0xFF) shl 8) or
                    (padded[j + 3].toInt() and 0xFF)
            }
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

    private fun pad(message: ByteArray): ByteArray {
        val bitLen = message.size.toLong() * 8
        val padLen = ((56 - (message.size + 1) % 64) + 64) % 64
        val total = message.size + 1 + padLen + 8
        val out = ByteArray(total)
        message.copyInto(out)
        out[message.size] = 0x80.toByte()
        for (i in 0 until 8) out[total - 1 - i] = ((bitLen ushr (8 * i)) and 0xFF).toByte()
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
