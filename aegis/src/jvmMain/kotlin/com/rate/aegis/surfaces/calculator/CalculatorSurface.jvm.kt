package com.rate.aegis.surfaces.calculator

import androidx.compose.runtime.Composable
import com.rate.aegis.business.calculator.App

/**
 * JVM CalculatorSurface — delegates to the original desktop App() composable that
 * was moved into :aegis when the platforms were unified. The desktop calculator
 * carries its own sub-navigation (Calculator / Configurator / Import) and HTTP
 * client; the BUSINESS shell embeds it without wrapping its chrome.
 */
@Composable
actual fun CalculatorSurface() {
    App()
}
