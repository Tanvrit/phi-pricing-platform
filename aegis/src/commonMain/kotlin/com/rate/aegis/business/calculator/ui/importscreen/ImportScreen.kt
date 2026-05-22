package com.rate.aegis.business.calculator.ui.importscreen

import androidx.compose.runtime.Composable
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.business.calculator.navigation.Screen

/**
 * Excel import surface — the only operator screen still tied to a host platform.
 * JVM target uses `java.awt.FileDialog` to pick the workbook off the local disk;
 * the WASM target has no file-system access and renders a placeholder until we
 * wire a browser File API + ByteArray upload (next iteration if it lands soon).
 */
@Composable
expect fun ImportScreen(client: ApiClient, onNavigate: (Screen) -> Unit)
