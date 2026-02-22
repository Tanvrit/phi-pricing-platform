package com.rate.desktop

import androidx.compose.runtime.*
import com.rate.desktop.api.ApiClient
import com.rate.desktop.navigation.Screen
import com.rate.desktop.ui.calculator.CalculatorScreen
import com.rate.desktop.ui.configurator.ConfiguratorScreen
import com.rate.desktop.ui.importscreen.ImportScreen
import com.rate.desktop.ui.theme.AppTheme

@Composable
fun App() {
    val client = remember { ApiClient() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Calculator) }

    AppTheme {
        when (currentScreen) {
            Screen.Calculator   -> CalculatorScreen(client)    { currentScreen = it }
            Screen.Configurator -> ConfiguratorScreen(client) { currentScreen = it }
            Screen.Import       -> ImportScreen(client)        { currentScreen = it }
        }
    }
}
