package com.rate.aegis.surfaces.imports

import kotlinx.coroutines.delay

/**
 * WASM actual — drive a hidden `<input type="file">` via small `js(...)` helpers.
 *
 * Bridge approach
 * ---------------
 * Kotlin/Wasm can't pass a Kotlin lambda into the JS side as a callable that
 * survives the interop boundary cleanly (the same constraint that drives the
 * EventSource bridge in [AuditStream.wasmJsMain]). We use a queue-and-poll
 * pattern instead:
 *
 * 1. [startFilePick] creates / reuses a hidden `<input type="file">` rooted at
 *    `window.__aegisFilePick`, attaches `change` + `cancel` listeners that
 *    write the chosen `File` (and read its `ArrayBuffer`) into the same global
 *    slot, then programmatically calls `input.click()`.
 *
 * 2. The Kotlin side polls [filePickState] every 60 ms — fast enough to feel
 *    instant once the user picks a file, slow enough to be free CPU-wise while
 *    waiting (the user can sit on the OS file dialog for arbitrary time).
 *
 * 3. When state flips to `"done"` or `"cancelled"`, we read the filename via
 *    [filePickName] and the bytes via index-walking [filePickByteAt] over a
 *    [filePickByteLength] count. Index-by-index is verbose but is the most
 *    portable shape across Kotlin/Wasm versions — typed-array bridges to
 *    `ByteArray` are not yet officially exposed in commonMain.
 *
 * The `input.click()` call MUST happen synchronously inside a user-gesture
 * stack frame (a Compose `onClick` lambda is one). We don't fight Compose's
 * dispatcher — by the time this `expect` resolves to the actual, we're still
 * on the JS event-loop tick that the click handler started, and the synthetic
 * click on the input is therefore trusted by the browser's gesture heuristic.
 *
 * We use `FileReader.readAsArrayBuffer(...)` rather than the newer
 * `File.arrayBuffer()` Promise because Chromium-family browsers shipped the
 * Promise variant only in 2020+; the FileReader pattern works in every
 * relevant browser including the older corporate Edge/Chromium builds an
 * operator may be running. The performance difference for the few-MB Excel
 * workbooks we care about is negligible.
 */
actual suspend fun pickFile(
    filterDescription: String,
    extensions: List<String>,
): PickedFile? {
    // `filterDescription` is intentionally unused on WASM — the browser's file
    // picker shows the page-level filename input, not a custom title. Browsers
    // honour the `accept=` attribute on the input element for filtering.
    val accept = extensions.joinToString(",") { ext ->
        val cleaned = ext.lowercase().removePrefix(".")
        ".$cleaned"
    }
    if (!startFilePick(accept)) return null

    // Poll the JS-side state machine until the user picks a file or cancels.
    // 60 ms is below the human "instant" threshold (~100 ms) so the picked
    // file shows up immediately after the OS dialog closes.
    while (true) {
        val state = filePickState()
        when (state) {
            "done" -> {
                val name = filePickName()
                val len = filePickByteLength()
                val bytes = ByteArray(len)
                var i = 0
                while (i < len) {
                    bytes[i] = filePickByteAt(i).toByte()
                    i++
                }
                resetFilePick()
                return PickedFile(name = name, bytes = bytes)
            }
            "cancelled", "error" -> {
                resetFilePick()
                return null
            }
            else -> delay(60L) // "pending"
        }
    }
    @Suppress("UNREACHABLE_CODE") return null
}

