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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisButton
import com.rate.aegis.components.AegisButtonSize
import com.rate.aegis.components.AegisButtonVariant
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCard
import com.rate.aegis.components.AegisChip
import com.rate.aegis.components.AegisInput
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.business.calculator.api.ApiOperator
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.i18n.AegisLocale
import com.rate.aegis.settings.AegisSettings
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.aegis.surfaces.audit.MyAuditEvents
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.util.copyToClipboard
import com.rate.aegis.util.runtimeHostInfo
import com.rate.aegis.util.runtimeKind
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
    var locale by remember { mutableStateOf(normaliseLocale(persisted.locale)) }
    var muted by remember { mutableStateOf(persisted.mutedNotificationActions) }
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
            operatorIdentity != persisted.operatorIdentity ||
            locale != normaliseLocale(persisted.locale) ||
            muted != persisted.mutedNotificationActions
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

        // ── Your audit trail ─────────────────────────────────────────────
        // MyAuditEvents() already renders its own AegisCard (title
        // "My audit trail"), so we call it directly here rather than nest
        // cards. It self-handles the blank-identity case with an INFO callout.
        MyAuditEvents()

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

        // ── Language ────────────────────────────────────────────────────
        // Customer-journey only (buyonline). Operator surfaces stay English-only
        // in Phase 1 — the localization budget is Phase 2. Visible to every
        // role (NOT admin-gated) so customer-role browsers can flip locale too.
        AegisCard(
            title = "Language",
            subtitle = "Language used by the customer buy-online journey. Operator surfaces remain English.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                    AegisLocale.entries.forEach { entry ->
                        AegisChip(
                            label = entry.displayName,
                            selected = locale == entry.code,
                            onClick = { locale = entry.code },
                        )
                    }
                }
                AegisCallout(
                    kind = CalloutKind.INFO,
                    title = "Heads up",
                    body = "Language changes apply on next page open. Hindi coverage is partial; " +
                            "any missing translation falls back to English.",
                )
            }
        }

        // ── Notification preferences ─────────────────────────────────────
        // Per-event-type mute list for the bell-icon NotificationCenter
        // dropdown. The known-actions list is hardcoded (no polling) and the
        // mute set is snapshotted on first composition of `rememberNotifications`
        // — restart-or-recompose-to-apply, matching theme/locale. This card
        // ONLY affects the dropdown; the audit log and ActivityFeed are
        // untouched.
        AegisCard(
            title = "Notification preferences",
            subtitle = "Hide specific event types from the bell-icon dropdown. Doesn't affect the audit log itself.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                KNOWN_NOTIFICATION_ACTIONS.forEach { action ->
                    val isMuted = action in muted
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            action,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = AegisColors.textBody,
                        )
                        AegisChip(
                            label = if (isMuted) "Muted" else "Show",
                            selected = isMuted,
                            onClick = {
                                muted = if (isMuted) muted - action else muted + action
                            },
                        )
                    }
                }
            }
        }

        // ── Manage operators (admin-gated) ──────────────────────────────
        // Local visibility check only; server enforces real RBAC.
        if (defaultRole.uppercase() == "ADMIN") {
            ManageOperatorsCard()
        }

        // ── System info ──────────────────────────────────────────────────
        // Read-only diagnostic block. We render the persisted `serverBaseUrl`
        // (not the in-flight `serverBaseUrl` state) so the value reflects what
        // the running session is actually using — editing-but-not-saving the
        // URL above shouldn't change this row.
        SystemInfoCard(serverBaseUrl = persisted.serverBaseUrl)

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
                        locale = locale,
                        mutedNotificationActions = muted,
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
                    locale = normaliseLocale(d.locale)
                    muted = d.mutedNotificationActions
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
 * Known audit `action` strings the operator can mute from the NotificationCenter
 * dropdown. Hardcoded by design — we don't poll the server for the live set, so
 * shipping a new action type requires updating this list. Kept in roughly the
 * same order operators see them in the audit log so the card scans naturally.
 *
 * Muting an action only hides it from the bell-icon dropdown; AuditEventsSurface
 * and ActivityFeed still show every event regardless of this list.
 */
private val KNOWN_NOTIFICATION_ACTIONS = listOf(
    "plan.upserted", "plan.deleted",
    "quote.created", "quote.calculated",
    "session.email_requested",
    "outbox.purged",
    "audit.chain_verified", "audit.chain_broken",
    "import.uploaded",
)

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

/**
 * Normalises a persisted locale code ("en", "hi", …) to a recognised
 * [AegisLocale] code. Anything unknown falls back to English — matches the
 * resolver in [com.rate.aegis.i18n.AegisLocale.fromCode].
 */
private fun normaliseLocale(raw: String): String =
    AegisLocale.fromCode(raw).code

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

// ── System info card ──────────────────────────────────────────────────────
//
// Diagnostic surface for bug reports. Everything here is static or read from
// host APIs; nothing is sent off-device. Constants live next to the card
// because they're literally the data it renders — no need to spread them
// across the file.

/**
 * Aegis build tag. Hardcoded YYYY.MM.dev for now — once a real release pipeline
 * lands (changelog + tag), this gets replaced by a generated `BuildConfig.kt`.
 */
private const val AEGIS_VERSION = "2026.05.dev"

/**
 * Library versions we surface in the card. Kept as plain strings instead of
 * pulling from `KotlinVersion.CURRENT` etc. because (a) the Compose MP string
 * has no runtime API, and (b) keeping all three together makes drift between
 * the gradle catalog and the displayed values easier to spot in code review.
 */
private const val COMPOSE_MP_VERSION = "1.7.3"
private const val KOTLIN_VERSION = "2.1.0"

/**
 * Read-only "System info" card. Six rows of build + runtime context plus a
 * small Copy button that ships the same content as a tab-separated block to
 * the clipboard. Purely informational — operators include the blob in bug
 * reports so we know what host the issue was filed against.
 *
 * Receives [serverBaseUrl] from the caller rather than reading from
 * [AegisSettingsStore] directly so the rendered value matches what the
 * running session is using (the persisted snapshot, not the in-flight form).
 */
@Composable
private fun SystemInfoCard(serverBaseUrl: String) {
    val rows = listOf(
        "Aegis build" to AEGIS_VERSION,
        "Compose MP" to COMPOSE_MP_VERSION,
        "Kotlin" to KOTLIN_VERSION,
        "Runtime" to runtimeKind(),
        "Host" to runtimeHostInfo(),
        "Server URL" to serverBaseUrl,
    )
    // Inline copy-confirmation that auto-clears. Optimistic — the WASM
    // clipboard write is fire-and-forget, so a "Copied" badge here doesn't
    // promise the bytes actually landed in the OS clipboard.
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000L)
            copied = false
        }
    }

    AegisCard(
        title = "System info",
        subtitle = "Build + runtime context. Useful when filing a bug — include this in the report.",
        action = {
            AegisButton(
                label = if (copied) "Copied" else "Copy",
                variant = AegisButtonVariant.Ghost,
                size = AegisButtonSize.Sm,
                onClick = {
                    // Tab-separated so the block pastes cleanly into Slack /
                    // GitHub issue bodies without monospaced formatting.
                    val payload = rows.joinToString("\n") { (k, v) -> "$k\t$v" }
                    copied = copyToClipboard(payload)
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AegisColors.textSecondary,
                        modifier = Modifier.widthIn(min = 110.dp),
                    )
                    Text(
                        value.ifBlank { "—" },
                        fontSize = 12.sp,
                        color = AegisColors.textBody,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
