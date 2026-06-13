package com.rate.sdk.ui.kit.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf

/**
 * Motion presets. Pass 4 §4.10.
 *
 * Honour reduced motion: a transition can read [LocalReducedMotion] and shorten
 * its duration when set. Compose has no portable platform API for
 * `prefers-reduced-motion`, so we expose it as a composition local that the host
 * (operator shell / buyonline app) wires to an OS preference or a user setting.
 */
object AegisMotion {
    val easeOut: Easing = LinearOutSlowInEasing
    val standardEase: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    const val durationFastMs: Int = 150
    const val durationDefaultMs: Int = 200
    const val durationDrawerMs: Int = 250
    const val durationModalMs: Int = 180

    fun <T> tweenFast(): TweenSpec<T> = tween(durationFastMs, easing = easeOut)
    fun <T> tweenDefault(): TweenSpec<T> = tween(durationDefaultMs, easing = easeOut)
    fun <T> tweenDrawer(): TweenSpec<T> = tween(durationDrawerMs, easing = standardEase)
}

/** True if the user / OS has opted into reduced motion. */
val LocalReducedMotion = compositionLocalOf { false }
