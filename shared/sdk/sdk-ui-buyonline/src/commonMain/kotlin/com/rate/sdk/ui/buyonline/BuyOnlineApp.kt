package com.rate.sdk.ui.buyonline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.core.network.client.TanvritClient
import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.proposal.network.BuyOnlineApi
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.ui.buyonline.navigation.BuyOnlineScreen
import com.rate.sdk.ui.buyonline.platform.clearQuoteParam
import com.rate.sdk.ui.buyonline.platform.launchQuoteId
import com.rate.sdk.ui.buyonline.platform.launchSessionId
import com.rate.sdk.ui.buyonline.platform.setBeforeLeaveHandler
import com.rate.sdk.ui.buyonline.screens.addons.AddOnsScreen
import com.rate.sdk.ui.buyonline.screens.complete.ApplicationCompleteScreen
import com.rate.sdk.ui.buyonline.screens.complete.SatisfactionScreen
import com.rate.sdk.ui.buyonline.screens.components.FloatingHelpButton
import com.rate.sdk.ui.buyonline.screens.components.ResumeBanner
import com.rate.sdk.ui.buyonline.screens.components.SessionExpiresCallout
import com.rate.sdk.ui.buyonline.screens.components.StepIndicator
import com.rate.sdk.ui.buyonline.screens.details.PersonalDetailsScreen
import com.rate.sdk.ui.buyonline.screens.getstarted.GetStartedScreen
import com.rate.sdk.ui.buyonline.screens.health.CriticalIllnessScreen
import com.rate.sdk.ui.buyonline.screens.health.PreExistingDiseaseScreen
import com.rate.sdk.ui.buyonline.screens.kyc.BankDetailsScreen
import com.rate.sdk.ui.buyonline.screens.kyc.KycDetailsScreen
import com.rate.sdk.ui.buyonline.screens.kyc.KycMethodScreen
import com.rate.sdk.ui.buyonline.screens.kyc.KycOtpScreen
import com.rate.sdk.ui.buyonline.screens.kyc.KycSubmittedScreen
import com.rate.sdk.ui.buyonline.screens.landing.LandingScreen
import com.rate.sdk.ui.buyonline.screens.loading.EligibilityScreen
import com.rate.sdk.ui.buyonline.screens.loading.PlanLoadingScreen
import com.rate.sdk.ui.buyonline.screens.otp.OtpScreen
import com.rate.sdk.ui.buyonline.screens.payment.PaymentScreen
import com.rate.sdk.ui.buyonline.screens.payment.PaymentSuccessScreen
import com.rate.sdk.ui.buyonline.screens.questionnaire.LifestyleQuestionsScreen
import com.rate.sdk.ui.buyonline.screens.questionnaire.MedicalQuestionsScreen
import com.rate.sdk.ui.buyonline.screens.quote.QuoteScreen
import com.rate.sdk.ui.buyonline.screens.summary.PlanSummaryScreen
import com.rate.sdk.ui.buyonline.viewmodel.BuyOnlineViewModel
import com.rate.sdk.ui.buyonline.viewmodel.SessionEmailRequest
import com.rate.sdk.ui.kit.brand.PRUHealthTheme
import com.rate.sdk.ui.kit.brand.PruBackground
import com.rate.sdk.ui.kit.brand.PruRed
import com.rate.sdk.ui.kit.brand.PruSubtext
import com.rate.sdk.ui.kit.brand.PruText
import com.rate.sdk.ui.kit.i18n.AegisLocale
import com.rate.sdk.ui.kit.i18n.LocalAegisLocale
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The single public entry point for the buy-online customer journey.
 *
 * RELOCATED from the monolith's `com.rate.aegis.customer.buyonline.BuyOnlineApp`.
 * The monolith reached into the operator app shell for launch context
 * (`AegisLaunchContext`), settings (`AegisSettingsStore`) and a hand-rolled
 * `BuyOnlineApiClient`. This version is self-contained and pure-KMP:
 *
 *  - networking goes through the supplied core-network [client] ([TanvritClient]),
 *    wrapped into sdk-proposal's [BuyOnlineApi] + sdk-quoting's [QuoteApi];
 *  - launch context (`?session=` / `?quote=`) comes from the module's own
 *    platform seam (overridable via [sessionId] / [quoteId] for the host/tests);
 *  - locale is host-owned: the host passes the active [locale] + an
 *    [onLocaleChange] callback (no settings-store dependency), and we publish it
 *    via [LocalAegisLocale] so every `t("…")` lookup resolves;
 *  - [loadPlan] is an optional plan-enrichment hook for the shared-quote view
 *    (the host fetches the core rating-contract [Plan] over the wire so this
 *    module needs no catalog dependency).
 *
 * @param client      the shared core-network HTTP client (engine supplied per platform by host)
 * @param sessionId   resume bearer; defaults to the platform `?session=` / `-Daegis.session` read
 * @param quoteId     shared-quote id; defaults to the platform `?quote=` / `-Daegis.quote` read
 * @param locale      host-owned active journey locale (EN/HI)
 * @param onLocaleChange invoked when the in-journey switcher flips the language
 * @param loadPlan    optional plan-enrichment lookup for [SharedQuoteView]
 */
