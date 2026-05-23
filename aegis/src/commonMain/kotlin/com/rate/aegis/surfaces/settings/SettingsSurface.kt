package com.rate.aegis.surfaces.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisChip
import com.rate.aegis.components.AegisInput
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.business.calculator.api.ApiOperator
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.settings.AegisSettings
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aegis SETTINGS surface — view + edit the per-device operator settings.
 *
 * Persists via [AegisSettingsStore] (`~/.aegis/settings.json` on JVM,
 * `localStorage["aegis.settings"]` on WASM). Values changed here DO NOT take
 * effect for already-mounted surfaces in the current session — they're read
 * fresh on the next page open. The Save callout makes that contract explicit.
 *
 * NEXT ITERATION: wire `LiveDashboardRepo` (and the operator `ApiClient` default
 * baseUrl in `business/calculator/api/ApiClient.kt`) to read
 * `AegisSettingsStore.load().serverBaseUrl` instead of hardcoding
 * `http://localhost:9090`. The role wiring belongs in `jvmMain/Main.kt`
 * (replace the `-Daegis.role=...` fallback) and `wasmJsMain/Main.kt`
 * (default `roleFromQueryString` to the stored value).
 */
@Composable
fun SettingsSurface() {
    // Snapshot what's currently persisted; track it as state so a successful
    // Save can refresh it without an actual disk/localStorage round-trip — what
    // we just wrote IS the truth. The `dirty` flag below derives from this.
    var persisted by remember { mutableStateOf(AegisSettingsStore.load()) }

    var serverBaseUrl by remember { mutableStateOf(persisted.serverBaseUrl) }
    var defaultRole by remember { mutableStateOf(persisted.defaultRole) }
    var theme by remember { mutableStateOf(normaliseTheme(persisted.theme)) }
    var operatorIdentity by remember { mutableStateOf(persisted.operatorIdentity) }
    var saveBannerShown by remember { mutableStateOf(false) }

    // Auto-hide the SUCCESS callout a few seconds after it appears so the
    // surface doesn't stay in a "just saved" mood forever.
    LaunchedEffect(saveBannerShown) {
        if (saveBannerShown) {
            delay(3_500L)
            saveBannerShown = false
        }
    }

    val urlError: String? = validateBaseUrl(serverBaseUrl)
    val dirty = serverBaseUrl != persisted.serverBaseUrl ||
            defaultRole != persisted.defaultRole ||
            theme != normaliseTheme(persisted.theme) ||
            operatorIdentity != persisted.operatorIdentity
    val canSave = dirty && urlError == null

    Column(
        Modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        Text(
            "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = AegisColors.textBody,
        )
        Text(
            "These values are saved locally — on this device/browser. They don't roam between machines.",
            fontSize = 13.sp,
            color = AegisColors.textSecondary,
        )

        if (saveBannerShown) {
            AegisCallout(
                kind = CalloutKind.SUCCESS,
                title = "Saved",
                body = "Changes apply on next page open.",
            )
        }

        // ── Server base URL ───────────────────────────────────────────────
        AegisCard(
            title = "Server base URL",
            subtitle = "Where Aegis goes for live data (dashboard, quotes, audit, calculator).",
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                AegisInput(
                    value = serverBaseUrl,
                    onValueChange = { serverBaseUrl = it.trim() },
                    label = "Server base URL",
                    helper = "e.g. http://localhost:9090 or https://aegis-staging.internal",
                    placeholder = "http://localhost:9090",
                    error = urlError,
                )
            }
        }

        // ── Default role ─────────────────────────────────────────────────
        AegisCard(
            title = "Default role",
            subtitle = "Role Aegis boots into when launched without an override.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                ROLE_OPTIONS.forEach { role ->
                    AegisChip(
                        label = role,
                        selected = defaultRole == role,
                        onClick = { defaultRole = role },
                    )
                }
            }
        }

        // ── Operator identity ────────────────────────────────────────────
        // Sent verbatim as the `X-Aegis-Actor` request header by ApiClient,
        // which the server stamps onto every audit_event row created from
        // this device. Blank = "unknown" (server falls back to AuditActor.unknown()).
        // No strict validation — we accept anything the operator types — but
        // emails are nudged in the callout below since they're easy to triage.
        AegisCard(
            title = "Operator identity",
            subtitle = "Who Aegis attributes audit events to from this device.",
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                AegisInput(
                    value = operatorIdentity,
                    onValueChange = { operatorIdentity = it },
                    label = "Identity (your name or email)",
                    helper = "Stamped on audit events created from this device. Leave blank to attribute as 'unknown'.",
                    placeholder = "jane.doe@example.com",
                )
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Emails recommended",
                    body = "An email or employee id makes audit history easier to triage. " +
                            "This is local until real auth lands — anyone can type anything here.",
                )
            }
        }

        // ── Theme ───────────────────────────────────────────────────────
        AegisCard(
            title = "Theme",
            subtitle = "Palette used by the operator surfaces (Home, Quotes, Plans, …).",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    THEME_OPTIONS.forEach { (id, label) ->
                        AegisChip(
                            label = label,
                            selected = theme == id,
                            onClick = { theme = id },
                        )
                    }
                }
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Heads up",
                    body = "Theme changes apply on next page open.",
                )
            }
        }

        // ── Manage operators (admin-gated) ──────────────────────────────
        // Local visibility check only; server enforces real RBAC.
        if (defaultRole.uppercase() == "ADMIN") {
            ManageOperatorsCard()
        }

        // ── Action footer ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
        ) {
            AegisButton(
                label = "Save",
                onClick = {
                    val next = AegisSettings(
                        serverBaseUrl = serverBaseUrl,
                        defaultRole = defaultRole,
                        theme = theme,
                        // Trim to avoid sneaking whitespace into the X-Aegis-Actor
                        // header; a pure-whitespace input collapses to "" (= unknown).
                        operatorIdentity = operatorIdentity.trim(),
                    )
                    AegisSettingsStore.save(next)
                    // Refresh the "persisted" snapshot so `dirty` flips back to
                    // false without a disk/localStorage round-trip. We don't
                    // read the file back — the values we just wrote ARE the truth.
                    persisted = next
                    saveBannerShown = true
                },
                enabled = canSave,
                variant = AegisButtonVariant.Primary,
            )
            AegisButton(
                label = "Reset to defaults",
                onClick = {
                    // In-memory reset only; user still has to hit Save to persist.
                    val d = AegisSettings()
                    serverBaseUrl = d.serverBaseUrl
                    defaultRole = d.defaultRole
                    theme = normaliseTheme(d.theme)
                    operatorIdentity = d.operatorIdentity
                },
                variant = AegisButtonVariant.Ghost,
            )
        }

        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Where used",
            body = "Server URL is consumed by the dashboard fetcher (LiveDashboardRepo) " +
                    "and the operator API client. Default role is the role the desktop " +
                    "binary boots into when launched without -Daegis.role=…, and the URL " +
                    "query parameter for the web (?role=…). Wiring lands in the next iteration.",
        )
    }
}

