package com.rate.aegis

/**
 * JVM actual — read the dev-bypass flag from the `AEGIS_DEV_PROFILE` env var or the
 * `-Daegis.devProfile` system property. Any of `true` / `1` / `yes` (case-insensitive)
 * turns it on; anything else (incl. unset) leaves the auth gate in force.
 */
actual val aegisDevProfile: Boolean = run {
    val raw = System.getProperty("aegis.devProfile") ?: System.getenv("AEGIS_DEV_PROFILE")
    raw?.trim()?.lowercase() in setOf("true", "1", "yes")
}
