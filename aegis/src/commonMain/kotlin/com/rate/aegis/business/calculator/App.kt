package com.rate.aegis.business.calculator

import androidx.compose.runtime.*
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.aegis.business.calculator.navigation.Screen
import com.rate.aegis.business.calculator.ui.calculator.CalculatorScreen
import com.rate.aegis.business.calculator.ui.configurator.ConfiguratorScreen
import com.rate.aegis.business.calculator.ui.importscreen.ImportScreen
import com.rate.aegis.business.calculator.ui.theme.AppTheme

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
