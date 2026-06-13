package com.rate.core.auth.otp

import kotlinx.serialization.Serializable

/**
 * Why an OTP is being issued. The [length] is part of the contract — the customer
 * login flow uses a 4-digit code while E-KYC verification uses a stricter 6-digit
 * code (matching the regulated KYC channel). The store and the SMS templates both
 * read [length] off this enum so there is one source of truth.
 *
 * `<mobile>:<purpose>` is the natural store key, so a LOGIN code and a KYC code for
 * the same mobile coexist without clobbering each other.
 */
@Serializable
enum class OtpPurpose(val length: Int) {
    /** Customer mobile-number login / proposal-start verification (4 digits). */
    LOGIN(4),

    /** E-KYC / DigiLocker step-up verification (6 digits, regulated channel). */
    KYC(6);

    /** Largest value of an `length`-digit code, e.g. 9999 for LOGIN, 999999 for KYC. */
    val maxCodeExclusive: Int get() = ipow10(length)

    /** Smallest value whose decimal form has exactly `length` digits, e.g. 1000 / 100000. */
    val minCodeInclusive: Int get() = ipow10(length - 1)

    companion object {
        private fun ipow10(n: Int): Int {
            var r = 1
            repeat(n) { r *= 10 }
            return r
        }
    }
}