/**
 * Build (or rebuild) the hidden `<input type="file">` and start the pick.
 * Returns `false` if the runtime is too old to host a `FileReader` /
 * `<input>` (no realistic browser fails this — guard is defence-in-depth).
 *
 * The `change` handler reads the file via `FileReader.readAsArrayBuffer` and
 * stashes the resulting `Uint8Array` in `window.__aegisFilePick.bytes`; the
 * `cancel` event (or a `change` with an empty FileList) flips the state to
 * `"cancelled"` so the Kotlin poll loop can return null.
 *
 * Some browsers (Safari < 16) don't fire the `cancel` event at all, so we
 * additionally treat a `change` event with `files.length === 0` as a cancel.
 * On those browsers, dismissing the OS dialog without picking simply leaves
 * the state in `"pending"` forever — the suspending caller would hang. We
 * sidestep that by also wiring a `window.focus` listener that, when the page
 * regains focus AND no file has been chosen within 500 ms, flips the state
 * to `"cancelled"`. Pragmatic, not perfect — but the import surface lets the
 * operator click "Pick file…" again, so a stuck poll is at worst a re-click.
 */
private fun startFilePick(accept: String): Boolean =
    js(
        "{ " +
            "if (typeof document === 'undefined' || typeof FileReader === 'undefined') return false; " +
            "var slot = window.__aegisFilePick; " +
            "if (slot && slot.input) { try { slot.input.remove(); } catch (e) {} } " +
            "slot = { state: 'pending', name: '', bytes: null, input: null }; " +
            "window.__aegisFilePick = slot; " +
            "var input = document.createElement('input'); " +
            "input.type = 'file'; " +
            "input.accept = accept; " +
            "input.style.display = 'none'; " +
            "input.addEventListener('change', function() { " +
                "var f = (input.files && input.files[0]) || null; " +
                "if (!f) { slot.state = 'cancelled'; return; } " +
                "slot.name = f.name || 'upload'; " +
                "var rdr = new FileReader(); " +
                "rdr.onload = function() { " +
                    "try { slot.bytes = new Uint8Array(rdr.result); slot.state = 'done'; } " +
                    "catch (e) { console.error('aegis: file read failed', e); slot.state = 'error'; } " +
                "}; " +
                "rdr.onerror = function() { slot.state = 'error'; }; " +
                "rdr.readAsArrayBuffer(f); " +
            "}); " +
            "input.addEventListener('cancel', function() { slot.state = 'cancelled'; }); " +
            // Safari-fallback: if the window regains focus and no change fired
            // within 500ms, treat the dialog as dismissed.
            "var onFocus = function() { " +
                "setTimeout(function() { " +
                    "if (slot.state === 'pending') { slot.state = 'cancelled'; } " +
                "}, 500); " +
                "window.removeEventListener('focus', onFocus); " +
            "}; " +
            "window.addEventListener('focus', onFocus); " +
            "document.body.appendChild(input); " +
            "slot.input = input; " +
            "input.click(); " +
            "return true; " +
        "}"
    )

private fun filePickState(): String =
    js("(window.__aegisFilePick && window.__aegisFilePick.state) || 'pending'")

private fun filePickName(): String =
    js("(window.__aegisFilePick && window.__aegisFilePick.name) || ''")

private fun filePickByteLength(): Int =
    js("(window.__aegisFilePick && window.__aegisFilePick.bytes && window.__aegisFilePick.bytes.length) || 0")

/**
 * One indexed byte read from the staged Uint8Array. Returns 0 if the index is
 * out of bounds (defensive — Kotlin caller already gates by [filePickByteLength]).
 * Returns Int because Kotlin/Wasm's `js(...)` doesn't have a `Byte` mapping;
 * the caller down-casts via `.toByte()`.
 */
private fun filePickByteAt(index: Int): Int =
    js("(window.__aegisFilePick && window.__aegisFilePick.bytes && window.__aegisFilePick.bytes[index]) || 0")

/**
 * Tear down the staged input + buffers so a subsequent pick starts fresh.
 * Idempotent; safe to call after [startFilePick] returned `false`.
 */
private fun resetFilePick(): Unit =
    js(
        "{ " +
            "if (window.__aegisFilePick && window.__aegisFilePick.input) { " +
                "try { window.__aegisFilePick.input.remove(); } catch (e) {} " +
            "} " +
            "window.__aegisFilePick = null; " +
        "}"
    )
