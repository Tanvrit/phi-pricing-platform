package com.rate.sdk.ui.operator.registry

import com.rate.core.money.Money
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Pure parse/format helpers shared by every entity descriptor so the form buffer (a
 * `Map<String,String>`) round-trips typed values consistently. Kept tiny and dependency-free.
 */
internal object FormCodec {

    /**
     * The JSON codec used to round-trip structured list/map fields (CoverOption rows, CIItem rows,
     * tieredPlanRefs, …) through a single buffer string. Lenient + defaults-tolerant so a
     * hand-edited or partial row never hard-fails the form.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ── Structured list-of-objects fields (FieldKind.TABLE) ─────────────────
    /** Encode a `List<T>` to a JSON-array buffer string (empty list → ""). */
    fun <T> encodeList(serializer: KSerializer<T>, values: List<T>): String =
        if (values.isEmpty()) "" else json.encodeToString(ListSerializer(serializer), values)

    /** Decode a JSON-array buffer string back to a `List<T>` (blank/garbage → []). */
    fun <T> decodeList(serializer: KSerializer<T>, buffer: Map<String, String>, key: String): List<T> {
        val raw = buffer[key]?.trim().orEmpty()
        if (raw.isEmpty() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(emptyList())
    }

    // ── Structured key→value-list map fields (FieldKind.KEY_VALUE) ──────────
    private val stringListMapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    /** Encode a `Map<String, List<String>>` to a JSON-object buffer string (empty → ""). */
    fun encodeStringListMap(value: Map<String, List<String>>): String =
        if (value.isEmpty()) "" else json.encodeToString(stringListMapSerializer, value)

    /** Decode a JSON-object buffer string back to a `Map<String, List<String>>` (blank/garbage → {}). */
    fun decodeStringListMap(buffer: Map<String, String>, key: String): Map<String, List<String>> {
        val raw = buffer[key]?.trim().orEmpty()
        if (raw.isEmpty() || raw == "{}") return emptyMap()
        return runCatching { json.decodeFromString(stringListMapSerializer, raw) }.getOrDefault(emptyMap())
    }

    fun str(buffer: Map<String, String>, key: String, default: String = ""): String =
        buffer[key]?.takeIf { it.isNotBlank() } ?: default

    fun strOrNull(buffer: Map<String, String>, key: String): String? =
        buffer[key]?.trim()?.takeIf { it.isNotBlank() }

    fun int(buffer: Map<String, String>, key: String, default: Int = 0): Int =
        buffer[key]?.trim()?.toIntOrNull() ?: default

    fun long(buffer: Map<String, String>, key: String, default: Long = 0L): Long =
        buffer[key]?.trim()?.toLongOrNull() ?: default

    fun double(buffer: Map<String, String>, key: String, default: Double = 0.0): Double =
        buffer[key]?.trim()?.toDoubleOrNull() ?: default

    fun bool(buffer: Map<String, String>, key: String, default: Boolean = false): Boolean =
        when (buffer[key]?.trim()?.lowercase()) {
            "true", "yes", "1", "on" -> true
            "false", "no", "0", "off" -> false
            else -> default
        }

    /** Money stored/edited in whole rupees; the field renders ₹ and digits only. */
    fun money(buffer: Map<String, String>, key: String, default: Money = Money.ZERO): Money {
        val digits = buffer[key]?.filter { it.isDigit() } ?: return default
        return digits.toLongOrNull()?.let { Money.fromRupees(it) } ?: default
    }

    /** Comma/newline separated list → trimmed non-blank items. */
    fun list(buffer: Map<String, String>, key: String): List<String> =
        buffer[key]?.split(',', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun longList(buffer: Map<String, String>, key: String): List<Long> =
        list(buffer, key).mapNotNull { it.toLongOrNull() }

    fun intList(buffer: Map<String, String>, key: String): List<Int> =
        list(buffer, key).mapNotNull { it.toIntOrNull() }

    /** Resolve an enum value from a string, falling back to [default] for unknown input. */
    inline fun <reified E : Enum<E>> enum(buffer: Map<String, String>, key: String, default: E): E {
        val raw = buffer[key]?.trim() ?: return default
        return enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
    }

    // ── ISO dates (FieldKind.DATE / DATE_RANGE) ─────────────────────────────
    // Dates live in the buffer as plain ISO strings: "YYYY-MM-DD" (DATE) and
    // "YYYY-MM-DD..YYYY-MM-DD" (DATE_RANGE). No new serialization — the descriptor decides whether
    // it stores them as String or parses to a typed date.

    /** Read a DATE buffer value, returning [default] when blank or not a valid ISO date. */
    fun isoDate(buffer: Map<String, String>, key: String, default: String = ""): String {
        val raw = buffer[key]?.trim().orEmpty()
        return if (raw.isNotEmpty() && isValidIsoDate(raw)) raw else default
    }

    /**
     * Validate a strict ISO calendar date "YYYY-MM-DD": 4-digit year, 1–12 month, day within the
     * month's real length (leap-year aware). Mirrors the kit renderer's inline validation so the
     * descriptor can validate the buffer without depending on the UI kit.
     */
    fun isValidIsoDate(s: String): Boolean {
        val parts = s.split('-')
        if (parts.size != 3) return false
        val (yStr, mStr, dStr) = parts
        if (yStr.length != 4 || mStr.length != 2 || dStr.length != 2) return false
        val year = yStr.toIntOrNull() ?: return false
        val month = mStr.toIntOrNull() ?: return false
        val day = dStr.toIntOrNull() ?: return false
        if (month !in 1..12) return false
        val maxDay = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            else -> 0
        }
        return day in 1..maxDay
    }

    // ── Formatters (entity → buffer) ────────────────────────────────────────
    fun fmt(value: Any?): String = value?.toString() ?: ""
    fun fmtBool(value: Boolean): String = value.toString()

    /**
     * Format a date for the DATE buffer. Accepts anything stringifying to ISO ("YYYY-MM-DD", e.g. a
     * `kotlinx.datetime.LocalDate` or a raw String); blank when null. Only emits the value when it
     * is a valid ISO date so a stray value never lands in the buffer as a "valid" date.
     */
    fun fmtDate(value: Any?): String {
        val s = value?.toString()?.trim().orEmpty()
        return if (s.isNotEmpty() && isValidIsoDate(s)) s else ""
    }
    fun fmtList(values: List<Any>): String = values.joinToString(", ") { it.toString() }
    fun fmtMoney(value: Money): String = (value.paise / 100L).toString()
}
