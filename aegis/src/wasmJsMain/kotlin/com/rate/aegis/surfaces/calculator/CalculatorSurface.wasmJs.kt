package com.rate.aegis.surfaces.calculator

import androidx.compose.runtime.Composable
import com.rate.aegis.business.calculator.App

/**
 * WASM CalculatorSurface — now backed by the same `App()` composable as JVM.
 * The calculator + configurator screens are commonMain; the import screen is
 * a stub here because the browser has no direct file-system access.
 */
@Composable
actual fun CalculatorSurface() {
    App()
}
