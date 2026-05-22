package com.rate.aegis.business.calculator.ui.calculator

import androidx.compose.runtime.*
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.domain.data.CoverCatalog
import com.rate.domain.data.CoverMeta
import com.rate.domain.data.PincodeZoneMap
import com.rate.domain.model.*
import kotlinx.coroutines.*

class CalculatorViewModel(private val client: ApiClient) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Reference data ─────────────────────────────────────────────────────
    var plans            by mutableStateOf<List<Plan>>(emptyList())
    var covers           by mutableStateOf<List<CoverMeta>>(emptyList())
    var deductibleCovers by mutableStateOf<List<CoverMeta>>(emptyList())
    var discounts        by mutableStateOf<List<CoverMeta>>(emptyList())
    var familyTypes      by mutableStateOf<List<FamilyTypeInfo>>(emptyList())
    var domesticSIs      by mutableStateOf<List<Long>>(emptyList())

    // ── Form fields ────────────────────────────────────────────────────────
    var selectedPlan by mutableStateOf<Plan?>(null)
    var primaryAge   by mutableStateOf("35")

    private var _spouseAge by mutableStateOf("")
    var spouseAge: String
        get() = _spouseAge
        set(value) {
            _spouseAge = value
            if (!isMultiIndividual) selectedFT = deriveFamilyType(value, _childAges)
        }

    private var _childAges by mutableStateOf("")
    var childAges: String
        get() = _childAges
        set(value) {
            _childAges = value
            if (!isMultiIndividual) selectedFT = deriveFamilyType(_spouseAge, value)
        }

    var selectedFT   by mutableStateOf<FamilyTypeInfo?>(null)
    var selectedSI   by mutableStateOf<Long?>(null)

    // Multi-individual: explicit override of auto-derived family type
    var isMultiIndividual by mutableStateOf(false)
        private set

    fun toggleMultiIndividual(v: Boolean) {
        isMultiIndividual = v
        selectedFT = if (v) getFamilyTypeInfo("multi") else deriveFamilyType()
    }

    // Pincode auto-derives zone
    private var _pincode by mutableStateOf("")
    var pincode: String
        get() = _pincode
        set(value) {
            _pincode = value
            if (value.length == 6) {
                val zoneLabel = PincodeZoneMap.zoneForPincode(value)
                Zone.entries.firstOrNull { it.label == zoneLabel }?.let { selectedZone = it }
                detectedCity = PincodeZoneMap.cityHintForPincode(value)
            } else {
                detectedCity = null
            }
        }
    var detectedCity by mutableStateOf<String?>(null)
        private set

    var selectedZone      by mutableStateOf(Zone.ZONE_4)
    var selectedTenure    by mutableStateOf(Tenure.ONE_YEAR)
    var selectedPayTenure by mutableStateOf(Tenure.ONE_YEAR)
    var selectedPayMode   by mutableStateOf(PaymentMode.ANNUAL)
    var uwLoading         by mutableStateOf("0")

    // Cover selections
    var coverParams         by mutableStateOf<Map<String, CoverParam>>(emptyMap())
    var selectedCoverIds    by mutableStateOf<Set<String>>(emptySet())
    var selectedDiscountIds by mutableStateOf<Set<String>>(emptySet())

    // ── Results ────────────────────────────────────────────────────────────
    var result        by mutableStateOf<QuoteResult?>(null)
    var tenureResults by mutableStateOf<Map<Tenure, QuoteResult>>(emptyMap())
    var loading       by mutableStateOf(false)
    var error         by mutableStateOf<String?>(null)

    init { scope.launch { loadReferenceData() } }

    private suspend fun loadReferenceData() {
        loading = true
        try {
            plans = client.getPlans()
        } catch (e: Exception) {
            error = "Cannot reach server: ${e.message}"
        }
        familyTypes  = FAMILY_TYPES
        domesticSIs  = plans.firstOrNull()?.availableSumInsureds ?: emptyList()
        selectedPlan = plans.firstOrNull()
        selectedFT   = familyTypes.firstOrNull()
        selectedSI   = domesticSIs.getOrNull(5)
        refreshCoversForPlan(selectedPlan)
        loading = false
    }

    /** Filter covers to only those allowed by the selected plan. */
    fun refreshCoversForPlan(plan: Plan?) {
        val allowedIds   = plan?.allowedCoverIds ?: emptySet()
        val allRegular   = CoverCatalog.ALL.filter { !it.isDiscount }
        val allDeduc     = CoverCatalog.ALL.filter { it.isDiscount }
        val allDiscounts = CoverCatalog.DISCOUNTS

        covers = if (allowedIds.isEmpty()) allRegular
                 else allRegular.filter { it.id in allowedIds }
        deductibleCovers = if (allowedIds.isEmpty()) allDeduc
                           else allDeduc.filter { it.id in allowedIds }
        discounts = if (allowedIds.isEmpty()) allDiscounts
                    else allDiscounts.filter { it.id in allowedIds }

        // Deselect unavailable
        val availIds = (covers + deductibleCovers + discounts).map { it.id }.toSet()
        selectedCoverIds    = selectedCoverIds.intersect(availIds)
        selectedDiscountIds = selectedDiscountIds.intersect(availIds)
    }

    fun onPlanSelected(plan: Plan) {
        selectedPlan = plan
        val sis = plan.availableSumInsureds
        if (selectedSI !in sis) selectedSI = sis.getOrNull(5) ?: sis.lastOrNull()
        if (plan.availableZones.isNotEmpty() &&
            selectedZone.label !in plan.availableZones) {
            selectedZone = Zone.entries.firstOrNull { it.label == plan.availableZones.first() }
                ?: Zone.ZONE_4
        }
        refreshCoversForPlan(plan)
    }

    fun toggleCover(coverId: String, isDiscount: Boolean, enabled: Boolean) {
        if (isDiscount) {
            selectedDiscountIds = if (enabled) selectedDiscountIds + coverId
                                  else selectedDiscountIds - coverId
        } else {
            selectedCoverIds = if (enabled) selectedCoverIds + coverId
                               else selectedCoverIds - coverId
        }
        if (!enabled) coverParams = coverParams - coverId
    }

    fun updateParam1(coverId: String, p1: String) {
        coverParams = coverParams + (coverId to (coverParams[coverId] ?: CoverParam()).copy(param1 = p1))
    }

    fun updateParam2(coverId: String, p2: String) {
        coverParams = coverParams + (coverId to (coverParams[coverId] ?: CoverParam()).copy(param2 = p2))
    }

    fun getParam(coverId: String) = coverParams[coverId] ?: CoverParam()

    fun calculate() {
        val plan  = selectedPlan ?: run { error = "Select a plan"; return }
        val si    = selectedSI   ?: run { error = "Select sum insured"; return }
        val age   = primaryAge.toIntOrNull() ?: run { error = "Enter valid primary age"; return }
        val uwPct = (uwLoading.toDoubleOrNull() ?: 0.0) / 100.0
        val ft    = selectedFT ?: run { error = "Select family type"; return }

        val members = buildMemberList(age)

        val selCovers = selectedCoverIds.map { id ->
            CoverSelection(coverId = id, params = coverParams[id] ?: CoverParam())
        }
        val selDiscounts = selectedDiscountIds.map { id ->
            DiscountSelection(discountId = id, param = coverParams[id]?.param1)
        }

        val request = QuoteRequest(
            planId            = plan.id,
            primaryAge        = age,
            sumInsured        = si,
            familyType        = ft.code,
            zone              = selectedZone.label,
            tenure            = selectedTenure,
            paymentMode       = selectedPayMode,
            paymentTenure     = selectedPayTenure,
            members           = members,
            selectedCovers    = selCovers,
            selectedDiscounts = selDiscounts,
            uwLoadingFactor   = uwPct,
            maxDiscountCap    = plan.maxDiscountCap
        )

        scope.launch {
            loading = true; error = null
            try {
                result = client.calculate(request)
                val allResults = mutableMapOf<Tenure, QuoteResult>()
                for (tenure in Tenure.entries) {
                    val req = request.copy(tenure = tenure, paymentTenure = tenure)
                    allResults[tenure] = client.calculate(req)
                }
                tenureResults = allResults
            } catch (e: Exception) {
                error = "Calculation error: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    private fun buildMemberList(primaryAge: Int): List<Member> {
        val list = mutableListOf<Member>()
        var id = 1
        list.add(Member(memberId = id++, age = primaryAge, relationship = "Self"))
        spouseAge.toIntOrNull()?.let {
            list.add(Member(memberId = id++, age = it, relationship = "Spouse", gender = "F"))
        }
        childAges.split(",").mapNotNull { it.trim().toIntOrNull() }
            .forEach { list.add(Member(memberId = id++, age = it, relationship = "Child")) }
        return list
    }

    private fun deriveFamilyType(
        spouseAge: String = _spouseAge,
        childAges: String = _childAges
    ): FamilyTypeInfo {
        val hasSpouse  = spouseAge.toIntOrNull() != null
        val childCount = childAges.split(",").mapNotNull { it.trim().toIntOrNull() }.size
        val adults     = if (hasSpouse) 2 else 1
        val code = when {
            adults == 2 && childCount == 0 -> "2A"
            adults == 2 && childCount == 1 -> "2A1C"
            adults == 2 && childCount == 2 -> "2A2C"
            adults == 2 && childCount == 3 -> "2A3C"
            adults == 2                    -> "2A4C"
            adults == 1 && childCount == 0 -> "1A"
            adults == 1 && childCount == 1 -> "1A1C"
            adults == 1 && childCount == 2 -> "1A2C"
            adults == 1 && childCount == 3 -> "1A3C"
            else                           -> "1A4C"
        }
        return getFamilyTypeInfo(code)
    }

    fun dispose() = scope.cancel()
}
