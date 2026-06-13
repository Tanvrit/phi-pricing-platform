package com.rate.sdk.ui.buyonline.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.proposal.model.BuyOnlineSessionState
import com.rate.sdk.proposal.network.BuyOnlineApi
import com.rate.sdk.proposal.network.ProposalSubmitRequest
import com.rate.sdk.proposal.model.journey.EligibilityRequest
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.quoting.network.SavedQuoteView
import com.rate.sdk.ui.buyonline.model.AddOn
import com.rate.sdk.ui.buyonline.model.ApplicationResult
import com.rate.sdk.ui.buyonline.model.BankDetails
import com.rate.sdk.ui.buyonline.model.KycMethod
import com.rate.sdk.ui.buyonline.model.LifestyleAnswers
import com.rate.sdk.ui.buyonline.model.MemberType
import com.rate.sdk.ui.buyonline.model.PersonalDetail
import com.rate.sdk.ui.buyonline.model.PlanTier
import com.rate.sdk.ui.buyonline.model.coverSelectionsFor
import com.rate.sdk.ui.buyonline.model.planIdToTier
import com.rate.sdk.ui.buyonline.model.toBuyOnlineTier
import com.rate.sdk.ui.buyonline.model.toPlanId
import com.rate.sdk.ui.buyonline.navigation.BuyOnlineScreen
import com.rate.sdk.ui.buyonline.platform.resumeUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * State holder for the 22-screen buy-online customer journey.
 *
 * RELOCATED from the monolith's
 * `com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel`. The screen
 * surface (every `var` the composables bind to + every `proceedFromX()`) is
 * preserved byte-for-byte so the relocated screens compile unchanged.
 *
 * The one structural change is the data path. The monolith drove pricing through
 * an in-process `PricingEngine` and OTP/proposal/KYC through a hand-rolled
 * `BuyOnlineApiClient`. The re-architecture forbids the concrete engine + Mongo
 * repositories in any UI module — they are server-only. So this VM speaks ONLY
 * the feature SDKs' Ktor-client surfaces, layered on core-network's
 * `TanvritClient`:
 *  - [buyOnline] (sdk-proposal `BuyOnlineApi`) for OTP, pincode/hospitals,
 *    eligibility, KYC-OTP, proposal submit/track and save+resume;
 *  - [quotes] (sdk-quoting `QuoteApi`) for premium — `calculate()` returns the
 *    full server-priced [QuoteResult] the Quote/AddOns/Summary screens render,
 *    so every rupee the customer sees still comes from the same engine the
 *    server runs, just over the wire.
 *
 * [emailResumeLink] is injected (the monolith's `emailResumeLink` HTTP call) so
 * the ResumeBanner can mail the resume URL without re-constructing a client.
 */
class BuyOnlineViewModel(
    private val buyOnline: BuyOnlineApi,
    private val quotes: QuoteApi,
    private val emailResumeLink: suspend (email: String, url: String) -> Boolean = { _, _ -> false },
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Navigation ────────────────────────────────────────────────────────────
    var currentScreen by mutableStateOf<BuyOnlineScreen>(BuyOnlineScreen.Landing)
    private val backStack = mutableListOf<BuyOnlineScreen>()

    fun navigate(screen: BuyOnlineScreen) {
        backStack.add(currentScreen); currentScreen = screen
        // Every screen-change is a natural save point — debounced so rapid
        // proceed-clicks coalesce into one network round-trip.
        saveSoon()
    }
    fun navigateBack() { if (backStack.isNotEmpty()) currentScreen = backStack.removeLast(); saveSoon() }

    // ── Save+resume session ──────────────────────────────────────────────────
    var sessionId: String by mutableStateOf("")
        private set
    var sessionUpdatedAtIso: String by mutableStateOf("")
        private set
    private var pendingSaveJob: Job? = null

    /** Surfaced so the ResumeBanner can build/share the resume URL + mail it. */
    fun resumeUrlFor(id: String): String = resumeUrl(id)
    suspend fun sendResumeEmail(email: String, url: String): Boolean = emailResumeLink(email, url)

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
    var serverPremium      by mutableStateOf<Double?>(null)  // null = not yet priced

    val sumInsuredOptions = listOf(1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L)
    val tenureOptions     = listOf(1, 2, 3, 4, 5)
    val tenureDiscounts   = mapOf(1 to 0.0, 2 to 0.075, 3 to 0.10, 4 to 0.125, 5 to 0.15)

    fun formatSI(si: Long) = when {
        si >= 10_000_000L -> "₹1 crore"
        si >= 5_000_000L  -> "₹50 lakh"
        si >= 2_500_000L  -> "₹25 lakh"
        else              -> "₹10 lakh"
    }

    // ── Server-priced quote (single source of truth) ────────────────────────
    //
    // The monolith priced in-process; here every figure comes back from the
    // server's quoting engine via QuoteApi.calculate() — the same engine, just
    // over the wire (the concrete PricingEngine is server-only in the new DAG).
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
                quotes.calculate(buildQuoteRequest()).result
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
        val tenureYears = selectedTenure.coerceIn(1, 5)
        val primaryAge = eldestAge.toIntOrNull() ?: 35
        val members = buildEngineMembers(primaryAge)
        val covers = coverSelectionsFor(selectedAddOnIds)
        return QuoteRequest(
            planId         = selectedTier.toBuyOnlineTier().toPlanId(),
            primaryAge     = primaryAge,
            sumInsured     = selectedSumInsured,
            familyType     = deriveFamilyTypeCode(),
            zone           = "Zone 1",
            tenure         = Tenure.fromYears(tenureYears),
            paymentMode    = PaymentMode.ANNUAL,
            paymentTenure  = Tenure.fromYears(tenureYears),
            members        = members,
            selectedCovers = covers,
        )
    }

    /** Build the engine's Member list from the current member selection. */
    private fun buildEngineMembers(primaryAge: Int): List<Member> {
        val out = mutableListOf(Member(memberId = 1, age = primaryAge, relationship = "Self"))
        var id = 2
        if (MemberType.SPOUSE in selectedMembers) {
            // Spouse age unknown at this stage; use primary age as a proxy (captured later).
            out += Member(memberId = id++, age = primaryAge, relationship = "Spouse", gender = "F")
        }
        repeat(kidsCount) {
            out += Member(memberId = id++, age = 5, relationship = "Son")
        }
        return out
    }

    /** Derive family-type code from selected members. */
    private fun deriveFamilyTypeCode(): String {
        val hasSpouse = MemberType.SPOUSE in selectedMembers
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
        AddOn("vision",    "Vision Care",          "Eye check-ups, glasses, contact lenses up to ₹10,000",    2000.0),
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
        saveSoon()
    }

    val totalAddOnCost get() = availableAddOns.filter { it.id in selectedAddOnIds }.sumOf { it.annualCost }
    // Headline figure: GST-inclusive total from the server engine.
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
            runCatching { buyOnline.sendOtp(mobile) }
                .onSuccess { navigate(BuyOnlineScreen.Otp); startOtpTimer() }
                .onFailure { error = "Failed to send OTP. Check your connection." }
            loading = false
        }
    }

    fun verifyOtp() {
        scope.launch {
            loading = true; error = null; otpError = null
            val otpStr = otpDigits.joinToString("")
            runCatching { buyOnline.verifyOtp(mobile, otpStr) }
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
            runCatching { buyOnline.hospitals(pincode).hospitalsNearby }
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
                buyOnline.eligibility(
                    EligibilityRequest(
                        mobile                 = mobile,
                        hasPED                 = hasPED,
                        pedMembers             = pedMembers.toList(),
                        hasCriticalIllness     = hasCriticalIllness,
                        criticalIllnessMembers = criticalIllnessMembers.toList(),
                        members                = allMembers,
                    ),
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

    fun proceedFromAddOns()          = navigate(BuyOnlineScreen.PlanSummary)
    fun proceedFromSummary()         = navigate(BuyOnlineScreen.PersonalDetails)
    fun proceedFromPersonalDetails() = navigate(BuyOnlineScreen.LifestyleQuestions)
    fun proceedFromLifestyle()       = navigate(BuyOnlineScreen.MedicalQuestions)

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
                runCatching { buyOnline.sendKycOtp(mobile) }
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
            runCatching { buyOnline.verifyKycOtp(mobile, otpStr) }
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
                buyOnline.submitProposal(
                    ProposalSubmitRequest(
                        mobile            = mobile,
                        planTier          = selectedTier.name,
                        sumInsured        = selectedSumInsured,
                        annualPremium     = totalAnnualPreTax,
                        totalIncludingGst = totalAnnualWithGst,
                        tenure            = selectedTenure,
                        quoteRef          = lastQuote?.requestId,
                        kycMethod         = selectedKycMethod.name,
                        bankAccountNumber = bankDetails.accountNumber,
                        bankName          = bankDetails.bankName,
                        ifscCode          = bankDetails.ifscCode,
                        accountHolderName = getPersonalDetail("Myself").let {
                            listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ")
                        },
                        selectedAddOnIds  = selectedAddOnIds.toList(),
                        members           = buildEngineMembers(eldestAge.toIntOrNull() ?: 35),
                    ),
                )
            }.onSuccess { resp ->
                applicationResult = ApplicationResult(
                    proposalNumber = resp.proposalNumber,
                    planTier       = resp.planTier,
                    sumInsured     = resp.sumInsured,
                    annualPremium  = resp.annualPremium,
                    status         = resp.status,
                )
                navigate(BuyOnlineScreen.KycSubmitted)
            }.onFailure {
                // Fallback: generate local proposal number so the journey completes offline.
                applicationResult = ApplicationResult(
                    proposalNumber = "PHI${(1000000..9999999).random()}",
                    planTier       = selectedTier.displayName,
                    sumInsured     = selectedSumInsured,
                    annualPremium  = totalPremium,
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
        updatedAtIso           = Clock.System.now().toString(),
    )

    /** Apply a previously-saved snapshot, then re-price. */
    fun restore(state: BuyOnlineSessionState) {
        sessionId = state.sessionId
        sessionUpdatedAtIso = state.updatedAtIso
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
        refreshPremium()
    }

    /**
     * Seed the journey VM from a previously-saved shared quote (the operator-
     * shared `?quote=<id>` link). Replays the inputs onto the Quote screen so
     * the customer confirms rather than re-enters what the advisor captured.
     */
    fun seedFromSharedQuote(detail: SavedQuoteView) {
        val req = detail.request
        eldestAge          = req.primaryAge.toString()
        selectedSumInsured = req.sumInsured
        selectedTenure     = req.tenure.years
        selectedTier       = when (planIdToTier(req.planId)) {
            com.rate.sdk.ui.buyonline.model.BuyOnlineTier.PREMIER   -> PlanTier.PREMIER
            com.rate.sdk.ui.buyonline.model.BuyOnlineTier.SIGNATURE -> PlanTier.SIGNATURE
            com.rate.sdk.ui.buyonline.model.BuyOnlineTier.GLOBAL    -> PlanTier.GLOBAL
        }
        val (members, kids) = parseFamilyType(req.familyType)
        selectedMembers = members
        kidsCount       = kids
        // Convert engine cover ids back to BuyOnline add-on UI ids; covers not in
        // the curated catalogue are silently dropped (customer can re-add later).
        val coverIds = req.selectedCovers.map { it.coverId }.toSet()
        selectedAddOnIds = com.rate.sdk.ui.buyonline.model.BUYONLINE_ADDONS
            .filter { it.coverId in coverIds }
            .map { it.id }
            .toSet()
        currentScreen = BuyOnlineScreen.Quote
        backStack.clear()
        refreshPremium()
        saveSoon()
    }

    /**
     * Inverse of [deriveFamilyTypeCode]. Codes follow `<n>A<m>C`. Unknown codes
     * fall back to a SELF-only profile.
     */
    private fun parseFamilyType(code: String): Pair<Set<MemberType>, Int> {
        val hasSpouse = code.startsWith("2A") || code.startsWith("3A")
        val kids = code.substringAfter("A", "").substringBefore("C").toIntOrNull() ?: 0
        val members = buildSet {
            add(MemberType.SELF)
            if (hasSpouse) add(MemberType.SPOUSE)
        }
        return members to kids
    }

    /**
     * Boot-time hook. If [maybeId] is non-null and the server has a snapshot for
     * it, restore. Otherwise mint a fresh hex id and keep it stable — but don't
     * save until the customer does something worth saving.
     */
    fun loadOrCreateSession(maybeId: String?) {
        scope.launch {
            if (!maybeId.isNullOrBlank()) {
                val loaded = runCatching { buyOnline.loadSession(maybeId) }.getOrNull()
                if (loaded != null) { restore(loaded); return@launch }
                sessionId = maybeId  // keep the customer's URL stable even if the row was lost
                sessionUpdatedAtIso = Clock.System.now().toString()
            } else {
                sessionId = randomHexId()
                sessionUpdatedAtIso = Clock.System.now().toString()
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
            val now = Clock.System.now().toString()
            runCatching { buyOnline.saveSession(snapshot()) }
                .onSuccess { sessionUpdatedAtIso = now }
        }
    }

    /** Sealed-subtype → stable string for the wire. */
    private fun screenName(screen: BuyOnlineScreen): String =
        screen::class.simpleName ?: "Landing"

    /** Stable string → sealed-subtype. Explicit `when` for KMP portability. */
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
