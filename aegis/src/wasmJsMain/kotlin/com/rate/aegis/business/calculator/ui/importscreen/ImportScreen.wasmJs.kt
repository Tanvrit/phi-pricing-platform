package com.rate.aegis.business.calculator.ui.importscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.business.calculator.navigation.Screen
import kotlinx.coroutines.launch

/**
 * WASM ImportScreen — no file-system access in the browser, so Excel upload is
 * not available here. The "Seed built-in data" path still works (server-side
 * operation), and that's surfaced as a button.
 */
@Composable
actual fun ImportScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Import", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Excel upload is not available in the browser. Use the desktop binary " +
                    "to import a workbook, or seed the built-in actuarial defaults from here.",
            fontSize = 13.sp
        )
        OutlinedButton(onClick = {
            scope.launch { runCatching { client.seedBuiltinData() } }
        }) { Text("Seed built-in data") }
        OutlinedButton(onClick = { onNavigate(Screen.Calculator) }) {
            Text("Back to Calculator")
        }
    }
}
