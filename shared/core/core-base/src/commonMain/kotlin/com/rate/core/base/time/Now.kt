package com.rate.core.base.time

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Single source of "now". Wrapping [Clock.System] here keeps `Clock.System.now()`
 * out of domain code and gives tests one seam to freeze time if needed.
 */
object Now {
    fun instant(): Instant = Clock.System.now()
    fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
