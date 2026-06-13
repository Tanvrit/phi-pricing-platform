package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.money.Money
import com.rate.sdk.rating.handler.ManualGroupRater
import com.rate.sdk.rating.model.GroupQuoteResult
import com.rate.sdk.rating.model.ManualClaimYear
import com.rate.sdk.rating.model.ManualDemographyRow
import com.rate.sdk.rating.model.ManualGroupRateInput
import com.rate.sdk.rating.model.ManualGroupStrategyMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Client-side state holder for the GROUP manual-quote surface. There is no network or
 * persistence here: every figure is operator-typed into a [ManualGroupRateInput], and pressing
 * "Calculate" runs the pure [ManualGroupRater] in [scope] to produce a [GroupQuoteResult] the
 * surface renders. The whole engine path (size-discount/loading → experience blend → GST →
 * employer/employee split) runs in-process, so the breakdown matches a server census quote.
 *
 * The input is held as a single immutable [ManualGroupRateInput] in a [mutableStateOf]; every
 * edit replaces it via `copy`, which keeps Compose recomposition correct without a deep
 * snapshot-list dance. The demography grid and claim-year rows are edited through the helpers
 * below (add / remove / mutate one row) so the surface stays declarative.
 */
class GroupManualQuoteViewModel(private val scope: CoroutineScope) {

    /** The full operator-typed input. Replaced wholesale on every edit. */
    var input by mutableStateOf(ManualGroupRateInput.sample())
        private set

    var calculating by mutableStateOf(false)
        private set
    var result by mutableStateOf<GroupQuoteResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // ── Section 1: group details ───────────────────────────────────────────────
    fun setClientName(v: String) = update { it.copy(clientName = v) }
    fun setGroupConfigId(v: String) = update { it.copy(groupConfigId = v) }
    fun setIndustryCode(v: String) = update { it.copy(industryCode = v) }
    fun setZone(v: String) = update { it.copy(zone = v) }
    /** GST typed as a whole-number percent (18 ⇒ 0.18). */
    fun setGstPct(v: String) = update { it.copy(gstRate = v.toPctOr(it.gstRate * 100.0) / 100.0) }
    fun setEmployerSharePct(v: String) = update { it.copy(employerSharePct = v.toPctOr(it.employerSharePct)) }
    fun setGroupSizeDiscountPct(v: String) = update { it.copy(groupSizeDiscountPct = v.toPctOr(it.groupSizeDiscountPct)) }
    fun setIndustryLoadingPct(v: String) = update { it.copy(industryLoadingPct = v.toPctOr(it.industryLoadingPct)) }

    // ── Section 2: demography grid ─────────────────────────────────────────────
    fun addDemographyRow() = update {
        it.copy(
            demography = it.demography + ManualDemographyRow(
                grade = "New", ageBandLabel = "26-35", ageBandMinAge = 26,
                lives = 1, sumInsured = 500_000L, ratePerLifeRupees = 5_000L,
            ),
        )
    }

    fun removeDemographyRow(index: Int) = update {
        if (index !in it.demography.indices) it
        else it.copy(demography = it.demography.filterIndexed { i, _ -> i != index })
    }

    fun updateDemographyRow(index: Int, transform: (ManualDemographyRow) -> ManualDemographyRow) = update {
        if (index !in it.demography.indices) it
        else it.copy(demography = it.demography.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    // ── Section 3: strategy ────────────────────────────────────────────────────
    fun setMode(mode: ManualGroupStrategyMode) = update { it.copy(mode = mode) }
    fun setExpenseRatioPct(v: String) = update { it.copy(expenseRatioPct = v.toPctOr(it.expenseRatioPct)) }
    fun setCredibilityOverridePct(v: String) = update {
        it.copy(credibilityOverridePct = if (v.isBlank()) null else v.toDoubleOrNull() ?: it.credibilityOverridePct)
    }
    fun setManualWeightPct(v: String) = update { it.copy(manualWeightPct = v.toPctOr(it.manualWeightPct)) }

    fun addClaimYear() = update {
        it.copy(
            claimYears = it.claimYears + ManualClaimYear(
                label = "FY${it.claimYears.size + 1}",
                averageLives = 0,
                earnedPremium = Money.ZERO,
                incurredClaims = Money.ZERO,
                claimCount = 0,
            ),
        )
    }

    fun removeClaimYear(index: Int) = update {
        if (index !in it.claimYears.indices) it
        else it.copy(claimYears = it.claimYears.filterIndexed { i, _ -> i != index })
    }

    fun updateClaimYear(index: Int, transform: (ManualClaimYear) -> ManualClaimYear) = update {
        if (index !in it.claimYears.indices) it
        else it.copy(claimYears = it.claimYears.mapIndexed { i, y -> if (i == index) transform(y) else y })
    }

    fun resetToSample() {
        input = ManualGroupRateInput.sample()
        result = null
        error = null
    }

    // ── Compute ────────────────────────────────────────────────────────────────

    /** Local validation mirrored from the rater's guards so the operator sees errors before computing. */
    fun validate(): List<String> = buildList {
        if (input.demography.isEmpty()) add("Add at least one demography row")
        if (input.demography.sumOf { it.lives } <= 0) add("Total lives must be greater than 0")
        if (input.employerSharePct !in 0.0..100.0) add("Employer share must be between 0 and 100%")
        if (input.mode != ManualGroupStrategyMode.MANUAL && input.claimYears.isEmpty()) {
            add("Experience / hybrid rating needs at least one claim year")
        }
    }

    fun calculate() {
        val errs = validate()
        if (errs.isNotEmpty()) {
            error = errs.joinToString("; ")
            result = null
            return
        }
        error = null
        calculating = true
        scope.launch {
            runCatching { ManualGroupRater.rate(input) }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: "Calculation failed"; result = null }
            calculating = false
        }
    }

    // ── Derived running totals (for the grid footer) ───────────────────────────

    val totalLives: Int get() = input.demography.sumOf { it.lives }

    /** Σ lives × ratePerLife — the manual book premium before any group factors. */
    val totalBookPremium: Money
        get() = input.demography.fold(Money.ZERO) { acc, r ->
            acc + Money.fromRupees(r.ratePerLifeRupees) * r.lives
        }

    private inline fun update(transform: (ManualGroupRateInput) -> ManualGroupRateInput) {
        input = transform(input)
    }
}

/** Parse a whole-number-percent text field, keeping the previous value when unparseable. */
private fun String.toPctOr(previous: Double): Double = trim().toDoubleOrNull() ?: previous