/** Validates a user-entered base URL. Returns null when ok, an error message otherwise. */
private fun validateBaseUrl(value: String): String? {
    val v = value.trim()
    if (v.isEmpty()) return "Required."
    if (!(v.startsWith("http://") || v.startsWith("https://"))) {
        return "Must start with http:// or https://"
    }
    // Reject just-a-scheme inputs like "http://" with nothing after.
    val afterScheme = v.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isBlank()) return "Missing host after scheme."
    return null
}

private val ROLE_OPTIONS = listOf("CUSTOMER", "BUSINESS", "ADMIN")

/**
 * Theme options surfaced as chips. We keep the persisted id ("light"/"dark")
 * separate from the chip label so the JSON stays human-grep-friendly and so a
 * future "dim" / "high-contrast" entry slots in without breaking older saves.
 */
private val THEME_OPTIONS: List<Pair<String, String>> = listOf(
    "light" to "Light",
    "dark" to "Dark",
)

/**
 * Normalises a persisted theme id to one of the known [THEME_OPTIONS] keys.
 * Anything unrecognised falls back to "light" — matches the resolver in
 * [com.rate.aegis.theme.AegisTheme].
 */
private fun normaliseTheme(raw: String): String {
    val v = raw.lowercase()
    return if (THEME_OPTIONS.any { it.first == v }) v else "light"
}

