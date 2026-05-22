package com.rate.domain.money

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money expressed as integer paise (1 INR = 100 paise).
 *
 * Why: insurance premiums must be exact to the paise. Floating-point Doubles accumulate
 * error across the engine's many multiplications and sums (50+ covers × up to 5 policy
 * years × discount cascades). Storing as Long paise eliminates drift and makes equality
 * meaningful.
 *
 * Convention:
 * - `paise` is the canonical wire representation.
 * - `toRupees(): Double` is only for display.
 * - Arithmetic operators preserve precision. Division rounds half-up to nearest paisa.
 */
@JvmInline
@Serializable
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(paise + other.paise)
    operator fun minus(other: Money): Money = Money(paise - other.paise)
    operator fun times(factor: Double): Money = Money((paise * factor).roundHalfEven())
    operator fun times(factor: Int): Money = Money(paise * factor)
    operator fun times(factor: Long): Money = Money(paise * factor)
    operator fun div(divisor: Int): Money {
        require(divisor != 0) { "Money cannot be divided by zero" }
        return Money(paise / divisor + if (paise % divisor >= divisor / 2) 1 else 0)
    }
    operator fun unaryMinus(): Money = Money(-paise)

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun isZero(): Boolean = paise == 0L
    fun isNegative(): Boolean = paise < 0L
    fun abs(): Money = Money(abs(paise))

    /** For display only — never use the Double back in calculations. */
    fun toRupees(): Double = paise / 100.0

    /** Indian number grouping: ₹1,23,45,678.90 */
    fun formatIndian(showSymbol: Boolean = true, showDecimals: Boolean = true): String {
        val sign = if (paise < 0) "-" else ""
        val absPaise = abs(paise)
        val rupees = absPaise / 100
        val pp = (absPaise % 100).toInt()
        val rupeesStr = rupees.toString()
        // Indian grouping: last 3 digits, then groups of 2 thereafter
        val grouped = buildString {
            val n = rupeesStr.length
            if (n <= 3) append(rupeesStr)
            else {
                append(rupeesStr, 0, n - 3)
                // Insert commas every 2 chars from the right of the prefix
                val prefix = this.toString()
                clear()
                val withCommas = StringBuilder()
                for ((i, c) in prefix.reversed().withIndex()) {
                    if (i > 0 && i % 2 == 0) withCommas.append(',')
                    withCommas.append(c)
                }
                append(withCommas.reverse())
                append(',')
                append(rupeesStr, n - 3, n)
            }
        }
        val prefix = if (showSymbol) "₹" else ""
        return if (showDecimals) "$sign$prefix$grouped.${pp.toString().padStart(2, '0')}"
        else "$sign$prefix$grouped"
    }

    companion object {
        val ZERO: Money = Money(0L)
        fun fromRupees(rupees: Double): Money = Money((rupees * 100.0).roundHalfEven())
        fun fromRupees(rupees: Long): Money = Money(rupees * 100L)
        fun paise(p: Long): Money = Money(p)
    }
}

/** Half-even (banker's) rounding for currency multiplications. */
private fun Double.roundHalfEven(): Long {
    if (this.isNaN() || this.isInfinite()) return 0L
    val floor = kotlin.math.floor(this).toLong()
    val frac = this - floor
    return when {
        frac < 0.5 -> floor
        frac > 0.5 -> floor + 1
        else -> if (floor % 2L == 0L) floor else floor + 1
    }
}

fun Double.toMoney(): Money = Money.fromRupees(this)
fun Iterable<Money>.sumMoney(): Money = fold(Money.ZERO) { acc, m -> acc + m }
