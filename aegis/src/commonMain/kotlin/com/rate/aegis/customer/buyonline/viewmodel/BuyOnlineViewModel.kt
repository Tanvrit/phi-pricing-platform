package com.rate.aegis.customer.buyonline.viewmodel

import androidx.compose.runtime.*
import com.rate.aegis.customer.buyonline.api.*
import com.rate.aegis.customer.buyonline.model.*
import com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen
import com.rate.domain.buyonline.BUYONLINE_ADDONS
import com.rate.domain.buyonline.BuyOnlineTier
import com.rate.domain.buyonline.toPlanId
import com.rate.domain.data.InProcessRateDataProvider
import com.rate.domain.engine.PricingEngine
import com.rate.domain.model.BuyOnlineSessionState
import com.rate.domain.model.CoverSelection
import com.rate.domain.model.Member
import com.rate.domain.model.PaymentMode
import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import com.rate.domain.model.Tenure
import kotlinx.coroutines.*

class BuyOnlineViewModel(
    private val client: BuyOnlineApiClient,
    private val engine: PricingEngine = PricingEngine(InProcessRateDataProvider())
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Navigation ────────────────────────────────────────────────────────────
    var currentScreen by mutableStateOf<BuyOnlineScreen>(BuyOnlineScreen.Landing)
    private val backStack = mutableListOf<BuyOnlineScreen>()

    fun navigate(screen: BuyOnlineScreen) {
        backStack.add(currentScreen); currentScreen = screen
        // Every screen-change is a natural save point — debounced so rapid
        // proceed-clicks (PlanLoading → Eligibility happens within a single
        // coroutine) coalesce into one network round-trip.
        saveSoon()
    }
    fun navigateBack() { if (backStack.isNotEmpty()) currentScreen = backStack.removeLast(); saveSoon() }

    // ── Save+resume session ──────────────────────────────────────────────────
    // sessionId is the opaque hex token the customer carries in their resume URL
    // (`?session=<id>`). Empty string = pre-launch (loadOrCreateSession hasn't
    // run yet); blank stays blank until first interaction so we never leave
    // orphan rows for users who hit Landing and bounce.
    var sessionId: String by mutableStateOf("")
        private set
    private var pendingSaveJob: Job? = null

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

    // ── In-process pricing (single source of truth) ─────────────────────────
    //
    // Pre-Foundation-Pack this VM contained an `estimatedPremium()` mock that hardcoded
    // base premiums and tier multipliers, diverging from the real actuarial engine.
    // Foundation Pack moved it to a server round-trip (/api/buy-online/premium). This
    // iteration moves it again — straight to the engine in-process. The engine is in
    // :shared/commonMain (KMP-portable), so it runs on the WASM/JVM client without any
    // server hop. The HTTP path stays for OTP / proposal / KYC, which still need the
    // server, but every rupee shown to the customer comes from the same `PricingEngine`
    // that the server uses.
    var lastQuote        by mutableStateOf<QuoteResult?>(null)
        private set
    var premiumLoading   by mutableStateOf(false)
        private set
    var premiumError     by mutableStateOf<String?>(null)
        private set

    val totalAnnualWithGst: Double
        get() = lastQuote?.totalIncludingGst ?: 0.0

    val totalAnnualPreTax: Double
        get() = lastQuote?.let { it.totalAfterDiscount + it.instalmentLoadingAmount } ?: 0.0

    val gstAmount: Double
        get() = lastQuote?.gstAmount ?: 0.0

    fun refreshPremium() {
        scope.launch {
            premiumLoading = true
            premiumError = null
            runCatching {
                engine.calculate(buildQuoteRequest())
            }.onSuccess { result ->
                if (result.isValid) {
                    lastQuote = result
                    serverPremium = result.totalAfterDiscount + result.instalmentLoadingAmount
                } else {
                    premiumError = result.validationErrors.firstOrNull()
                        ?: "Unable to calculate premium for this combination."
                    lastQuote = null
                    serverPremium = null
                }
            }.onFailure {
                premiumError = "Couldn't calculate premium right now. Please retry."
                lastQuote = null
                serverPremium = null
            }
            premiumLoading = false
        }
    }

    /** Build a QuoteRequest from the current UI state. Tier → planId via shared mapping. */
    private fun buildQuoteRequest(): QuoteRequest {
        val tier = when (selectedTier) {
            PlanTier.PREMIER   -> BuyOnlineTier.PREMIER
            PlanTier.SIGNATURE -> BuyOnlineTier.SIGNATURE
            PlanTier.GLOBAL    -> BuyOnlineTier.GLOBAL
        }
        val tenureYears = selectedTenure.coerceIn(1, 5)
        val primaryAge = eldestAge.toIntOrNull() ?: 35
        val members = buildEngineMembers(primaryAge)
        val covers = BUYONLINE_ADDONS
            .filter { it.id in selectedAddOnIds }
            .map { CoverSelection(it.coverId, it.defaultParam) }
        return QuoteRequest(
            planId         = tier.toPlanId(),
            primaryAge     = primaryAge,
            sumInsured     = selectedSumInsured,
            familyType     = deriveFamilyTypeCode(),
            zone           = "Zone 1",
            tenure         = Tenure.fromYears(tenureYears),
            paymentMode    = PaymentMode.ANNUAL,
            paymentTenure  = Tenure.fromYears(tenureYears),
            members        = members,
            selectedCovers = covers
        )
    }

    /** Build the engine's Member list from the current member selection. */
    private fun buildEngineMembers(primaryAge: Int): List<Member> {
        val out = mutableListOf(Member(memberId = 1, age = primaryAge, relationship = "Self"))
        var id = 2
        if (MemberType.SPOUSE in selectedMembers) {
            // Spouse age unknown at this stage; use primary age as a proxy (real flow captures it later).
            out += Member(memberId = id++, age = primaryAge, relationship = "Spouse", gender = "F")
        }
        repeat(kidsCount) {
            out += Member(memberId = id++, age = 5, relationship = "Son")
        }
        return out
    }

    /** Map BuyOnline tier defaults + user selections into the single add-on ID set. */
    private fun effectiveAddOnIds(): Set<String> =
        // The mapped IDs from BUYONLINE_ADDONS — kept stable across UI changes.
        selectedAddOnIds

    /** Derive family-type code from selected members. */
    private fun deriveFamilyTypeCode(): String {
        val hasSpouse = com.rate.aegis.customer.buyonline.model.MemberType.SPOUSE in selectedMembers
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
        // Tier toggles can happen multiple times on the Quote screen without
        // navigating; persist them so a customer who fiddled with tiers then
        // closed the tab gets their last choice back.
        saveSoon()
    }

    val totalAddOnCost get() = availableAddOns.filter { it.id in selectedAddOnIds }.sumOf { it.annualCost }
    // Headline figure: GST-inclusive total from the engine. If the engine hasn't
    // responded yet we show 0.0 (UI gates on `premiumLoading` and `premiumError`).
    val totalPremium   get() = lastQuote?.totalIncludingGst ?: 0.0

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

    // ── Save+resume helpers ──────────────────────────────────────────────────
    //
    // Strategy: take a flat snapshot of the high-effort UI inputs (members,
    // health declarations, plan choices), serialise, ship to the server with a
    // 1.5s debounce. On boot, if a `?session=` id was in the URL we GET the
    // snapshot back and rehydrate. KYC/payment/proposal fields are NOT in the
    // snapshot — they're either sensitive or single-use and shouldn't survive
    // a session restore.

    /** Build a serialisable snapshot of the current journey state. */
    fun snapshot(): BuyOnlineSessionState = BuyOnlineSessionState(
        sessionId              = sessionId,
        currentScreen          = screenName(currentScreen),
        mobile                 = mobile,
        pincode                = pincode,
        eldestAge              = eldestAge,
        selectedMembers        = selectedMembers.map { it.name },
        kidsCount              = kidsCount,
        hasPED                 = hasPED,
        pedMembers             = pedMembers.toList(),
        hasCriticalIllness     = hasCriticalIllness,
        criticalIllnessMembers = criticalIllnessMembers.toList(),
        selectedTier           = selectedTier.name,
        selectedSumInsured     = selectedSumInsured,
        selectedTenure         = selectedTenure,
        selectedAddOnIds       = selectedAddOnIds.toList(),
        updatedAtIso           = kotlinx.datetime.Clock.System.now().toString()
    )

    /** Apply a previously-saved snapshot, then re-run the pricing engine. */
    fun restore(state: BuyOnlineSessionState) {
        sessionId = state.sessionId
        mobile = state.mobile
        pincode = state.pincode
        eldestAge = state.eldestAge
        selectedMembers = state.selectedMembers.mapNotNull {
            runCatching { MemberType.valueOf(it) }.getOrNull()
        }.toSet().ifEmpty { setOf(MemberType.SELF) }
        kidsCount = state.kidsCount
        hasPED = state.hasPED
        pedMembers = state.pedMembers.toSet()
        hasCriticalIllness = state.hasCriticalIllness
        criticalIllnessMembers = state.criticalIllnessMembers.toSet()
        selectedTier = runCatching { PlanTier.valueOf(state.selectedTier) }.getOrElse { PlanTier.PREMIER }
        selectedSumInsured = state.selectedSumInsured
        selectedTenure = state.selectedTenure
        selectedAddOnIds = state.selectedAddOnIds.toSet()
        currentScreen = screenFromName(state.currentScreen)
        // The restored screen may be Quote / AddOns / Summary — all of which
        // expect `lastQuote` to be populated. Kick the engine so the UI doesn't
        // flash through a "₹0" state.
        refreshPremium()
    }

    /**
     * Seed the journey VM from a previously-saved shared quote (the operator-
     * shared `?quote=<id>` link). The customer arrives on SharedQuoteView, sees
     * the read-only summary, and taps "Continue to apply" — we replay the
     * inputs (age, family, SI, tenure, tier, add-ons) into the VM and drop
     * them on the Quote screen so they don't re-enter what the advisor
     * already captured.
     *
     * We do NOT auto-advance past Quote — the customer should see the seeded
     * inputs and confirm. We also fire [refreshPremium] so the rendered total
     * comes from the live engine instead of stale `lastQuote = null`.
     */
    fun seedFromSharedQuote(detail: QuoteDetailResponse) {
        val req = detail.request
        eldestAge          = req.primaryAge.toString()
        selectedSumInsured = req.sumInsured
        selectedTenure     = req.tenure.years
        selectedTier       = reverseMapTier(req.planId)
        val (members, kids) = parseFamilyType(req.familyType)
        selectedMembers = members
        kidsCount       = kids
        // Convert engine cover IDs back to BuyOnline add-on UI ids. Any cover
        // not in the BuyOnline catalogue (the engine ships ~50, BuyOnline curates ~8)
        // is silently dropped — the customer can re-add it on the AddOns screen.
        val coverIds = req.selectedCovers.map { it.coverId }.toSet()
        selectedAddOnIds = BUYONLINE_ADDONS
            .filter { it.coverId in coverIds }
            .map { it.id }
            .toSet()
        currentScreen = BuyOnlineScreen.Quote
        backStack.clear() // No earlier screens in this entry path — back goes nowhere meaningful.
        refreshPremium()
        saveSoon()
    }

    /** Reverse the BuyOnlinePlanMapping.toPlanId() mapping. Unknown ids fall back to PREMIER. */
    private fun reverseMapTier(planId: String): PlanTier = when (planId) {
        "PHI_BASIC"     -> PlanTier.PREMIER
        "PHI_FLAGSHIP1" -> PlanTier.SIGNATURE
        "PHI_GLOBAL1"   -> PlanTier.GLOBAL
        else            -> PlanTier.PREMIER
    }

    /**
     * Inverse of [deriveFamilyTypeCode]. Codes follow the pattern `<n>A<m>C`
     * where n=adults (1 or 2 today; 3 tolerated defensively) and m=kids.
     * Returns the canonical UI selection (members + kid count). Unknown
     * codes fall back to a SELF-only profile.
     */
    private fun parseFamilyType(code: String): Pair<Set<MemberType>, Int> {
        val hasSpouse = code.startsWith("2A") || code.startsWith("3A")
        val kidsCount = code.substringAfter("A", "").substringBefore("C").toIntOrNull() ?: 0
        val members = buildSet {
            add(MemberType.SELF)
            if (hasSpouse) add(MemberType.SPOUSE)
        }
        return members to kidsCount
    }

    /**
     * Boot-time hook. If [maybeId] is non-null and the server has a snapshot
     * for it, we restore. Otherwise we generate a fresh hex id and keep it
     * stable for the rest of the session — but we don't save until the user
     * has done something worth saving (first navigate or first tier change).
     */
    fun loadOrCreateSession(maybeId: String?) {
        scope.launch {
            if (!maybeId.isNullOrBlank()) {
                val loaded = client.loadSession(maybeId)
                if (loaded != null) { restore(loaded); return@launch }
                sessionId = maybeId  // keep the customer's URL stable even if the row was lost
            } else {
                sessionId = randomHexId()
            }
        }
    }

    private fun randomHexId(): String =
        kotlin.random.Random.nextBytes(16).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    /** Debounced save — collapses bursts of mutations into one POST. */
    fun saveSoon() {
        if (sessionId.isBlank()) return
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(1500)
            runCatching { client.saveSession(snapshot()) }
        }
    }

    /** Sealed-subtype → stable string for the wire. */
    private fun screenName(screen: BuyOnlineScreen): String =
        screen::class.simpleName ?: "Landing"

    /**
     * Stable string → sealed-subtype. Kept as an explicit `when` (not reflective)
     * for KMP portability — `KClass.objectInstance` works on JVM but not WASM,
     * and the screen set is small enough that an exhaustive map costs nothing.
     */
    private fun screenFromName(name: String): BuyOnlineScreen = when (name) {
        "Landing"             -> BuyOnlineScreen.Landing
        "Otp"                 -> BuyOnlineScreen.Otp
        "GetStarted"          -> BuyOnlineScreen.GetStarted
        "PreExistingDisease"  -> BuyOnlineScreen.PreExistingDisease
        "CriticalIllness"     -> BuyOnlineScreen.CriticalIllness
        "PlanLoading"         -> BuyOnlineScreen.PlanLoading
        "Eligibility"         -> BuyOnlineScreen.Eligibility
        "Quote"               -> BuyOnlineScreen.Quote
        "AddOns"              -> BuyOnlineScreen.AddOns
        "PlanSummary"         -> BuyOnlineScreen.PlanSummary
        "PersonalDetails"     -> BuyOnlineScreen.PersonalDetails
        "LifestyleQuestions"  -> BuyOnlineScreen.LifestyleQuestions
        "MedicalQuestions"    -> BuyOnlineScreen.MedicalQuestions
        "Payment"             -> BuyOnlineScreen.Payment
        "PaymentSuccess"      -> BuyOnlineScreen.PaymentSuccess
        "KycMethod"           -> BuyOnlineScreen.KycMethod
        "KycDetails"          -> BuyOnlineScreen.KycDetails
        "KycOtp"              -> BuyOnlineScreen.KycOtp
        "BankDetails"         -> BuyOnlineScreen.BankDetails
        "KycSubmitted"        -> BuyOnlineScreen.KycSubmitted
        "ApplicationComplete" -> BuyOnlineScreen.ApplicationComplete
        "Satisfaction"        -> BuyOnlineScreen.Satisfaction
        else                  -> BuyOnlineScreen.Landing
    }
}
