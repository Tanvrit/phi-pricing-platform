package com.rate.core.base.id

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Lexicographically-sortable, time-prefixed id (ULID-style, Crockford base32).
 * 48-bit millisecond timestamp + 80-bit randomness → 26 chars. Pure-KMP: uses
 * only kotlinx-datetime [Clock] and kotlin [Random], so it works on JVM, WASM and iOS.
 */
object Ulid {
    private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford base32
    private const val TIME_LEN = 10
    private const val RAND_LEN = 16

    fun generate(now: Long = Clock.System.now().toEpochMilliseconds()): String {
        val sb = StringBuilder(TIME_LEN + RAND_LEN)
        var time = now
        val timeChars = CharArray(TIME_LEN)
        for (i in TIME_LEN - 1 downTo 0) {
            timeChars[i] = ENCODING[(time and 0x1F).toInt()]
            time = time shr 5
        }
        sb.append(timeChars)
        repeat(RAND_LEN) { sb.append(ENCODING[Random.nextInt(32)]) }
        return sb.toString()
    }
}

/** Facade used across all modules to mint new entity ids. */
fun newId(): String = Ulid.generate()
