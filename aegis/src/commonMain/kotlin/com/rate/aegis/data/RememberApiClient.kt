package com.rate.aegis.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.settings.AegisSettingsStore

/**
 * Single source of truth for getting an [ApiClient] with the operator's saved
 * server URL. Surfaces should prefer this over `remember { ApiClient() }`.
 *
 * Reads `AegisSettingsStore.load().serverBaseUrl` once at first composition.
 * If the operator edits the URL in SettingsSurface, the next time the host
 * surface enters composition the new URL takes effect; live surfaces don't
 * re-create their client mid-session (that would drop in-flight requests).
 */
@Composable
fun rememberApiClient(): ApiClient {
    val baseUrl = AegisSettingsStore.load().serverBaseUrl
    return remember(baseUrl) { ApiClient(baseUrl) }
}
