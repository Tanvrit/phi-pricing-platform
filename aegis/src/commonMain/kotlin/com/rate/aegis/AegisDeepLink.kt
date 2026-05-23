package com.rate.aegis

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

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
