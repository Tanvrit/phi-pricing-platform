package com.rate.aegis.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.GlobalScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * WASM actual — wires a browser-native `EventSource` to `/api/audit/stream` and
 * surfaces decoded events as a [Flow] of [AuditEventDto].
 *
 * Bridge approach
 * ---------------
 * Kotlin/Wasm can't capture a Kotlin lambda inside a `js(...)` block as a
 * callable (closures don't survive the interop boundary cleanly), so we use
 * the simplest robust alternative:
 *
 * 1. The JS side maintains a global queue at `window.__aegisAuditQueue` and
 *    pushes the raw `event.data` JSON string from each `audit` event into it.
 * 2. A Kotlin coroutine wakes every 250 ms, drains the queue (a single
 *    `splice(0)` is atomic in JS — no race), parses each entry, and emits to
 *    a [MutableSharedFlow] with a small replay buffer so consumers can be
 *    swapped without dropping events.
 *
 * The drain interval is 250 ms — fast enough to feel real-time in the UI,
 * slow enough that an idle stream costs ~zero CPU. Memory-poll-and-drain is
 * cheap; the only allocation per cycle is the spliced array.
 *
 * Failure modes
 * -------------
 * If `EventSource` construction throws (insecure context, exotic browser,
 * mixed-content block), [installEventSource] swallows the error and the
 * caller returns `null` so consumers fall back to polling. If the EventSource
 * later errors after a successful connect, the browser auto-reconnects with
 * its default 3 s back-off; we don't need to wire reconnection ourselves.
 */
@Suppress("OPT_IN_USAGE")
actual fun openAuditStream(baseUrl: String): Flow<AuditEventDto>? {
    val installed = runCatching { installEventSource("$baseUrl/api/audit/stream") }
        .getOrElse { return null }
    if (!installed) return null

    // Small replay so a late-collecting Compose surface still sees the
    // last few events that arrived while it was composing. extraBufferCapacity
    // absorbs bursts (e.g. plan import emits dozens of audit rows in flight).
    val flow = MutableSharedFlow<AuditEventDto>(
        replay = 4,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Drain loop lives on GlobalScope because the Flow is conceptually a
    // process-wide live wire — surfaces come and go but the EventSource
    // (and its queue) persists across re-compositions. Cancellation is
    // implicit on tab close. We swallow parse errors so one malformed event
    // can't poison the rest of the stream.
    GlobalScope.launch {
        while (coroutineContext.isActive) {
            val drained = drainAuditQueue()
            if (drained.isNotEmpty()) {
                for (raw in drained) {
                    val dto = runCatching {
                        Json.decodeFromString(AuditEventDto.serializer(), raw)
                    }.getOrNull()
                    if (dto != null) flow.tryEmit(dto)
                }
            }
            try {
                delay(250L)
            } catch (_: CancellationException) {
                throw CancellationException("audit stream drain cancelled")
            }
        }
    }

    return flow.asSharedFlow()
}

/**
 * Install (idempotently) a single `EventSource` on the page and start pushing
 * `event.data` payloads into `window.__aegisAuditQueue`. Returns `true` on
 * success — including when an existing wire is already in place from a prior
 * call (re-composition, surface swap). Returns `false` on browsers without
 * `EventSource`.
 *
 * Kotlin/Wasm requires the `js(...)` body to be a compile-time string at the
 * top of a function body — we therefore keep this helper minimal.
 */
private fun installEventSource(url: String): Boolean =
    js(
        "{ " +
            "if (typeof EventSource === 'undefined') return false; " +
            "if (!window.__aegisAuditQueue) { window.__aegisAuditQueue = []; } " +
            "if (window.__aegisAuditSrc && window.__aegisAuditSrc.url === url) { return true; } " +
            "if (window.__aegisAuditSrc) { try { window.__aegisAuditSrc.close(); } catch (e) {} } " +
            "try { " +
                "var src = new EventSource(url); " +
                "src.addEventListener('audit', function(ev) { " +
                    "try { window.__aegisAuditQueue.push(ev.data); } catch (e) { console.error('aegis: audit push failed', e); } " +
                "}); " +
                "src.addEventListener('error', function(ev) { console.warn('aegis: audit stream error, browser will retry'); }); " +
                "window.__aegisAuditSrc = src; " +
                "return true; " +
            "} catch (e) { console.error('aegis: EventSource init failed', e); return false; } " +
        "}"
    )

/**
 * Atomic drain of the JS-side queue. Returns the buffered JSON strings in
 * insertion order and resets the queue to empty in a single statement so a
 * push concurrent with the drain can't be lost.
 *
 * The returned value crosses the Kotlin/Wasm boundary as a `JsArray<JsString>`
 * which we materialise into a Kotlin `List<String>` inside [drainAuditQueue]
 * via index iteration — the most portable shape across Kotlin/Wasm versions.
 */
private fun drainAuditQueue(): List<String> {
    val size = drainSize()
    if (size <= 0) return emptyList()
    val out = ArrayList<String>(size)
    for (i in 0 until size) {
        out.add(drainAt(i))
    }
    // Now that we've copied everything out, clear the staging slot so the next
    // tick starts fresh. Done in JS to keep the splice/clear atomic from the
    // EventSource handler's point of view.
    clearStaging()
    return out
}

/**
 * Move the queue contents into a staging slot and return the new staging
 * length. Splitting the drain into "move + length" + "indexed read" lets us
 * avoid trying to return a JS array directly into Kotlin (the typed bridge
 * for that is awkward across Kotlin/Wasm versions).
 */
private fun drainSize(): Int =
    js(
        "{ " +
            "if (!window.__aegisAuditQueue) return 0; " +
            "window.__aegisAuditStaging = window.__aegisAuditQueue; " +
            "window.__aegisAuditQueue = []; " +
            "return window.__aegisAuditStaging.length; " +
        "}"
    )

private fun drainAt(index: Int): String =
    js("(window.__aegisAuditStaging && window.__aegisAuditStaging[index]) || ''")

private fun clearStaging(): Unit =
    js("{ window.__aegisAuditStaging = null; }")
