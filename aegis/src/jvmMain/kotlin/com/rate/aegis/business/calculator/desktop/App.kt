package com.rate.aegis.business.calculator.desktop

import androidx.compose.runtime.*
import com.rate.aegis.business.calculator.desktop.api.ApiClient
import com.rate.aegis.business.calculator.desktop.navigation.Screen
import com.rate.aegis.business.calculator.desktop.ui.calculator.CalculatorScreen
import com.rate.aegis.business.calculator.desktop.ui.configurator.ConfiguratorScreen
import com.rate.aegis.business.calculator.desktop.ui.importscreen.ImportScreen
import com.rate.aegis.business.calculator.desktop.ui.theme.AppTheme

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
