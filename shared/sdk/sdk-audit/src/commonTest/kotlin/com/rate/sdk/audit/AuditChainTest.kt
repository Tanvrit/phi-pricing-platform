package com.rate.sdk.audit

import com.rate.sdk.audit.handler.AuditChain
import com.rate.sdk.audit.model.AuditActor
import com.rate.sdk.audit.model.AuditEvent
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditChainTest {

    private val t0 = Instant.parse("2026-06-09T00:00:00Z")

    private fun chainOf(n: Int): List<AuditEvent> {
        val events = mutableListOf<AuditEvent>()
        var prev = AuditChain.GENESIS
        for (i in 1..n) {
            val e = AuditChain.nextEvent(
                seq = i.toLong(),
                prevHash = prev,
                action = "plan.published",
                entity = "plan",
                entityId = "PLAN-$i",
                actor = AuditActor(subject = "admin", role = "ADMIN"),
                payloadJson = """{"i":$i,"amount":1500}""",
                at = t0,
            )
            events += e
            prev = e.hash
        }
        return events
    }

    @Test
    fun buildAndVerifyValidChain() {
        val events = chainOf(5)
        assertEquals(AuditChain.GENESIS, events.first().prevHash)
        assertEquals(events[0].hash, events[1].prevHash)
        val result = AuditChain.verifyChain(events)
        assertTrue(result.ok, result.reason)
        assertEquals(5, result.eventsChecked)
    }

    @Test
    fun emptyChainVerifies() {
        val result = AuditChain.verifyChain(emptyList())
        assertTrue(result.ok)
        assertEquals(0, result.eventsChecked)
    }

    @Test
    fun detectsTamperedPayload() {
        val events = chainOf(3).toMutableList()
        // Mutate the middle event's payload without recomputing its hash.
        events[1] = events[1].copy(payloadJson = """{"i":999,"amount":1500}""")
        val result = AuditChain.verifyChain(events)
        assertFalse(result.ok)
        assertEquals(2L, result.breakAtSeq)
    }

    @Test
    fun detectsBrokenPrevHashLink() {
        val events = chainOf(3).toMutableList()
        events[2] = events[2].copy(prevHash = "BOGUS")
        val result = AuditChain.verifyChain(events)
        assertFalse(result.ok)
        assertEquals(3L, result.breakAtSeq)
    }

    @Test
    fun detectsSequenceGap() {
        val events = chainOf(3).toMutableList()
        // Drop the middle event -> seq jumps 1,3.
        events.removeAt(1)
        val result = AuditChain.verifyChain(events)
        assertFalse(result.ok)
        assertEquals(3L, result.breakAtSeq)
    }

    @Test
    fun hashIsDeterministicForSameContent() {
        val a = chainOf(2)
        val b = chainOf(2)
        assertEquals(a.map { it.hash }, b.map { it.hash })
    }
}
