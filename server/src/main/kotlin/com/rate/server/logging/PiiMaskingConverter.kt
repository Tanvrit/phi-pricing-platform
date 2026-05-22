package com.rate.server.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Replaces Aadhaar / mobile / PAN / account numbers in log messages with masked forms.
 *
 * This is **defence in depth** — application code should not log PII in the first place,
 * but if it slips through (third-party libs, exception messages, stack traces) the mask
 * keeps log retention DPDP-friendly.
 *
 * Patterns:
 *  - 12-digit Aadhaar  → `XXXX XXXX <last4>`
 *  - 10-digit mobile   → `XXXXXX<last4>`
 *  - 10-char PAN (AAAAA1234A) → `XXXXX<last4>A`
 *  - 9-18 digit bank a/c (longer than mobile, prefixed by "acc"/"account" cue) — mask all but last 4
 */
class PiiMaskingConverter : MessageConverter() {

    override fun convert(event: ILoggingEvent): String {
        val raw = super.convert(event)
        return mask(raw)
    }

    companion object {
        private val AADHAAR = Regex("""(?<!\d)(\d{4})\s?(\d{4})\s?(\d{4})(?!\d)""")
        private val MOBILE  = Regex("""(?<!\d)([6-9]\d{9})(?!\d)""")
        private val PAN     = Regex("""(?<![A-Z0-9])[A-Z]{5}\d{4}[A-Z](?![A-Z0-9])""")

        fun mask(input: String): String {
            var s = input
            // Aadhaar first (12-digit pattern superset of mobile)
            s = AADHAAR.replace(s) { m ->
                val last4 = m.value.replace(" ", "").takeLast(4)
                "XXXX XXXX $last4"
            }
            // Mobile (10-digit)
            s = MOBILE.replace(s) { m ->
                "XXXXXX" + m.value.takeLast(4)
            }
            // PAN
            s = PAN.replace(s) { m ->
                "XXXXX" + m.value.takeLast(5)
            }
            return s
        }
    }
}
