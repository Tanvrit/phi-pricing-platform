package com.rate.domain.validation

import com.rate.domain.model.*

/**
 * Shared validation primitives for Indian insurance forms.
 *
 * All validators are pure (no IO) so they can run identically on JVM, iOS, and WASM
 * targets — same regex, same error messages everywhere.
 */

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Invalid(val errorCode: String, val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Ok
}

object Validators {

    // ── Indian mobile (10 digits, leading 6-9) ────────────────────────────
    private val MOBILE_RE = Regex("^[6-9]\\d{9}$")
    fun mobile(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("MOBILE_REQUIRED", "Mobile number is required")
        if (!MOBILE_RE.matches(v)) return ValidationResult.Invalid(
            "MOBILE_INVALID",
            "Enter a valid 10-digit Indian mobile number"
        )
        return ValidationResult.Ok
    }

    // ── PAN: AAAAA1234A ─────────────────────────────────────────────────
    private val PAN_RE = Regex("^[A-Z]{5}\\d{4}[A-Z]$")
    fun pan(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty().uppercase()
        if (v.isEmpty()) return ValidationResult.Invalid("PAN_REQUIRED", "PAN is required")
        if (!PAN_RE.matches(v)) return ValidationResult.Invalid(
            "PAN_INVALID",
            "PAN must be in format AAAAA1234A"
        )
        return ValidationResult.Ok
    }

    // ── IFSC: 4 letters + 0 + 6 alphanumeric (NPCI format) ──────────────
    private val IFSC_RE = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    fun ifsc(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty().uppercase()
        if (v.isEmpty()) return ValidationResult.Invalid("IFSC_REQUIRED", "IFSC code is required")
        if (!IFSC_RE.matches(v)) return ValidationResult.Invalid(
            "IFSC_INVALID",
            "IFSC must be 11 characters (e.g. SBIN0001234)"
        )
        return ValidationResult.Ok
    }

    // ── Indian pincode (6 digits, first digit 1-9) ─────────────────────
    private val PINCODE_RE = Regex("^[1-9]\\d{5}$")
    fun pincode(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("PINCODE_REQUIRED", "Pincode is required")
        if (!PINCODE_RE.matches(v)) return ValidationResult.Invalid(
            "PINCODE_INVALID",
            "Pincode must be 6 digits and start with 1-9"
        )
        return ValidationResult.Ok
    }

    // ── Aadhaar (12 digits + Verhoeff checksum) ─────────────────────────
    fun aadhaar(raw: String?): ValidationResult {
        val v = raw?.trim()?.replace(" ", "").orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("AADHAAR_REQUIRED", "Aadhaar number is required")
        if (v.length != 12 || !v.all(Char::isDigit))
            return ValidationResult.Invalid("AADHAAR_INVALID", "Aadhaar must be 12 digits")
        if (v[0] !in '2'..'9')
            return ValidationResult.Invalid("AADHAAR_INVALID", "Aadhaar cannot start with 0 or 1")
        if (!Verhoeff.validate(v))
            return ValidationResult.Invalid("AADHAAR_CHECKSUM_FAILED", "Aadhaar checksum invalid")
        return ValidationResult.Ok
    }

    // ── Account number (9-18 digits, NPCI guidance) ─────────────────────
    private val ACCOUNT_RE = Regex("^\\d{9,18}$")
    fun accountNumber(raw: String?): ValidationResult {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ValidationResult.Invalid("ACCOUNT_REQUIRED", "Account number is required")
        if (!ACCOUNT_RE.matches(v)) return ValidationResult.Invalid(
            "ACCOUNT_INVALID",
            "Account number must be 9-18 digits"
        )
        return ValidationResult.Ok
    }

    // ── Quote request (plan-aware) ──────────────────────────────────────
    /**
     * Validates a QuoteRequest against its target Plan.
     * Returns an empty list if all rules pass.
     *
     * These checks complement (and are stricter than) the existing engine validation —
     * they catch consistency between the request and the plan's catalogue limits.
     */
    fun quoteRequest(plan: Plan, req: QuoteRequest): List<String> {
        val errors = mutableListOf<String>()
        if (!plan.isActive) errors += "Plan ${plan.id} is not active"
        if (req.primaryAge < plan.minAge || req.primaryAge > plan.maxAge)
            errors += "Primary age ${req.primaryAge} is outside plan range ${plan.minAge}-${plan.maxAge}"
        if (plan.availableSumInsureds.isNotEmpty() && req.sumInsured !in plan.availableSumInsureds)
            errors += "Sum insured ${req.sumInsured} is not in plan grid ${plan.availableSumInsureds}"
        if (plan.availableZones.isNotEmpty() && req.zone !in plan.availableZones)
            errors += "Zone '${req.zone}' is not available on plan ${plan.id} (allowed: ${plan.availableZones})"
        if (plan.availableFamilyTypes.isNotEmpty() && req.familyType !in plan.availableFamilyTypes)
            errors += "Family type '${req.familyType}' is not available on plan ${plan.id}"
        if (req.paymentTenure.years > req.tenure.years)
            errors += "Payment tenure (${req.paymentTenure.label}) cannot exceed policy tenure (${req.tenure.label})"
        val ft = FAMILY_TYPES.firstOrNull { it.code == req.familyType }
        if (ft != null && req.members.size != ft.totalMembers)
            errors += "Family type ${req.familyType} expects ${ft.totalMembers} members; request has ${req.members.size}"
        // Maternity/infertility requires a female adult in floater families
        val needsFemale = setOf(
            CoverIds.MATERNITY_NEWBORN, CoverIds.MATERNITY_FIXED, CoverIds.INFERTILITY
        )
        if (req.selectedCovers.any { it.coverId in needsFemale } &&
            req.members.none { it.gender.equals("F", ignoreCase = true) && it.isAdult }
        ) {
            errors += "Maternity / Infertility covers require at least one female adult member"
        }
        // co_pay + (per_claim_deductible OR aggregate_deductible) is not allowed
        val ids = req.selectedCovers.map { it.coverId }.toSet()
        if (CoverIds.CO_PAY in ids && (CoverIds.PER_CLAIM_DEDUCTIBLE in ids || CoverIds.AGGREGATE_DEDUCTIBLE in ids))
            errors += "Co-Pay cannot be combined with a Deductible"
        return errors
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
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val p = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
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
