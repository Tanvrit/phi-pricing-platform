package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlin.jvm.JvmName

/**
 * Operator rate-calculator ViewModel — relocated from aegis CalculatorViewModel, rewired onto the
 * real wire surface. Loads the plan catalogue via the admin API, prices a [QuoteRequest] via the
 * server [QuoteApi] (no local engine — the engine lives behind the RatingPort in server), and can
 * persist the priced quote.
 */
class CalculatorViewModel(
    private val configAdmin: ConfigAdminApi,
    private val quotes: QuoteApi,
    private val scope: CoroutineScope,
) {
    var plans by mutableStateOf<List<Plan>>(emptyList())
        private set
    var selectedPlanId by mutableStateOf("")
        private set
    var primaryAge by mutableStateOf("35")
        private set
    var sumInsured by mutableStateOf("1000000")
        private set
    var familyType by mutableStateOf("Self")
        private set
    var zone by mutableStateOf("Zone 1")
        private set
    var tenure by mutableStateOf(Tenure.ONE_YEAR)
        private set
    var paymentMode by mutableStateOf(PaymentMode.ANNUAL)
        private set

    var calculating by mutableStateOf(false)
        private set
    var result by mutableStateOf<QuoteResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastSavedId by mutableStateOf<String?>(null)
        private set

    fun selectPlan(id: String) { selectedPlanId = id }
    fun setAge(v: String) { primaryAge = v.filter { it.isDigit() } }
    @JvmName("applySumInsured") fun setSumInsured(v: String) { sumInsured = v.filter { it.isDigit() } }
    @JvmName("applyFamilyType") fun setFamilyType(v: String) { familyType = v }
    @JvmName("applyZone") fun setZone(v: String) { zone = v }
    @JvmName("applyTenure") fun setTenure(v: Tenure) { tenure = v }
    @JvmName("applyPaymentMode") fun setPaymentMode(v: PaymentMode) { paymentMode = v }

    @Suppress("UNCHECKED_CAST")
    fun loadPlans() {
        scope.launch {
            runCatching {
                configAdmin.list(
                    "plans",
                    Plan.serializer() as KSerializer<ConfigEntity>,
                    PageRequest(page = 0, size = 200),
                )
            }.onSuccess { page ->
                plans = page.items.filterIsInstance<Plan>()
                if (selectedPlanId.isBlank()) selectedPlanId = plans.firstOrNull()?.id ?: ""
            }.onFailure { error = it.message }
        }
    }

    private fun buildRequest(): QuoteRequest {
        val age = primaryAge.toIntOrNull() ?: 0
        val si = sumInsured.toLongOrNull() ?: 0L
        return QuoteRequest(
            planId = selectedPlanId,
            primaryAge = age,
            sumInsured = si,
            familyType = familyType,
            zone = zone,
            tenure = tenure,
            paymentMode = paymentMode,
            paymentTenure = tenure,
            members = listOf(Member(memberId = 1, age = age, relationship = "Self")),
        )
    }

    fun validate(): List<String> = buildList {
        if (selectedPlanId.isBlank()) add("Pick a plan")
        if ((primaryAge.toIntOrNull() ?: 0) <= 0) add("Enter a valid age")
        if ((sumInsured.toLongOrNull() ?: 0L) <= 0L) add("Enter a sum insured")
    }

    fun calculate() {
        val errs = validate()
        if (errs.isNotEmpty()) { error = errs.joinToString("; "); return }
        calculating = true
        error = null
        result = null
        lastSavedId = null
        scope.launch {
            runCatching { quotes.calculate(buildRequest()) }
                .onSuccess { result = it.result }
                .onFailure { error = it.message ?: "Calculation failed" }
            calculating = false
        }
    }

    fun saveQuote(onDone: (String?) -> Unit = {}) {
        scope.launch {
            runCatching { quotes.save(buildRequest()) }
                .onSuccess { lastSavedId = it.quoteId; result = it.result; onDone(it.quoteId) }
                .onFailure { error = it.message ?: "Save failed"; onDone(null) }
        }
    }
}
