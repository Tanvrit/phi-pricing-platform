package com.rate.aegis.util

/**
 * JVM actuals — read OS info from the standard System properties.
 *
 * `os.name` and `os.version` are guaranteed-present on every spec-compliant
 * JDK, but we still defend against `null` because some embedded JREs (and the
 * occasional GraalVM native-image config) strip arbitrary properties. The
 * fallback "?" keeps the System-info card visually stable instead of
 * collapsing to a blank value.
 */
actual fun runtimeKind(): String = "JVM"

actual fun runtimeHostInfo(): String =
    "${System.getProperty("os.name") ?: "?"} ${System.getProperty("os.version") ?: "?"}"
