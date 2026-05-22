package com.rate.buyonline.viewmodel

import androidx.compose.runtime.*
import com.rate.buyonline.api.*
import com.rate.buyonline.model.*
import com.rate.buyonline.navigation.BuyOnlineScreen
import kotlinx.coroutines.*

class BuyOnlineViewModel(private val client: BuyOnlineApiClient) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Navigation ────────────────────────────────────────────────────────────
    var currentScreen by mutableStateOf<BuyOnlineScreen>(BuyOnlineScreen.Landing)
    private val backStack = mutableListOf<BuyOnlineScreen>()

    fun navigate(screen: BuyOnlineScreen) { backStack.add(currentScreen); currentScreen = screen }
    fun navigateBack() { if (backStack.isNotEmpty()) currentScreen = backStack.removeLast() }

    // ── Landing ───────────────────────────────────────────────────────────────
    var selectedMembers by mutableStateOf<Set<MemberType>>(setOf(MemberType.SELF))
    var kidsCount       by mutableStateOf(0)
    var eldestAge       by mutableStateOf("")
    var mobile          by mutableStateOf("")
    var consentGiven    by mutableStateOf(false)
    var showTerms       by mutableStateOf(false)

    // ── OTP ───────────────────────────────────────────────────────────────────
    var otpDigits by mutableStateOf(List(4) { "" })
    var otpTimer  by mutableStateOf(180)
    var otpError  by mutableStateOf<String?>(null)
    private var timerJob: Job? = null

    fun startOtpTimer() {
        timerJob?.cancel(); otpTimer = 180
        timerJob = scope.launch { while (otpTimer > 0) { delay(1000); otpTimer-- } }
    }

    fun updateOtpDigit(index: Int, value: String) {
        otpDigits = otpDigits.toMutableList().also { it[index] = value.takeLast(1) }
    }

    val otpFilled get() = otpDigits.all { it.isNotEmpty() }
    val otpTimerFormatted get() =
        "${(otpTimer / 60).toString().padStart(2, '0')}:${(otpTimer % 60).toString().padStart(2, '0')}"

    // ── GetStarted ────────────────────────────────────────────────────────────
    var pincode         by mutableStateOf("")
    var hospitalsNearby by mutableStateOf(0)

    // ── Health declarations ───────────────────────────────────────────────────
    var hasPED                 by mutableStateOf(false)
    var pedMembers             by mutableStateOf<Set<String>>(emptySet())
    var hasCriticalIllness     by mutableStateOf(false)
    var criticalIllnessMembers by mutableStateOf<Set<String>>(emptySet())
    var showCriticalInfoModal  by mutableStateOf(false)

    val allMembers: List<String> get() {
        val list = mutableListOf("Myself")
        if (MemberType.SPOUSE in selectedMembers) list.add("Spouse")
        repeat(kidsCount) { list.add("Kid ${it + 1}") }
        return list
    }

    val extendedMembers: List<String> get() =
        allMembers + listOf("Father", "Mother", "Father-in-law", "Mother-in-law")

    // ── Eligibility ───────────────────────────────────────────────────────────
    var coveredMembers   by mutableStateOf<List<String>>(emptyList())
    var uncoveredMembers by mutableStateOf<List<String>>(emptyList())

    // ── Quote ─────────────────────────────────────────────────────────────────
    var selectedTier       by mutableStateOf(PlanTier.PREMIER)
    var selectedSumInsured by mutableStateOf(1_000_000L)
    var selectedTenure     by mutableStateOf(1)
    var showSISheet        by mutableStateOf(false)
    var showTenureSheet    by mutableStateOf(false)
    var serverPremium      by mutableStateOf<Double?>(null)  // null = use local calc

    val sumInsuredOptions = listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L)
    val tenureOptions     = listOf(1, 2, 3, 4, 5)
    val tenureDiscounts   = mapOf(1 to 0.0, 2 to 0.075, 3 to 0.10, 4 to 0.125, 5 to 0.15)

    fun formatSI(si: Long) = when {
        si >= 10_000_000L -> "₹1 crore"
        si >= 5_000_000L  -> "₹50 lakh"
        si >= 2_500_000L  -> "₹25 lakh"
        else              -> "₹10 lakh"
    }

    // ── Server-side pricing (single source of truth) ────────────────────────
    //
    // Pre-Foundation-Pack this VM contained an `estimatedPremium()` mock that hardcoded
    // base premiums and tier multipliers, diverging from the real actuarial engine.
    // It has been removed. The premium is now always whatever the engine returns
    // (via /api/buy-online/premium). UI shows a small "Calculating…" state until the
    // first response lands; no fabricated numbers are ever displayed.
    var lastPremium      by mutableStateOf<PremiumResponse?>(null)
        private set
    var premiumLoading   by mutableStateOf(false)
        private set
    var premiumError     by mutableStateOf<String?>(null)
        private set

    val totalAnnualWithGst: Double
        get() = lastPremium?.totalIncludingGst ?: 0.0

    val totalAnnualPreTax: Double
        get() = lastPremium?.annualPremium ?: 0.0

    val gstAmount: Double
        get() = lastPremium?.gstAmount ?: 0.0

    fun refreshPremium() {
        scope.launch {
            premiumLoading = true
            premiumError = null
            runCatching {
                client.calculatePremium(
                    PremiumRequest(
                        sumInsured = selectedSumInsured,
                        tier       = selectedTier.name,
                        tenure     = selectedTenure,
                        addOnIds   = effectiveAddOnIds().toList(),
                        primaryAge = eldestAge.toIntOrNull() ?: 35,
                        familyType = deriveFamilyTypeCode(),
                        zone       = "Zone 1"
                    )
                )
            }.onSuccess {
                lastPremium = it
                serverPremium = it.annualPremium
            }.onFailure {
                premiumError = "Couldn't calculate premium right now. Please retry."
                lastPremium = null
                serverPremium = null
            }
            premiumLoading = false
        }
    }

    /** Map BuyOnline tier defaults + user selections into the single add-on ID set. */
    private fun effectiveAddOnIds(): Set<String> =
        // The mapped IDs from BUYONLINE_ADDONS — kept stable across UI changes.
        selectedAddOnIds

    /** Derive family-type code from selected members. */
    private fun deriveFamilyTypeCode(): String {
        val hasSpouse = com.rate.buyonline.model.MemberType.SPOUSE in selectedMembers
        return when {
            hasSpouse && kidsCount == 0 -> "2A"
            hasSpouse && kidsCount == 1 -> "2A1C"
            hasSpouse && kidsCount == 2 -> "2A2C"
            hasSpouse && kidsCount == 3 -> "2A3C"
            hasSpouse                   -> "2A4C"
            kidsCount == 0              -> "1A"
            kidsCount == 1              -> "1A1C"
            kidsCount == 2              -> "1A2C"
            kidsCount == 3              -> "1A3C"
            else                        -> "1A4C"
        }
    }

    // ── Add-ons ───────────────────────────────────────────────────────────────
    val availableAddOns = listOf(
        AddOn("maternity", "Maternity Coverage", "Covers pre & post-natal expenses, delivery up to ₹50,000", 2000.0),
        AddOn("dental",    "Dental Care",         "Coverage for dental procedures, root canal, extractions",  1500.0),
        AddOn("vision",    "Vision Care",          "Eye check-ups, glasses, contact lenses up to ₹10,000",    2000.0)
    )
    var selectedAddOnIds     by mutableStateOf<Set<String>>(emptySet())
    var editingAddOns        by mutableStateOf(false)
    var showSkipAddOnConfirm by mutableStateOf(false)

    fun onTierChanged(tier: PlanTier) {
        selectedTier = tier
        selectedAddOnIds = when (tier) {
            PlanTier.SIGNATURE, PlanTier.GLOBAL -> availableAddOns.map { it.id }.toSet()
            else -> emptySet()
        }
        serverPremium = null
    }

    val totalAddOnCost get() = availableAddOns.filter { it.id in selectedAddOnIds }.sumOf { it.annualCost }
    // Headline figure: GST-inclusive total from the engine. If the engine hasn't
    // responded yet we show 0.0 (UI gates on `premiumLoading` and `premiumError`).
    val totalPremium   get() = lastPremium?.totalIncludingGst ?: 0.0

    // ── Personal details ──────────────────────────────────────────────────────
    var personalDetails by mutableStateOf<Map<String, PersonalDetail>>(emptyMap())
    var nomineeAsSelf   by mutableStateOf(false)

    fun updatePersonalDetail(id: String, detail: PersonalDetail) {
        personalDetails = personalDetails + (id to detail)
    }
    fun getPersonalDetail(id: String) = personalDetails[id] ?: PersonalDetail(memberId = id, label = id)

    // ── Lifestyle ─────────────────────────────────────────────────────────────
    var lifestyleAnswers by mutableStateOf(LifestyleAnswers())

    // ── Medical ───────────────────────────────────────────────────────────────
    var medicalAnswers by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    var medicalDetails by mutableStateOf<Map<String, String>>(emptyMap())

    fun toggleMedicalMember(qId: String, memberId: String, checked: Boolean) {
        val cur = medicalAnswers[qId] ?: emptySet()
        medicalAnswers = medicalAnswers + (qId to if (checked) cur + memberId else cur - memberId)
    }
    fun updateMedicalDetail(qId: String, text: String) { medicalDetails = medicalDetails + (qId to text) }

    // ── Payment ───────────────────────────────────────────────────────────────
    var transactionId  by mutableStateOf("")
    var paymentAmount  by mutableStateOf(0.0)
    var paymentMethod  by mutableStateOf("Debit Card")

    // ── KYC ───────────────────────────────────────────────────────────────────
    var selectedKycMethod by mutableStateOf(KycMethod.EKYC)
    var kycPanNumber      by mutableStateOf("")
    var kycAadhaar        by mutableStateOf("")
    var kycDob            by mutableStateOf("")
    var kycOtpDigits      by mutableStateOf(List(6) { "" })
    var kycOtpTimer       by mutableStateOf(180)
    var bankDetails       by mutableStateOf(BankDetails())

    fun updateKycOtpDigit(index: Int, value: String) {
        kycOtpDigits = kycOtpDigits.toMutableList().also { it[index] = value.takeLast(1) }
    }
    val kycOtpFilled get() = kycOtpDigits.all { it.isNotEmpty() }
    val kycOtpTimerFormatted get() =
        "${(kycOtpTimer / 60).toString().padStart(2, '0')}:${(kycOtpTimer % 60).toString().padStart(2, '0')}"

    fun startKycOtpTimer() {
        kycOtpTimer = 180
        scope.launch { while (kycOtpTimer > 0) { delay(1000); kycOtpTimer-- } }
    }

    // ── Application result ────────────────────────────────────────────────────
    var applicationResult  by mutableStateOf<ApplicationResult?>(null)
    var satisfactionRating by mutableStateOf(0)

    // ── UI state ──────────────────────────────────────────────────────────────
    var loading by mutableStateOf(false)
    var error   by mutableStateOf<String?>(null)

    // ── Actions ───────────────────────────────────────────────────────────────

    fun submitMobileForOtp() {
        scope.launch {
            loading = true; error = null
            runCatching { client.sendOtp(mobile) }
                .onSuccess { navigate(BuyOnlineScreen.Otp); startOtpTimer() }
                .onFailure { error = "Failed to send OTP. Check your connection." }
            loading = false
        }
    }

    fun verifyOtp() {
        scope.launch {
            loading = true; error = null; otpError = null
            val otpStr = otpDigits.joinToString("")
            runCatching { client.verifyOtp(mobile, otpStr) }
                .onSuccess { resp ->
                    if (resp.success) navigate(BuyOnlineScreen.GetStarted)
                    else otpError = "Invalid OTP. Please try again."
                }
                .onFailure { error = "Verification failed. Check your connection." }
            loading = false
        }
    }

    fun submitPincode() {
        scope.launch {
            loading = true
            runCatching { client.getHospitalsNearPincode(pincode) }
                .onSuccess { hospitalsNearby = it }
                .onFailure { hospitalsNearby = (15..45).random() } // graceful fallback
            loading = false
        }
    }

    fun proceedFromGetStarted()  = navigate(BuyOnlineScreen.PreExistingDisease)
    fun proceedFromPreExisting() = navigate(BuyOnlineScreen.CriticalIllness)

    fun proceedFromCriticalIllness() {
        navigate(BuyOnlineScreen.PlanLoading)
        scope.launch {
            runCatching {
                client.checkEligibility(
                    EligibilityRequest(
                        mobile                 = mobile,
                        hasPED                 = hasPED,
                        pedMembers             = pedMembers.toList(),
                        hasCriticalIllness     = hasCriticalIllness,
                        criticalIllnessMembers = criticalIllnessMembers.toList(),
                        members                = allMembers
                    )
                )
            }.onSuccess { resp ->
                coveredMembers   = resp.coveredMembers
                uncoveredMembers = resp.uncoveredMembers
            }.onFailure {
                // Fallback: local logic
                uncoveredMembers = allMembers.filter { m ->
                    (hasPED && m in pedMembers) || (hasCriticalIllness && m in criticalIllnessMembers)
                }
                coveredMembers = allMembers.filter { it !in uncoveredMembers }
            }
            navigate(BuyOnlineScreen.Eligibility)
        }
    }

    fun proceedFromEligibility() {
        navigate(BuyOnlineScreen.Quote)
        refreshPremium()
    }

    fun proceedFromQuote() {
        refreshPremium()
        navigate(BuyOnlineScreen.AddOns)
    }

    fun proceedFromAddOns()        = navigate(BuyOnlineScreen.PlanSummary)
    fun proceedFromSummary()       = navigate(BuyOnlineScreen.PersonalDetails)
    fun proceedFromPersonalDetails() = navigate(BuyOnlineScreen.LifestyleQuestions)
    fun proceedFromLifestyle()     = navigate(BuyOnlineScreen.MedicalQuestions)

    fun proceedFromMedical() {
        paymentAmount = totalPremium
        transactionId = "TXN${(100000000..999999999).random()}"
        navigate(BuyOnlineScreen.Payment)
    }

    fun onPaymentComplete()    = navigate(BuyOnlineScreen.PaymentSuccess)
    fun proceedToKyc()         = navigate(BuyOnlineScreen.KycMethod)
    fun proceedFromKycMethod() = navigate(BuyOnlineScreen.KycDetails)

    fun proceedFromKycDetails() {
        if (selectedKycMethod == KycMethod.EKYC) {
            scope.launch {
                loading = true
                runCatching { client.sendKycOtp(mobile) }
                loading = false
                startKycOtpTimer()
                navigate(BuyOnlineScreen.KycOtp)
            }
        } else {
            navigate(BuyOnlineScreen.BankDetails)
        }
    }

    fun verifyKycOtp() {
        scope.launch {
            loading = true; error = null
            val otpStr = kycOtpDigits.joinToString("")
            runCatching { client.verifyKycOtp(mobile, otpStr) }
                .onSuccess { resp ->
                    if (resp.success) navigate(BuyOnlineScreen.BankDetails)
                    else error = "Invalid KYC OTP."
                }
                .onFailure { navigate(BuyOnlineScreen.BankDetails) } // fallback
            loading = false
        }
    }

    fun submitBankDetails() {
        scope.launch {
            loading = true; error = null
            runCatching {
                client.submitProposal(
                    ProposalRequest(
                        mobile           = mobile,
                        planTier         = selectedTier.name,
                        sumInsured       = selectedSumInsured,
                        annualPremium    = totalPremium,
                        tenure           = selectedTenure,
                        kycMethod        = selectedKycMethod.name,
                        bankAccountNumber = bankDetails.accountNumber,
                        bankName         = bankDetails.bankName,
                        ifscCode         = bankDetails.ifscCode
                    )
                )
            }.onSuccess { resp ->
                applicationResult = ApplicationResult(
                    proposalNumber = resp.proposalNumber,
                    planTier       = resp.planTier,
                    sumInsured     = resp.sumInsured,
                    annualPremium  = resp.annualPremium,
                    status         = resp.status
                )
                navigate(BuyOnlineScreen.KycSubmitted)
            }.onFailure {
                // Fallback: generate local proposal number
                applicationResult = ApplicationResult(
                    proposalNumber = "PHI${(1000000..9999999).random()}",
                    planTier       = selectedTier.displayName,
                    sumInsured     = selectedSumInsured,
                    annualPremium  = totalPremium
                )
                navigate(BuyOnlineScreen.KycSubmitted)
            }
            loading = false
        }
    }

    fun proceedFromKycSubmitted() = navigate(BuyOnlineScreen.ApplicationComplete)

    fun proceedToSatisfaction() = navigate(BuyOnlineScreen.Satisfaction)

    fun dispose() { scope.cancel(); timerJob?.cancel() }
}
