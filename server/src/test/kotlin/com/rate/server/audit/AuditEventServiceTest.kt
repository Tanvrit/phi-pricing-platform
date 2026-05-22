package com.rate.server.audit

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

/**
 * Unit tests for the audit hash-chain primitives. Verifies:
 *   - canonical JSON is deterministic regardless of input key order
 *   - SHA-256 output is the expected 64 hex chars
 *   - Identical events produce identical hashes
 *   - Different events produce different hashes
 *   - The chain links: hash(prev || event) differs when prev changes
 */
class AuditEventServiceTest {

    private val t0 = Instant.parse("2026-05-20T10:00:00Z")
    private val sampleSubject = "alice@example.com"

    @Test
    fun `canonicalJson produces stable digest for same logical event`() {
        val payloadA = JsonObject(mapOf("planId" to JsonPrimitive("PHI_BASIC"), "amount" to JsonPrimitive(1500)))
        val payloadB = JsonObject(mapOf("amount" to JsonPrimitive(1500), "planId" to JsonPrimitive("PHI_BASIC")))

        val ca = AuditEventService.canonicalJson(t0, sampleSubject, "agent", "quote.created", "quote", "Q-1", payloadA, "req-1")
        val cb = AuditEventService.canonicalJson(t0, sampleSubject, "agent", "quote.created", "quote", "Q-1", payloadB, "req-1")

        ca shouldBe cb
        AuditEventService.sha256(ca) shouldBe AuditEventService.sha256(cb)
    }

    @Test
    fun `sha256 returns 64 hex characters`() {
        val digest = AuditEventService.sha256("hello world")
        digest shouldHaveLength 64
        digest shouldBe "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
    }

    @Test
    fun `different actions produce different hashes`() {
        val payload = buildJsonObject { put("planId", JsonPrimitive("PHI_BASIC")) }
        val h1 = AuditEventService.sha256(
            AuditEventService.canonicalJson(t0, sampleSubject, null, "quote.created", "quote", "Q-1", payload, null)
        )
        val h2 = AuditEventService.sha256(
            AuditEventService.canonicalJson(t0, sampleSubject, null, "quote.updated", "quote", "Q-1", payload, null)
        )
        h1 shouldNotBe h2
    }

    @Test
    fun `chain links via prev_hash + canonical event`() {
        val payload = JsonObject(mapOf("v" to JsonPrimitive(1)))
        val evt = AuditEventService.canonicalJson(t0, sampleSubject, null, "x", "y", "z", payload, null)

        val linkedToGenesis = AuditEventService.sha256(AuditEventService.GENESIS + evt)
        val linkedToOther   = AuditEventService.sha256("some-other-hash" + evt)

        linkedToGenesis shouldNotBe linkedToOther
    }

    @Test
    fun `null payload is treated identically across calls`() {
        val a = AuditEventService.canonicalJson(t0, null, null, "act", "res", null, null, null)
        val b = AuditEventService.canonicalJson(t0, null, null, "act", "res", null, null, null)
        a shouldBe b
    }

    @Test
    fun `nested payload key order does not affect hash`() {
        val payloadA = buildJsonObject {
            put("nested", buildJsonObject {
                put("z", JsonPrimitive(1))
                put("a", JsonPrimitive(2))
            })
            put("top", JsonPrimitive("v"))
        }
        val payloadB = buildJsonObject {
            put("top", JsonPrimitive("v"))
            put("nested", buildJsonObject {
                put("a", JsonPrimitive(2))
                put("z", JsonPrimitive(1))
            })
        }
        val ha = AuditEventService.sha256(
            AuditEventService.canonicalJson(t0, "u", null, "a", "r", "id", payloadA, null)
        )
        val hb = AuditEventService.sha256(
            AuditEventService.canonicalJson(t0, "u", null, "a", "r", "id", payloadB, null)
        )
        ha shouldBe hb
    }
}
