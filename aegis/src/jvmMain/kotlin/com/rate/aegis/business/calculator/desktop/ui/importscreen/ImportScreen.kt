package com.rate.aegis.business.calculator.desktop.ui.importscreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rate.aegis.business.calculator.desktop.api.ApiClient
import com.rate.aegis.business.calculator.desktop.navigation.Screen
import com.rate.aegis.business.calculator.desktop.ui.components.*
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun ImportScreen(client: ApiClient, onNavigate: (Screen) -> Unit) {
    val scope        = rememberCoroutineScope()
    var status       by remember { mutableStateOf<String?>(null) }
    var error        by remember { mutableStateOf<String?>(null) }
    var loading      by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        bottomBar = { AppNavBar(current = Screen.Import, onNavigate = onNavigate) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Import Rate Data", style = MaterialTheme.typography.headlineSmall)

            // Info banner
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Server required: http://localhost:9090",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Upload Rate Calculator v13.0 (.xlsm) to populate the server database with " +
                        "all 14 plan rate tables, 54 optional covers, cover availability matrix, " +
                        "instalment config, and all discounts. The server must be running before import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            error?.let  { ErrorBanner(it) }
            status?.let {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        it, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Upload v13.0 Excel
            SectionCard("Upload Rate Calculator v13.0 Excel File") {
                Text(
                    "Select the official Rate_Calculator_v13.0.xlsm file. " +
                    "This will replace all existing rate data in the database with fresh v13 data.\n\n" +
                    "Import will process: Ref sheet (14 plans) · All plan base rate sheets · " +
                    "Loadings & Discounts (54 covers) · Sheet1 (cover availability) · " +
                    "Sheet2 (instalment config).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value         = selectedFile?.name ?: "No file selected",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Excel File (.xlsm / .xlsx)") },
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { selectedFile = pickExcelFile() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Browse…") }

                    Button(
                        onClick = {
                            val f = selectedFile ?: run { error = "Select a file first"; return@Button }
                            scope.launch {
                                loading = true; error = null; status = null
                                try {
                                    val res = client.uploadExcel(f)
                                    status = res["message"]?.toString() ?: "Imported successfully"
                                } catch (e: Exception) {
                                    error = "Import failed — is the server running on port 9090?\n${e.message}"
                                } finally { loading = false }
                            }
                        },
                        enabled  = !loading && selectedFile != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Upload & Import") }
                }

                if (selectedFile != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selectedFile!!.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Parsing and importing all rate tables — this may take a moment…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun pickExcelFile(): File? {
    val dialog = FileDialog(null as Frame?, "Select Rate Calculator v13.0 Excel File", FileDialog.LOAD).apply {
        setFilenameFilter { _, name ->
            name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls")
        }
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val file = dialog.file      ?: return null
    return File(dir, file)
}
