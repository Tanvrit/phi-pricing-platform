package com.rate.sdk.audit.di

import com.rate.sdk.audit.event.AuditBroadcast
import com.rate.sdk.audit.event.InProcessAuditBroadcast
import com.rate.sdk.audit.handler.AuditRecorder
import com.rate.sdk.audit.repository.AuditStore
import com.rate.sdk.audit.repository.SequenceCounter
import org.koin.dsl.module

/**
 * Koin wiring for sdk-audit.
 *
 * What this module DOES provide (pure-KMP, no platform deps):
 *  - [AuditBroadcast] → [InProcessAuditBroadcast] (the live-feed fan-out).
 *  - [AuditRecorder] composed from the bound [AuditStore], [SequenceCounter] and
 *    [AuditBroadcast].
 *
 * What it does NOT provide (intentionally): the Mongo-backed [AuditStore],
 * [SequenceCounter] and [com.rate.sdk.audit.repository.IdempotencyStore] actuals — those
 * are bound by server-persistence (the only layer that may touch the driver). Importing
 * [auditModule] WITHOUT also binding those ports will fail fast at resolution time, which
 * is the desired signal that the persistence layer must be present.
 */
fun auditModule() = module {
    single<AuditBroadcast> { InProcessAuditBroadcast() }
    single {
        AuditRecorder(
            store = get<AuditStore>(),
            sequence = get<SequenceCounter>(),
            broadcast = get<AuditBroadcast>(),
        )
    }
}
