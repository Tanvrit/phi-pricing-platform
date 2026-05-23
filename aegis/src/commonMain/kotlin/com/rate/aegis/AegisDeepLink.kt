package com.rate.aegis

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.rate.aegis.components.AegisSurface

/**
 * Cross-surface targeting from the command palette. The palette's action sets
 * one of the typed fields; the destination surface reads it on first
 * composition and clears it so a later visit doesn't auto-open the same row.
 */
data class DeepLink(
    val planId: String? = null,
    val quoteId: String? = null,
    val coverId: String? = null,
    val discountId: String? = null,
) {
    companion object { val NONE = DeepLink() }
}

val LocalAegisDeepLink = compositionLocalOf<MutableState<DeepLink>> {
    mutableStateOf(DeepLink.NONE)
}

/**
 * Lets non-shell surfaces (e.g. audit rows with clickable resource ids)
 * request a surface change. Provided by [OperatorShell]; defaults to a
 * no-op so previews / isolated composables don't crash.
 */
val LocalSurfaceRouter: ProvidableCompositionLocal<(AegisSurface) -> Unit> =
    staticCompositionLocalOf { { /* no-op */ } }
