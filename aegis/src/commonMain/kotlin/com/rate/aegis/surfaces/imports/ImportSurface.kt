package com.rate.aegis.surfaces.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.LocalRefreshTicker
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonSize
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Aegis IMPORT surface — operator-facing Excel rate-workbook upload.
 *
 * Replaces the `SurfaceTodo` fall-through for [AegisSurface.IMPORT] in the
 * BUSINESS / ADMIN shells. Coexists with the legacy
 * `business.calculator.ui.importscreen.ImportScreen` (still wired into the
 * legacy in-shell navigation) — both target the same `POST /api/import/upload`
 * server endpoint, just with different UI affordances.
 *
 * Cross-platform plumbing
 * -----------------------
 * The actual byte-getting bit is delegated to the expect/actual [pickFile]:
 *  - JVM uses AWT FileDialog (native picker on every desktop OS).
 *  - WASM uses a hidden `<input type="file">` + FileReader polled via a
 *    `js(...)` queue (closures can't cross the Kotlin/Wasm boundary, so the
 *    EventSource-style "stage to window, poll from Kotlin" recipe applies).
 *
 * The uploaded ByteArray then flows through [ApiClient.uploadExcelBytes],
 * which is itself a multiplatform `formData { append(name, ByteArray, …) }`
 * — no `java.io.File` reach — so the entire pick → upload → confirm loop is
 * identical on desktop and browser.
 *
 * Refresh contract
 * ----------------
 * A successful upload mutates server-side rate tables in place, which means
 * any other surface caching plan/cover data is now stale. We bump
 * [LocalRefreshTicker] on success so the global header Refresh handler
 * is implicitly invoked across every surface that keys its LaunchedEffect on
 * that ticker (Calculator, Plan Configurator, Server Health, …).
 *
 * Scope gating is server-side: `POST /api/import/upload` requires the
 * `import.upload` scope on the calling operator. A scopeless caller gets the
 * standard 403 JSON body, which surfaces here as a DANGER callout with the
 * status code in the message — same UX as `verifyAuditChain()`.
 */
