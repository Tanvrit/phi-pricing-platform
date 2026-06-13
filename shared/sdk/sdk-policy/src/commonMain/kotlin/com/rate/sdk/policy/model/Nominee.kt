package com.rate.sdk.policy.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Policy nominee — the person who receives the claim payout if the insured dies, or the
 * refund if the policy is cancelled. IRDAI requires at least one nominee for every
 * health-insurance policy; a policy may have multiple nominees provided their percent
 * shares sum to exactly 100 (validate via [Nominee.validateShares]).
 *
 * Embedded into [Policy.nominees] rather than a separate collection: nominee data is
 * regulated (DPDP-class PII) but is always read/written alongside its owning policy, and
 * endorsements replace the whole list atomically ([com.rate.sdk.policy.handler.EndorsementEngine]).
 */
@Serializable
data class Nominee(
    @SerialName("name") val name: String,
    /** Self, Spouse, Son, Daughter, Father, Mother, Brother, Sister, Other. */
    @SerialName("relationship") val relationship: String,
    @SerialName("dob") val dob: LocalDate,
    /** Optional contact mobile for service communication. May be null for minor nominees. */
    @SerialName("mobile") val mobile: String? = null,
    /** Optional PAN — required only if nominee is also a tax-relevant payee. */
    @SerialName("pan") val pan: String? = null,
    /**
     * Share of the payout this nominee receives, as an integer percent. The list attached
     * to a [Policy] MUST sum to exactly 100.
     */
    @SerialName("percentShare") val percentShare: Int,
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
         * Returns null if [nominees] has shares summing to 100, otherwise an error message
         * describing the discrepancy.
         */
        fun validateShares(nominees: List<Nominee>): String? {
            if (nominees.isEmpty()) return "At least one nominee is required"
            val total = nominees.sumOf { it.percentShare }
            if (total != 100) return "Nominee percent-shares must sum to 100 (got $total)"
            return null
        }
    }
}