/** Five scopes the server gates today. New scopes added here when they ship. */
private val OPERATOR_SCOPES = listOf(
    "plans.write", "plans.delete", "import.upload", "audit.verify", "operators.write"
)

/**
 * Admin-only "Manage operators" card. Lists the server's operator allowlist,
 * supports add + remove. Bootstrap-permissive on the server (empty store = open)
 * so the first admin can grant themselves rights without out-of-band file edits.
 */
@Composable
private fun ManageOperatorsCard() {
    val client = rememberApiClient()
    val scope = rememberCoroutineScope()
    var operators by remember { mutableStateOf<List<ApiOperator>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true; error = null
        runCatching { client.listOperators() }
            .onSuccess { operators = it }
            .onFailure { error = "Server unreachable: ${it.message}" }
        loading = false
    }

    LaunchedEffect(client) { refresh() }

    var newIdentity by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("operator") }
    var newScopes by remember { mutableStateOf<Set<String>>(setOf("plans.write")) }

    AegisCard(
        title = "Manage operators",
        subtitle = "Identities listed here can write plans, manage operators, and trigger imports. Empty list = bootstrap mode (permissive).",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            when {
                error != null -> AegisCallout(
                    kind = CalloutKind.DANGER,
                    title = "Couldn't load operators",
                    body = error!!,
                )
                loading && operators.isEmpty() -> AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Loading…",
                    body = "Fetching operator allowlist from the server.",
                )
                operators.isEmpty() -> AegisCallout(
                    kind = CalloutKind.WARN,
                    title = "Unbootstrapped",
                    body = "No operators on the server. Every mutation is currently permissive. Add yourself below to lock things down.",
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    operators.forEach { op ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                        ) {
                            Text(
                                op.identity,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AegisColors.textBody,
                                modifier = Modifier.widthIn(min = 200.dp),
                            )
                            AegisChip(label = op.role, selected = op.role == "admin", onClick = {})
                            Text(
                                op.scopes.sorted().joinToString(", ").ifBlank { "—" },
                                fontSize = 12.sp,
                                color = AegisColors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            AegisButton(
                                label = "Remove",
                                variant = AegisButtonVariant.Danger,
                                onClick = {
                                    scope.launch {
                                        runCatching { client.removeOperator(op.identity) }
                                            .onSuccess { refresh() }
                                            .onFailure { error = it.message }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Add new operator row
            AegisCallout(
                kind = CalloutKind.INFO,
                title = "Add an operator",
                body = "Identity matches the X-Aegis-Actor header (set via 'Operator identity' above).",
            )
            AegisInput(
                value = newIdentity,
                onValueChange = { newIdentity = it },
                label = "Identity",
                helper = "e.g. alice@pruhealth.example.in",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                Text("Role:", fontSize = 13.sp, color = AegisColors.textSecondary)
                listOf("operator", "admin").forEach { r ->
                    AegisChip(label = r, selected = newRole == r, onClick = { newRole = r })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                Text("Scopes:", fontSize = 13.sp, color = AegisColors.textSecondary)
                OPERATOR_SCOPES.forEach { s ->
                    AegisChip(
                        label = s,
                        selected = s in newScopes,
                        onClick = {
                            newScopes = if (s in newScopes) newScopes - s else newScopes + s
                        },
                    )
                }
            }
            AegisButton(
                label = "Add operator",
                variant = AegisButtonVariant.Primary,
                enabled = newIdentity.isNotBlank(),
                onClick = {
                    scope.launch {
                        val op = ApiOperator(
                            identity = newIdentity.trim(),
                            role = newRole,
                            scopes = if (newRole == "admin") emptySet() else newScopes,
                        )
                        runCatching { client.addOperator(op) }
                            .onSuccess {
                                newIdentity = ""
                                newScopes = setOf("plans.write")
                                refresh()
                            }
                            .onFailure { error = it.message }
                    }
                },
            )
        }
    }
}
