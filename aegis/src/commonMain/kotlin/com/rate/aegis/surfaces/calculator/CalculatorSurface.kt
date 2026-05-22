package com.rate.aegis.surfaces.calculator

import androidx.compose.runtime.Composable

/**
 * Operator-facing rate calculator. Lives in the BUSINESS shell.
 *
 * The implementation is platform-specific — the JVM target renders the full
 * legacy desktop calculator (uses the Ktor CIO HTTP client to hit the server),
 * while the WASM target renders a "desktop only" placeholder since the existing
 * calculator UI uses JVM-only APIs.
 *
 * Migrating the calculator UI into commonMain so it's reachable from both targets
 * is the next iteration's work (no blocker — just a UI repackaging task).
 */
@Composable
expect fun CalculatorSurface()
