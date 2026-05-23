package com.rate.aegis

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Global "refresh now" signal. Surfaces include this in their LaunchedEffect
 * key list; when the operator hits the top-bar Refresh button, the counter
 * increments and every keyed effect re-runs.
 *
 * Surfaces that have their own polling cadence still observe their own intervals;
 * a global refresh is an *immediate* additional poll, not a replacement.
 */
val LocalRefreshTicker = compositionLocalOf<MutableState<Int>> { mutableStateOf(0) }
