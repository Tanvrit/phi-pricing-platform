package com.rate.aegis.business.calculator.desktop.navigation

sealed class Screen {
    object Calculator   : Screen()
    object Configurator : Screen()
    object Import       : Screen()

    val label: String get() = when (this) {
        Calculator   -> "Quote Calculator"
        Configurator -> "Product Configurator"
        Import       -> "Import Rates"
    }
}
