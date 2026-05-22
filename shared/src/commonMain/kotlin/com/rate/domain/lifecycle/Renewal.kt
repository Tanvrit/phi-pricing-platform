package com.rate.domain.lifecycle

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * One row in the forward illustration table — what the customer would pay each year if
 * they renewed for the next N years, holding plan/SI/family configuration constant.
 *
 * The illustration is required by IRDAI for any product whose tenure ≥ 3 years (Sales
 * Illustration mandate). It surfaces:
 *   - the projected base premium (rate-table lookup at the projected age band)
 *   - the NCB discount the customer would receive (assuming claim-free continuation)
 *   - the projected total — what they actually pay
 */
@Serializable
data class RenewalIllustrationLine(
    val policyYear: Int,          // 1 = current renewal year
    val ageAtRenewal: Int,
    val ageBand: String,
    val projectedBasePremium: Double,
    val ncbPercent: Double,
    val ncbAmount: Double,
    val projectedTotalPremium: Double
)

/**
 * Output of [RenewalEngine.quote] — everything the customer / agent needs to decide on
 * renewal.
 *
 * - `currentPremium`     : what the customer is paying on the expiring policy.
 * - `projectedPremium`   : what they would pay on renewal (post-NCB, gross of GST).
 * - `ageStepUpAmount`    : portion of the delta attributable to crossing an age band.
 * - `illustrationLines`  : year-by-year forward projection (1..5 years).
 */
@Serializable
data class RenewalQuote(
    val policyId: String,
    val dueDate: Instant,
    val gracePeriodEnds: Instant,
    val currentPremium: Double,
    val projectedPremium: Double,
    val ncbPercent: Double,
    val ncbAmount: Double,
    val ageStepUpAmount: Double,
    val illustrationLines: List<RenewalIllustrationLine>,
    val isValid: Boolean = true,
    val validationErrors: List<String> = emptyList()
)
