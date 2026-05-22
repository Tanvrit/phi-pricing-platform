package com.rate.domain.lifecycle

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Policy nominee — the person who receives the claim payout if the insured dies, or
 * the refund if the policy is cancelled. IRDAI requires at least one nominee for
 * every health-insurance policy; a policy may have multiple nominees provided their
 * percent shares sum to exactly 100.
 *
 * Why we model this in :shared and not as a string blob in :server: nominee data
 * is regulated (DPDP-class PII), used by claims, endorsements, and policy issuance;
 * the engine + Aegis + buy-online all need to format / validate it consistently.
 *
 * Phase 5b will add guardian-of-minor handling (nominee is a minor → appoint
 * guardian); for now nominee can simply be any natural person.
 */
@Serializable
data class Nominee(
    val name: String,
    /** Relationship to the policyholder — Self, Spouse, Son, Daughter, Father, Mother, Brother, Sister, Other. */
    val relationship: String,
    val dob: LocalDate,
    /** Optional contact mobile for service communication. May be null for minor nominees. */
    val mobile: String? = null,
    /** Optional PAN — required only if nominee is also a tax-relevant payee. */
    val pan: String? = null,
    /**
     * Share of the payout this nominee receives, expressed as an integer percent.
     * The list of nominees attached to a Policy MUST sum to exactly 100; validate
     * via [Nominee.validateShares].
     */
    val percentShare: Int
) {
    init {
        require(percentShare in 1..100) {
            "Nominee.percentShare must be between 1 and 100 (got $percentShare)"
        }
        require(name.isNotBlank()) { "Nominee.name must not be blank" }
        require(relationship.isNotBlank()) { "Nominee.relationship must not be blank" }
    }

    companion object {
        /**
         * Returns null if the supplied list of nominees has shares summing to 100,
         * otherwise an error message describing the discrepancy.
         */
        fun validateShares(nominees: List<Nominee>): String? {
            if (nominees.isEmpty()) return "At least one nominee is required"
            val total = nominees.sumOf { it.percentShare }
            if (total != 100) return "Nominee percent-shares must sum to 100 (got $total)"
            return null
        }
    }
}
