package com.rate.sdk.audit.handler

import com.rate.core.base.json.AppJson
import com.rate.sdk.audit.model.AuditEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deterministic canonical-JSON serializer used as the pre-image for the audit hash
 * chain. The hash MUST be identical on JVM, wasmJs and iOS, so this canonical form is
 * specified precisely and computed by hand (never by `Json.encodeToString`, whose key
 * order / number formatting are not guaranteed stable across targets).
 *
 * CANONICAL FORM RULES
 *  1. No insignificant whitespace anywhere.
 *  2. Object keys are emitted in ascending Unicode code-point order (string `<`),
 *     recursively. A JsonObject cannot hold duplicate keys.
 *  3. Strings use JSON escaping: `"` `\` and the short escapes `\n \r \t \b`; every
 *     other character below 0x20 (incl. form-feed 0x0C) is emitted as `\u00XX`.
 *     Non-ASCII is emitted literally (UTF-8 bytes are fed to SHA-256 downstream).
 *  4. Numbers:
 *       - Integral values carry NO trailing ".0" and NO decimal point ("5", not "5.0").
 *       - NEVER scientific notation ("1000000", not "1e6"; "0.0000001", not "1e-7").
 *       - The fractional part keeps the exact decimal of the parsed literal with
 *         trailing zeros trimmed. (Monetary fields use integer paise, so floats are rare.)
 *       - Negative zero normalizes to "0".
 *  5. Booleans → "true"/"false"; JSON null → "null".
 *
 * Because the audit payload is stored as ALREADY-canonical text in
 * [AuditEvent.payloadJson], call [canonicalizeJson] once when building the event and
 * never re-parse it for hashing — the stored bytes are authoritative.
 */
object AuditCanonicalizer {

    private const val FORM_FEED = '\u000C'

    /**
     * Parse [rawJson] (any valid JSON) and re-emit it in canonical form. Use this when
     * accepting a payload from the wire before storing it on an [AuditEvent].
     * An empty/blank input canonicalizes to the empty object "{}".
     */
    fun canonicalizeJson(rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return "{}"
        val element = AppJson.json.parseToJsonElement(rawJson)
        return canonicalizeElement(element)
    }

    /** Canonical form of an already-parsed [JsonElement]. */
    fun canonicalizeElement(element: JsonElement): String {
        val sb = StringBuilder()
        writeElement(sb, element)
        return sb.toString()
    }

    /**
     * The exact pre-image hashed for [event]: a canonical object over the chained
     * fields in fixed (sorted) key order. [AuditEvent.payloadJson] is spliced in as a
     * NESTED canonical object (parsed then re-canonicalized) so the chain is invariant
     * to incidental formatting differences in how the payload was stored.
     *
     * Note: [AuditEvent.id], the chain outputs ([AuditEvent.hash], [AuditEvent.prevHash])
     * and the BaseDataClass envelope (createdAt/updatedAt/v/isDeleted) are deliberately
     * EXCLUDED — the hash binds the semantic content, not the storage envelope, so a
     * client can recompute it without knowing server-assigned timestamps.
     */
    fun canonicalPreimage(event: AuditEvent): String {
        // Keys assembled in ascending order: action, actor, at, entity, entityId, payload, seq.
        val sb = StringBuilder()
        sb.append('{')
        writeKey(sb, "action"); writeString(sb, event.action); sb.append(',')

        writeKey(sb, "actor")
        sb.append('{')
        writeKey(sb, "requestId"); writeNullableString(sb, event.actor.requestId); sb.append(',')
        writeKey(sb, "role"); writeNullableString(sb, event.actor.role); sb.append(',')
        writeKey(sb, "subject"); writeNullableString(sb, event.actor.subject)
        sb.append('}'); sb.append(',')

        writeKey(sb, "at"); writeString(sb, event.at.toString()); sb.append(',')
        writeKey(sb, "entity"); writeString(sb, event.entity); sb.append(',')
        writeKey(sb, "entityId"); writeNullableString(sb, event.entityId); sb.append(',')
        // payload spliced as a nested canonical object (re-canonicalized for safety).
        writeKey(sb, "payload"); sb.append(canonicalizeJson(event.payloadJson)); sb.append(',')
        writeKey(sb, "seq"); sb.append(event.seq.toString())
        sb.append('}')
        return sb.toString()
    }

    // ── canonical writers ────────────────────────────────────────────────────

    private fun writeElement(sb: StringBuilder, el: JsonElement) {
        when (el) {
            is JsonNull -> sb.append("null")
            is JsonObject -> {
                sb.append('{')
                el.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) sb.append(',')
                    writeKey(sb, key)
                    writeElement(sb, el.getValue(key))
                }
                sb.append('}')
            }
            is JsonArray -> {
                sb.append('[')
                el.forEachIndexed { i, child ->
                    if (i > 0) sb.append(',')
                    writeElement(sb, child)
                }
                sb.append(']')
            }
            is JsonPrimitive -> writePrimitive(sb, el)
        }
    }

    private fun writePrimitive(sb: StringBuilder, p: JsonPrimitive) {
        if (p.isString) { writeString(sb, p.content); return }
        when (val raw = p.content) {
            "true", "false", "null" -> sb.append(raw)
            else -> sb.append(canonicalNumber(raw))
        }
    }

    private fun writeKey(sb: StringBuilder, key: String) {
        writeString(sb, key)
        sb.append(':')
    }

    private fun writeNullableString(sb: StringBuilder, v: String?) {
        if (v == null) sb.append("null") else writeString(sb, v)
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                FORM_FEED -> sb.append("\\f")
                else ->
                    if (c < ' ') {
                        sb.append("\\u")
                        val hex = c.code.toString(16)
                        repeat(4 - hex.length) { sb.append('0') }
                        sb.append(hex)
                    } else {
                        sb.append(c)
                    }
            }
        }
        sb.append('"')
    }

    /**
     * Normalize a numeric literal to canonical form: no exponent, integral values with
     * no ".0", fractional values with trailing zeros trimmed, negative-zero → "0".
     *
     * Parsing strategy: expand any exponent manually on the decimal string (so we never
     * depend on platform Double→String formatting, which differs JVM vs JS), then trim.
     */
    internal fun canonicalNumber(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return "0"

        // Split sign / mantissa / exponent.
        var sign = ""
        var body = s
        if (body.startsWith("-")) { sign = "-"; body = body.substring(1) }
        else if (body.startsWith("+")) { body = body.substring(1) }

        var exp = 0
        val eIdx = body.indexOfFirst { it == 'e' || it == 'E' }
        if (eIdx >= 0) {
            exp = body.substring(eIdx + 1).toInt()
            body = body.substring(0, eIdx)
        }

        // Split integer / fraction digit strings.
        val dotIdx = body.indexOf('.')
        var intPart = if (dotIdx >= 0) body.substring(0, dotIdx) else body
        val fracPart = if (dotIdx >= 0) body.substring(dotIdx + 1) else ""
        if (intPart.isEmpty()) intPart = "0"

        // Apply the exponent by shifting the decimal point across (int|frac) digits.
        var digits = intPart + fracPart
        // pointPos = number of digits to the LEFT of the decimal point after the shift.
        var pointPos = intPart.length + exp

        if (pointPos <= 0) {
            // value is 0.000<digits>
            digits = "0".repeat(1 - pointPos) + digits
            pointPos = 1
        }
        if (pointPos > digits.length) {
            digits += "0".repeat(pointPos - digits.length)
        }
        var newInt = digits.substring(0, pointPos)
        var newFrac = digits.substring(pointPos)

        // Strip leading zeros from the integer part (keep at least one digit).
        newInt = newInt.trimStart('0')
        if (newInt.isEmpty()) newInt = "0"
        // Strip trailing zeros from the fraction.
        newFrac = newFrac.trimEnd('0')

        val result = if (newFrac.isEmpty()) newInt else "$newInt.$newFrac"
        // Negative zero → "0".
        return if (sign == "-" && result == "0") "0" else sign + result
    }
}
