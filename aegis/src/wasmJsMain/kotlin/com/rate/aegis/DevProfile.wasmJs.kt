package com.rate.aegis

import kotlinx.browser.window

/**
 * WASM actual — opt into the dev bypass with a `?dev=1` / `?devProfile=1` URL flag
 * so a preview link can skip the operator login. The bare public URL has no such
 * flag, so the gate stays in force for real customers/operators on the wire.
 */
actual val aegisDevProfile: Boolean = run {
    val search = window.location.search.removePrefix("?")
    if (search.isEmpty()) return@run false
    val params = search.split("&").mapNotNull {
        val k = it.substringBefore('=').trim().lowercase()
        val v = it.substringAfter('=', "").trim().lowercase()
        if (k.isEmpty()) null else k to v
    }.toMap()
    val flag = params["dev"] ?: params["devprofile"]
    flag in setOf("1", "true", "yes")
}
