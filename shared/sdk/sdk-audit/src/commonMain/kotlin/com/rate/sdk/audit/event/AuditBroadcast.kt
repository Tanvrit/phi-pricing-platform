package com.rate.sdk.audit.event

import com.rate.sdk.audit.model.AuditEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Live, best-effort fan-out of freshly-appended audit events for "tail the audit log"
 * surfaces (operator console feed, SSE/WebSocket bridges in the server).
 *
 * This is a CONTRACT + a default in-process implementation. The persisted log in the
 * [com.rate.sdk.audit.repository.AuditStore] is the source of truth and is fully
 * verifiable via [com.rate.sdk.audit.handler.AuditChain.verifyChain]; this broadcast is
 * a convenience push, NOT a delivery guarantee. Late subscribers should bootstrap from
 * [com.rate.sdk.audit.repository.AuditStore.listBySeq] and then [subscribe].
 */
interface AuditBroadcast {
    /** Cold, multicast view of newly-appended events; each `collect` is independent. */
    fun subscribe(): SharedFlow<AuditEvent>

    /** Non-suspending publish from the append path; drops on overflow (see impl). */
    fun publish(event: AuditEvent)
}

/**
 * Default [AuditBroadcast]: a [MutableSharedFlow] with no replay and DROP_OLDEST overflow.
 *
 * Rationale (carried over from the old AuditEventService):
 *  - replay = 0: late subscribers don't get history (bootstrap from the store instead).
 *  - extraBufferCapacity = 64: small headroom for transient consumer slowness.
 *  - DROP_OLDEST: a stuck subscriber can NEVER backpressure the audit write path —
 *    losing a live-feed frame is acceptable; stalling a quote save is not.
 */
class InProcessAuditBroadcast(
    extraBufferCapacity: Int = 64,
) : AuditBroadcast {

    private val flow = MutableSharedFlow<AuditEvent>(
        replay = 0,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun subscribe(): SharedFlow<AuditEvent> = flow.asSharedFlow()

    override fun publish(event: AuditEvent) {
        // tryEmit is non-suspending; drops on overflow per DROP_OLDEST.
        flow.tryEmit(event)
    }
}