@Composable
fun BuyOnlineApp(
    client: TanvritClient,
    sessionId: String? = remember { launchSessionId() },
    quoteId: String? = remember { launchQuoteId() },
    locale: AegisLocale = AegisLocale.EN,
    onLocaleChange: (AegisLocale) -> Unit = {},
    loadPlan: suspend (planId: String) -> Plan? = { null },
) {
    val baseUrl = client.config.normalizedBaseUrl
    val buyOnline = remember(client) { BuyOnlineApi(client.http, baseUrl) }
    val quotes = remember(client) { QuoteApi(client.http, baseUrl) }
    val emailResumeLink: suspend (String, String) -> Boolean = remember(client) {
        { email, url ->
            // The mail-resume route isn't part of BuyOnlineApi's surface (Phase-1
            // mock that only audit-records); POST it via the shared client and
            // ignore the body — we only need 2xx/not for the toast copy.
            runCatching {
                client.execute(HttpMethod.Post, "/api/buy-online/session/email") {
                    contentType(ContentType.Application.Json)
                    setBody(SessionEmailRequest(email = email, url = url))
                }
            }.isSuccess
        }
    }

    val vm = remember(client) { BuyOnlineViewModel(buyOnline, quotes, emailResumeLink) }

    CompositionLocalProvider(LocalAegisLocale provides locale) {
        // Shared-quote short-circuit. `?quote=<id>` means the operator handed the
        // customer a saved-calculation link — render a read-only summary instead
        // of the 22-screen journey until they tap "Continue to apply".
        var showSharedQuote by remember { mutableStateOf(quoteId != null) }
        if (showSharedQuote && quoteId != null) {
            PRUHealthTheme {
                SharedQuoteView(
                    quoteId = quoteId,
                    quotes = quotes,
                    onContinue = {
                        clearQuoteParam()
                        showSharedQuote = false
                    },
                    onContinueWithQuote = { detail -> vm.seedFromSharedQuote(detail) },
                    loadPlan = loadPlan,
                )
            }
            return@CompositionLocalProvider
        }

        // Save+resume bootstrap. Null = brand-new customer; the VM mints a fresh id.
        LaunchedEffect(Unit) { vm.loadOrCreateSession(sessionId) }

        val ageDays = remember(vm.sessionUpdatedAtIso) {
            val updated = runCatching { Instant.parse(vm.sessionUpdatedAtIso) }.getOrNull()
            if (updated == null) null else (Clock.System.now() - updated).inWholeDays
        }

        val resumed = remember { !sessionId.isNullOrBlank() }
        var welcomeBackShown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (resumed) {
                delay(800)
                welcomeBackShown = true
            }
        }
        LaunchedEffect(welcomeBackShown) {
            if (welcomeBackShown) {
                delay(6_000)
                welcomeBackShown = false
            }
        }

        // Browser back-button guard — active once the customer has a session and
        // is mid-journey (not on Landing or a terminal screen). JVM is a no-op.
        val shouldGuard = vm.sessionId.isNotBlank() &&
            vm.currentScreen !is BuyOnlineScreen.Landing &&
            vm.currentScreen !is BuyOnlineScreen.ApplicationComplete &&
            vm.currentScreen !is BuyOnlineScreen.Satisfaction
        DisposableEffect(shouldGuard) {
            setBeforeLeaveHandler(shouldGuard)
            onDispose { setBeforeLeaveHandler(false) }
        }

        PRUHealthTheme {
            val showBanner = vm.sessionId.isNotBlank() && when (vm.currentScreen) {
                is BuyOnlineScreen.Landing,
                is BuyOnlineScreen.ApplicationComplete,
                is BuyOnlineScreen.Satisfaction -> false
                else -> true
            }
            val showSteps = when (vm.currentScreen) {
                is BuyOnlineScreen.Landing,
                is BuyOnlineScreen.ApplicationComplete,
                is BuyOnlineScreen.Satisfaction -> false
                else -> true
            }
            val showWelcomeBack = welcomeBackShown && vm.currentScreen !is BuyOnlineScreen.Landing
            val showExpires = ageDays != null && ageDays >= 25 &&
                vm.currentScreen !is BuyOnlineScreen.ApplicationComplete &&
                vm.currentScreen !is BuyOnlineScreen.Satisfaction
            val showLanguageSwitcher = when (vm.currentScreen) {
                is BuyOnlineScreen.Landing,
                is BuyOnlineScreen.ApplicationComplete,
                is BuyOnlineScreen.Satisfaction -> false
                else -> true
            }
            val showHelpFab = when (vm.currentScreen) {
                is BuyOnlineScreen.ApplicationComplete,
                is BuyOnlineScreen.Satisfaction -> false
                else -> true
            }

            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if (showExpires && ageDays != null) {
                        val daysLeft = (30 - ageDays).coerceAtLeast(0)
                        SessionExpiresCallout(ageDays = ageDays, daysLeft = daysLeft)
                    }
                    if (showBanner) {
                        ResumeBanner(sessionId = vm.sessionId, onEmailResumeLink = vm::sendResumeEmail)
                    }
                    if (showWelcomeBack) {
                        WelcomeBackCallout(label = screenLabel(vm.currentScreen))
                    }
                    if (showLanguageSwitcher) {
                        LanguageSwitcher(active = locale, onChange = onLocaleChange)
                    }
                    if (showSteps) {
                        StepIndicator(currentScreen = vm.currentScreen)
                    }
                    Column(Modifier.weight(1f)) {
                        when (vm.currentScreen) {
                            is BuyOnlineScreen.Landing             -> LandingScreen(vm)
                            is BuyOnlineScreen.Otp                 -> OtpScreen(vm)
                            is BuyOnlineScreen.GetStarted          -> GetStartedScreen(vm)
                            is BuyOnlineScreen.PreExistingDisease  -> PreExistingDiseaseScreen(vm)
                            is BuyOnlineScreen.CriticalIllness     -> CriticalIllnessScreen(vm)
                            is BuyOnlineScreen.PlanLoading         -> PlanLoadingScreen(vm)
                            is BuyOnlineScreen.Eligibility         -> EligibilityScreen(vm)
                            is BuyOnlineScreen.Quote               -> QuoteScreen(vm)
                            is BuyOnlineScreen.AddOns              -> AddOnsScreen(vm)
                            is BuyOnlineScreen.PlanSummary         -> PlanSummaryScreen(vm)
                            is BuyOnlineScreen.PersonalDetails     -> PersonalDetailsScreen(vm)
                            is BuyOnlineScreen.LifestyleQuestions  -> LifestyleQuestionsScreen(vm)
                            is BuyOnlineScreen.MedicalQuestions    -> MedicalQuestionsScreen(vm)
                            is BuyOnlineScreen.Payment             -> PaymentScreen(vm)
                            is BuyOnlineScreen.PaymentSuccess      -> PaymentSuccessScreen(vm)
                            is BuyOnlineScreen.KycMethod           -> KycMethodScreen(vm)
                            is BuyOnlineScreen.KycDetails          -> KycDetailsScreen(vm)
                            is BuyOnlineScreen.KycOtp              -> KycOtpScreen(vm)
                            is BuyOnlineScreen.BankDetails         -> BankDetailsScreen(vm)
                            is BuyOnlineScreen.KycSubmitted        -> KycSubmittedScreen(vm)
                            is BuyOnlineScreen.ApplicationComplete -> ApplicationCompleteScreen(vm)
                            is BuyOnlineScreen.Satisfaction        -> SatisfactionScreen(vm)
                        }
                    }
                }
                if (showHelpFab) {
                    FloatingHelpButton(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Inline EN / HI language switcher rendered in the buyonline top chrome.
 *
 * Unlike the monolith's version (which read/wrote `AegisSettingsStore`), this is
 * a controlled component: the host owns the persisted locale and is notified via
 * [onChange]. Two compact chips, right-aligned; the active chip is filled PruRed.
 */
@Composable
private fun LanguageSwitcher(active: AegisLocale, onChange: (AegisLocale) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        listOf(AegisLocale.EN to "EN", AegisLocale.HI to "हिं").forEach { (loc, label) ->
            val isActive = active == loc
            Box(
                Modifier
                    .clickable { onChange(loc) }
                    .background(
                        if (isActive) PruRed else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp, if (isActive) PruRed else PruSubtext,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    label,
                    color = if (isActive) Color.White else PruSubtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/**
 * Small inline "Welcome back" callout used when the customer arrived via a
 * `?session=` resume URL. Stateless — the caller auto-hides it after a few
 * seconds.
 */
@Composable
private fun WelcomeBackCallout(label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(PruBackground, shape)
                .border(1.dp, PruRed, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PruRed,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Welcome back",
                    color = PruRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "We picked up where you left off. Continue from $label.",
                    color = PruText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Friendly per-screen label for the "Welcome back" callout. Exhaustive `when`
 * over the sealed class so a new screen forces a compile-time decision here.
 */
private fun screenLabel(screen: BuyOnlineScreen): String = when (screen) {
    is BuyOnlineScreen.Landing             -> "the start"
    is BuyOnlineScreen.Otp                 -> "OTP verification"
    is BuyOnlineScreen.GetStarted          -> "Get started"
    is BuyOnlineScreen.PreExistingDisease  -> "Pre-existing conditions"
    is BuyOnlineScreen.CriticalIllness     -> "Critical illness"
    is BuyOnlineScreen.PlanLoading         -> "Loading plans"
    is BuyOnlineScreen.Eligibility         -> "Eligibility"
    is BuyOnlineScreen.Quote               -> "Quote"
    is BuyOnlineScreen.AddOns              -> "Add-ons"
    is BuyOnlineScreen.PlanSummary         -> "Plan summary"
    is BuyOnlineScreen.PersonalDetails     -> "Personal details"
    is BuyOnlineScreen.LifestyleQuestions  -> "Lifestyle questions"
    is BuyOnlineScreen.MedicalQuestions    -> "Medical questions"
    is BuyOnlineScreen.Payment             -> "Payment"
    is BuyOnlineScreen.PaymentSuccess      -> "Payment confirmation"
    is BuyOnlineScreen.KycMethod           -> "KYC method"
    is BuyOnlineScreen.KycDetails          -> "KYC details"
    is BuyOnlineScreen.KycOtp              -> "KYC OTP"
    is BuyOnlineScreen.BankDetails         -> "Bank details"
    is BuyOnlineScreen.KycSubmitted        -> "KYC submitted"
    is BuyOnlineScreen.ApplicationComplete -> "Application complete"
    is BuyOnlineScreen.Satisfaction        -> "Satisfaction"
}
