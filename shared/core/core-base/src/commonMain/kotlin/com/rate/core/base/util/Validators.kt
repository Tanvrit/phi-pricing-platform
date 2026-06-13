package com.rate.core.base.util

/**
 * Foundational, dependency-free field validators for Indian insurance forms.
 *
 * Relocated from the monolithic `com.rate.domain.validation.Validators`. Only the
 * pure field-level checks live here (mobile / PAN / Aadhaar / IFSC / pincode /
 * account / email / GSTIN) because they are reused everywhere — sdk-party handlers,
 * proposal handlers, and the server alike. The plan-aware `quoteRequest(...)`
 * validation that used to share this object depended on the rating model and now
 * lives next to that model in the rating/quoting layer, keeping core-base at ZERO
 * project dependencies.
 *
 * All validators are pure (no IO) so they run identically on JVM, iOS and WASM —
 * same regex, same error codes, same messages everywhere.
 */

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Invalid(val errorCode: String, val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Ok
    val errorOrNull: Invalid? get() = this as? Invalid
}

object Validators {

    // ── Indian mobile (10 digits, leading 6-9) ───────────────────────────────
    private val MOBILE_RE = Regex("^[6-9]\\d{9}$")
    fun mobile(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("MOBILE_REQUIRED", "Mobile number is required")
        if (!MOBILE_RE.matches(v)) return ValidationResult.Invalid(
            "MOBILE_INVALID",
            "Enter a valid 10-digit Indian mobile number",
        )
        return ValidationResult.Ok
    }

    // ── PAN: AAAAA1234A ───────────────────────────────────────────────────────
    private val PAN_RE = Regex("^[A-Z]{5}\\d{4}[A-Z]$")
    fun pan(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty().uppercase()
        if (v.isEmpty()) return ValidationResult.Invalid("PAN_REQUIRED", "PAN is required")
        if (!PAN_RE.matches(v)) return ValidationResult.Invalid(
            "PAN_INVALID",
            "PAN must be in format AAAAA1234A",
        )
        return ValidationResult.Ok
    }

    // ── IFSC: 4 letters + 0 + 6 alphanumeric (NPCI format) ────────────────────
    private val IFSC_RE = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    fun ifsc(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty().uppercase()
        if (v.isEmpty()) return ValidationResult.Invalid("IFSC_REQUIRED", "IFSC code is required")
        if (!IFSC_RE.matches(v)) return ValidationResult.Invalid(
            "IFSC_INVALID",
            "IFSC must be 11 characters (e.g. SBIN0001234)",
        )
        return ValidationResult.Ok
    }

    // ── Indian pincode (6 digits, first digit 1-9) ────────────────────────────
    private val PINCODE_RE = Regex("^[1-9]\\d{5}$")
    fun pincode(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("PINCODE_REQUIRED", "Pincode is required")
        if (!PINCODE_RE.matches(v)) return ValidationResult.Invalid(
            "PINCODE_INVALID",
            "Pincode must be 6 digits and start with 1-9",
        )
        return ValidationResult.Ok
    }

    // ── Aadhaar (12 digits + Verhoeff checksum) ───────────────────────────────
    fun aadhaar(raw: String?): ValidationResult {
        val v = raw?.trim()?.replace(" ", "").orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("AADHAAR_REQUIRED", "Aadhaar number is required")
        if (v.length != 12 || !v.all(Char::isDigit)) {
            return ValidationResult.Invalid("AADHAAR_INVALID", "Aadhaar must be 12 digits")
        }
        if (v[0] !in '2'..'9') {
            return ValidationResult.Invalid("AADHAAR_INVALID", "Aadhaar cannot start with 0 or 1")
        }
        if (!Verhoeff.validate(v)) {
            return ValidationResult.Invalid("AADHAAR_CHECKSUM_FAILED", "Aadhaar checksum invalid")
        }
        return ValidationResult.Ok
    }

    // ── Account number (9-18 digits, NPCI guidance) ───────────────────────────
    private val ACCOUNT_RE = Regex("^\\d{9,18}$")
    fun accountNumber(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("ACCOUNT_REQUIRED", "Account number is required")
        if (!ACCOUNT_RE.matches(v)) return ValidationResult.Invalid(
            "ACCOUNT_INVALID",
            "Account number must be 9-18 digits",
        )
        return ValidationResult.Ok
    }

    // ── Email (pragmatic RFC-5322 subset) ─────────────────────────────────────
    private val EMAIL_RE = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    fun email(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("EMAIL_REQUIRED", "Email is required")
        if (!EMAIL_RE.matches(v)) return ValidationResult.Invalid(
            "EMAIL_INVALID",
            "Enter a valid email address",
        )
        return ValidationResult.Ok
    }

    // ── GSTIN: 2 state digits + 10-char PAN + entity + Z + checksum ───────────
    // 15 chars: NN AAAAA1234A E Z C  (NPCI/GST format, structural check).
    private val GSTIN_RE = Regex("^\\d{2}[A-Z]{5}\\d{4}[A-Z]\\d[A-Z]\\d$")
    fun gstin(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty().uppercase()
        if (v.isEmpty()) return ValidationResult.Invalid("GSTIN_REQUIRED", "GSTIN is required")
        if (v.length != 15 || !GSTIN_RE.matches(v)) return ValidationResult.Invalid(
            "GSTIN_INVALID",
            "GSTIN must be 15 characters (e.g. 27AAAAA1234A1Z5)",
        )
        return ValidationResult.Ok
    }
}

/**
 * Verhoeff checksum (used by Aadhaar). Pure-Kotlin so it runs on all KMP targets.
 * Reference: https://en.wikipedia.org/wiki/Verhoeff_algorithm
 */
private object Verhoeff {
    private val d = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val p = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )

    fun validate(num: String): Boolean {
        var c = 0
        val reversed = num.reversed()
        for ((i, ch) in reversed.withIndex()) {
            val digit = ch.digitToIntOrNull() ?: return false
            c = d[c][p[i % 8][digit]]
        }
        return c == 0
    }
}