@Composable
fun ImportSurface() {
    val client = rememberApiClient()
    val scope = rememberCoroutineScope()
    val refreshTicker = LocalRefreshTicker.current

    var picked by remember { mutableStateOf<PickedFile?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var seeding by remember { mutableStateOf(false) }
    // Two callout slots — success carries a server-reported message string,
    // error carries either a network exception message or the server's 4xx
    // body. We use separate state because a brand-new pick after a failure
    // should clear the error without clobbering the previous success.
    var successMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Auto-hide the success callout after a few seconds so the surface doesn't
    // sit in "just imported" mood forever — same pattern as Settings.
    LaunchedEffect(successMsg) {
        if (successMsg != null) {
            delay(8_000L)
            successMsg = null
        }
    }

    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Import",
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "Upload an Excel rate workbook. Server parses and updates rate tables atomically.",
            fontSize = 13.sp, color = AegisColors.textSecondary,
        )

        // ── Status banners ──────────────────────────────────────────────
        // Order matters: a fresh error (most recent action) wins screen real
        // estate above any lingering success from a previous upload.
        errorMsg?.let {
            AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Import failed",
                body = it,
            )
        }
        successMsg?.let {
            AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Workbook imported",
                body = it,
            )
        }

        // ── Drop-zone-style card ────────────────────────────────────────
        AegisCard(
            title = "Upload workbook",
            subtitle = "Accepts .xls, .xlsx, .xlsm — typically Rate_Calculator_v13.0.xlsm",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
                // Visual drop-zone surface. We don't wire HTML5 drag-and-drop
                // here (a future iteration) — the button is the canonical
                // affordance on both platforms.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(AegisColors.surfaceMuted, RoundedCornerShape(AegisRadii.rMd))
                        .border(
                            1.dp, AegisColors.border,
                            RoundedCornerShape(AegisRadii.rMd),
                        )
                        .padding(AegisSpacing.s6),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                    ) {
                        val current = picked
                        if (current == null) {
                            Text(
                                "No file selected.",
                                fontSize = 14.sp,
                                color = AegisColors.textSecondary,
                            )
                            Text(
                                "Pick an Excel workbook to upload. The server replaces all rate tables on success.",
                                fontSize = 12.sp,
                                color = AegisColors.textTertiary,
                            )
                        } else {
                            Text(
                                current.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AegisColors.textPrimary,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                formatBytes(current.bytes.size),
                                fontSize = 12.sp,
                                color = AegisColors.textSecondary,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AegisButton(
                                label = if (picked == null) "Pick file…" else "Pick another…",
                                variant = AegisButtonVariant.Secondary,
                                enabled = !uploading,
                                onClick = {
                                    // Picker can suspend (WASM polls a FileReader),
                                    // so launch and let the click handler return
                                    // immediately. Errors here are limited to "no
                                    // browser support" — we treat them as cancels.
                                    scope.launch {
                                        errorMsg = null
                                        val result = runCatching {
                                            pickFile(
                                                filterDescription = "Excel workbooks",
                                                extensions = listOf("xls", "xlsx", "xlsm"),
                                            )
                                        }.getOrNull()
                                        if (result != null) picked = result
                                    }
                                },
                            )
                            AegisButton(
                                label = "Upload",
                                variant = AegisButtonVariant.Primary,
                                enabled = picked != null && !uploading,
                                loading = uploading,
                                onClick = {
                                    val file = picked ?: return@AegisButton
                                    scope.launch {
                                        uploading = true
                                        errorMsg = null
                                        runCatching {
                                            client.uploadExcelBytes(file.bytes, file.name)
                                        }.onSuccess { payload ->
                                            successMsg = payload.serverMessage()
                                                ?: "Workbook ingested successfully."
                                            // Drop the staged bytes so the
                                            // operator can't accidentally
                                            // re-upload the same buffer twice.
                                            picked = null
                                            // Fan-out refresh signal to every
                                            // surface that caches plans / covers.
                                            refreshTicker.value += 1
                                        }.onFailure { t ->
                                            errorMsg = t.message
                                                ?: t::class.simpleName
                                                ?: "Unknown upload error."
                                        }
                                        uploading = false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Secondary: seed built-in data ───────────────────────────────
        AegisCard(
            title = "Seed built-in data",
            subtitle = "Loads the in-source actuarial defaults bundled with the server build.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Text(
                    "Use this when bootstrapping a fresh database or when no Excel workbook is " +
                        "available. The seed dataset is the same one the desktop calculator falls " +
                        "back to when the server is offline.",
                    fontSize = 13.sp,
                    color = AegisColors.textSecondary,
                )
                AegisButton(
                    label = "Seed built-in data",
                    variant = AegisButtonVariant.Secondary,
                    size = AegisButtonSize.Md,
                    enabled = !seeding && !uploading,
                    loading = seeding,
                    onClick = {
                        scope.launch {
                            seeding = true
                            errorMsg = null
                            runCatching { client.seedBuiltinData() }
                                .onSuccess { payload ->
                                    successMsg = payload.serverMessage()
                                        ?: "Built-in defaults seeded."
                                    refreshTicker.value += 1
                                }
                                .onFailure { t ->
                                    errorMsg = t.message
                                        ?: t::class.simpleName
                                        ?: "Seed failed."
                                }
                            seeding = false
                        }
                    },
                )
            }
        }

        // Pointer to where the import ends up — useful context for first-time
        // operators who otherwise have to guess which surface to inspect after
        // a successful upload.
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "After import",
            body = "Refreshable surfaces (Calculator, Plan Configurator, Server Health) " +
                "re-fetch automatically. Inspect ingest details under Audit → events.",
        )
    }
}

// ────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────

/**
 * Pull the conventional `{"message":"…"}` field out of the server's
 * import-result JSON. Falls back to null so the caller can supply a default
 * string. The legacy ImportScreen does `res["message"]?.toString()`, which
 * incorrectly includes the surrounding `"` quotes for JsonPrimitive strings;
 * here we use `contentOrNull` so the operator sees `Imported 14 plans`
 * rather than `"Imported 14 plans"`.
 */
private fun Map<String, JsonElement>.serverMessage(): String? {
    val raw = this["message"] ?: return null
    return if (raw is JsonPrimitive) raw.contentOrNull else raw.toString()
}

/**
 * Compact byte-count formatter. We don't pull in a real units library — the
 * three buckets (B / KB / MB) cover every rate workbook we've ever seen
 * (typical .xlsm is ~1-3 MB, the v13 workbook is 1.6 MB).
 */
internal fun formatBytes(n: Int): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> {
        val kb = n / 1024.0
        // One decimal place via integer math — keeps us off `String.format`,
        // which has different behaviour on JVM vs. WASM.
        val tenths = (kb * 10.0).toInt()
        "${tenths / 10}.${tenths % 10} KB"
    }
    else -> {
        val mb = n / (1024.0 * 1024.0)
        val tenths = (mb * 10.0).toInt()
        "${tenths / 10}.${tenths % 10} MB"
    }
}
