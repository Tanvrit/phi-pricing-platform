package com.rate.server.logging

import ch.qos.logback.classic.pattern.MessageConverter
import ch.qos.logback.classic.spi.ILoggingEvent

/**
 * Replaces Aadhaar / mobile / PAN / account numbers in log messages with masked forms.
 *
 * RELOCATED verbatim from the monolith's `server.logging` — defence-in-depth logging infra that
 * is pure JVM/logback (no Mongo / domain coupling), so it stays a thin server package. Wired via
 * the `%piimsg` conversion rule in `logback.xml`. Application code should not log PII in the first
 * place, but if it slips through (third-party libs, exception messages, stack traces) the mask
 * keeps log retention DPDP-friendly.
 *
 * Patterns:
 *  - 12-digit Aadhaar  → `XXXX XXXX <last4>`
 *  - 10-digit mobile   → `XXXXXX<last4>`
 *  - 10-char PAN (AAAAA1234A) → `XXXXX<last5>`
 */
class PiiMaskingConverter : MessageConverter() {

    override fun convert(event: ILoggingEvent): String = mask(super.convert(event))

    companion object {
        private val AADHAAR = Regex("""(?<!\d)(\d{4})\s?(\d{4})\s?(\d{4})(?!\d)""")
        private val MOBILE = Regex("""(?<!\d)([6-9]\d{9})(?!\d)""")
        private val PAN = Regex("""(?<![A-Z0-9])[A-Z]{5}\d{4}[A-Z](?![A-Z0-9])""")

        fun mask(input: String): String {
            var s = input
            s = AADHAAR.replace(s) { m -> "XXXX XXXX " + m.value.replace(" ", "").takeLast(4) }
            s = MOBILE.replace(s) { m -> "XXXXXX" + m.value.takeLast(4) }
            s = PAN.replace(s) { m -> "XXXXX" + m.value.takeLast(5) }
            return s
        }
    }
}
