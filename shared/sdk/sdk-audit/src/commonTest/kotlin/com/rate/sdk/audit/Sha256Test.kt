package com.rate.sdk.audit

import com.rate.sdk.audit.crypto.Sha256Pure
import com.rate.sdk.audit.crypto.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun knownVectors() {
        // FIPS 180-4 / RFC test vectors.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun expectActualMatchesPureImpl() {
        // The platform actual must agree with the pure-Kotlin reference on every target.
        for (s in listOf("", "a", "the quick brown fox", "GENESIS{\"seq\":1}", "ünïcödé ✓")) {
            assertEquals(Sha256Pure.hex(s), sha256Hex(s), "mismatch for input='$s'")
        }
    }

    @Test
    fun multiBlockInput() {
        // > 64 bytes forces multi-block processing.
        val long = "x".repeat(1000)
        assertEquals(
            "44f8354494a5ba03ba1792a8d3e9c534c47a9181980fde7a3f44b06ef2ae7c7f",
            sha256Hex(long),
        )
    }
}
