package com.rate.server.security

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

/**
 * Verifies the request-hashing primitive that powers idempotency-key reuse detection.
 * The DB-touching `check()` / `store()` paths get exercised by integration tests; this
 * suite isolates the pure hashing semantics that determine "same body" vs "different body".
 */
class IdempotencyHashTest {

    @Test
    fun `identical bodies produce identical hashes`() {
        val body = """{"mobile":"9876543210","tier":"PREMIER","sumInsured":1000000}"""
        val h1 = IdempotencyService.hashRequest(body)
        val h2 = IdempotencyService.hashRequest(body)
        h1 shouldBe h2
    }

    @Test
    fun `different bodies produce different hashes`() {
        val a = """{"mobile":"9876543210"}"""
        val b = """{"mobile":"9876543211"}"""
        IdempotencyService.hashRequest(a) shouldNotBe IdempotencyService.hashRequest(b)
    }

    @Test
    fun `hash is 64 hex characters (SHA-256)`() {
        val h = IdempotencyService.hashRequest("anything")
        h shouldHaveLength 64
        h shouldMatch Regex("^[0-9a-f]{64}$")
    }

    @Test
    fun `empty body has a stable hash`() {
        val h = IdempotencyService.hashRequest("")
        // SHA-256("") is a well-known constant.
        h shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    @Test
    fun `whitespace differences produce different hashes`() {
        // JSON parsers would treat these as equivalent, but the idempotency contract
        // is exact-bytes — clients are expected to canonicalise their bodies if they
        // want whitespace-insensitive replay.
        val a = """{"a":1}"""
        val b = """{ "a": 1 }"""
        IdempotencyService.hashRequest(a) shouldNotBe IdempotencyService.hashRequest(b)
    }

    @Test
    fun `unicode in body is handled (UTF-8 bytes)`() {
        val ascii = "hello"
        val devanagari = "नमस्ते"
        // Both must produce valid 64-hex strings (no crash on multi-byte UTF-8).
        IdempotencyService.hashRequest(ascii) shouldHaveLength 64
        IdempotencyService.hashRequest(devanagari) shouldHaveLength 64
        IdempotencyService.hashRequest(ascii) shouldNotBe IdempotencyService.hashRequest(devanagari)
    }
}
