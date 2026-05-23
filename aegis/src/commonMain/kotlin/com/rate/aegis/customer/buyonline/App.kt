package com.rate.aegis.customer.buyonline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.AegisLaunchContext
import com.rate.aegis.customer.buyonline.api.BuyOnlineApiClient
import com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen
import com.rate.aegis.customer.buyonline.ui.addons.AddOnsScreen
import com.rate.aegis.customer.buyonline.ui.complete.ApplicationCompleteScreen
import com.rate.aegis.customer.buyonline.ui.complete.SatisfactionScreen
import com.rate.aegis.customer.buyonline.ui.components.ResumeBanner
import com.rate.aegis.customer.buyonline.ui.components.SessionExpiresCallout
import com.rate.aegis.customer.buyonline.ui.components.StepIndicator
import com.rate.aegis.customer.buyonline.ui.details.PersonalDetailsScreen
import com.rate.aegis.customer.buyonline.ui.getstarted.GetStartedScreen
import com.rate.aegis.customer.buyonline.ui.health.CriticalIllnessScreen
import com.rate.aegis.customer.buyonline.ui.health.PreExistingDiseaseScreen
import com.rate.aegis.customer.buyonline.ui.kyc.BankDetailsScreen
import com.rate.aegis.customer.buyonline.ui.kyc.KycDetailsScreen
import com.rate.aegis.customer.buyonline.ui.kyc.KycMethodScreen
import com.rate.aegis.customer.buyonline.ui.kyc.KycOtpScreen
import com.rate.aegis.customer.buyonline.ui.kyc.KycSubmittedScreen
import com.rate.aegis.customer.buyonline.ui.landing.LandingScreen
import com.rate.aegis.customer.buyonline.ui.loading.EligibilityScreen
import com.rate.aegis.customer.buyonline.ui.loading.PlanLoadingScreen
import com.rate.aegis.customer.buyonline.ui.otp.OtpScreen
import com.rate.aegis.customer.buyonline.ui.payment.PaymentScreen
import com.rate.aegis.customer.buyonline.ui.payment.PaymentSuccessScreen
import com.rate.aegis.customer.buyonline.ui.questionnaire.LifestyleQuestionsScreen
import com.rate.aegis.customer.buyonline.ui.questionnaire.MedicalQuestionsScreen
import com.rate.aegis.customer.buyonline.ui.quote.QuoteScreen
import com.rate.aegis.customer.buyonline.ui.summary.PlanSummaryScreen
import com.rate.aegis.customer.buyonline.ui.theme.PRUHealthTheme
import com.rate.aegis.customer.buyonline.ui.theme.PruBackground
import com.rate.aegis.customer.buyonline.ui.theme.PruRed
import com.rate.aegis.customer.buyonline.ui.theme.PruText
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel
import kotlinx.coroutines.delay

@Composable
fun BuyOnlineApp() {
    // Shared-quote short-circuit. If the page was opened with `?quote=<id>` the
    // operator handed the customer a saved-calculation link — render a
    // read-only summary instead of the 22-screen journey. The customer can
    // dismiss the overlay via the "Continue to apply" CTA, which flips this
    // local flag and falls through to the normal journey body (rather than
    // forcing a full page reload that would lose the rest of the app state).
    val sharedQuoteId = remember { AegisLaunchContext.quoteId }
    var showSharedQuote by remember { mutableStateOf(sharedQuoteId != null) }
    // VM is built up-front (rather than after the shared-quote short-circuit)
    // so the SharedQuoteView "Continue to apply" CTA has a target to seed.
    // It stays parked on Landing until the customer either dismisses the
    // shared-quote view normally or taps Continue, which seeds it onto Quote.
    val client = remember { BuyOnlineApiClient() }
    val vm     = remember { BuyOnlineViewModel(client) }
    if (showSharedQuote && sharedQuoteId != null) {
        PRUHealthTheme {
            SharedQuoteView(
                quoteId = sharedQuoteId,
                onContinue = {
                    // Clear the launch context so a subsequent re-composition
                    // (e.g. browser navigation) doesn't re-trigger the view,
                    // and flip the local flag to drop into the journey.
                    AegisLaunchContext.quoteId = null
                    // Strip `?quote=<id>` from the browser address bar so a
                    // page refresh lands the customer in the journey rather
                    // than bouncing them back to the read-only summary. WASM
                    // rewrites history; JVM is a no-op (no URL bar).
                    clearQuoteParam()
                    showSharedQuote = false
                },
                onContinueWithQuote = { detail ->
                    // Pre-fill the journey from the shared quote and jump
                    // straight to the Plan (Quote) screen — the customer has
                    // already given their inputs to the advisor, no need to
                    // re-collect them. They still confirm on Quote before
                    // proceeding to add-ons / summary / KYC.
                    vm.seedFromSharedQuote(detail)
                }
            )
        }
        return
    }

    // Save+resume bootstrap. Reads the one-shot `session=` id stashed by the
    // platform `main` (wasmJs URL query / jvm -D property). Null = brand-new
    // customer; the VM mints a fresh hex id and stays quiet until they interact.
    LaunchedEffect(Unit) { vm.loadOrCreateSession(AegisLaunchContext.sessionId) }

    // Session-age derived value, used to decide whether to surface the
    // "expires soon" banner below. The server-side save+resume row has a
    // documented 30-day TTL (not yet enforced — that's a Phase-2 cleanup
    // task) so we warn the customer once they cross 25 days. Keyed on the
    // VM's sessionId so a restored session re-evaluates after rehydrate.
    //
    // Source of `updatedAtIso`: vm.snapshot() — the VM doesn't expose the
    // loaded session's original timestamp directly, and snapshot() is the
    // canonical place that owns the field. Note that snapshot() stamps
    // `Clock.System.now()` each call, so this derived value will read 0
    // for fresh sessions and only flips positive once a real restore path
    // surfaces an older timestamp into the snapshot (e.g. if the snapshot
    // contract changes to preserve loaded `updatedAtIso`). Today this
    // means the banner only fires for sessions that genuinely return a
    // stale `updatedAtIso` from snapshot(); harmless on brand-new ones.
    val ageDays = remember(vm.sessionId) {
        val saved = vm.snapshot().updatedAtIso  // ISO instant
        val updated = runCatching { kotlinx.datetime.Instant.parse(saved) }.getOrNull()
        if (updated == null) null
        else (kotlinx.datetime.Clock.System.now() - updated).inWholeDays
    }

    // "Welcome back" affordance — only meaningful when the customer actually
    // arrived via a `?session=<id>` link (i.e. AegisLaunchContext.sessionId
    // was populated before composition). A brand-new session — where the VM
    // mints its own id — leaves AegisLaunchContext.sessionId blank, so we
    // skip the banner. Snapshot the launch value into `remember` so a later
    // mutation of the global (e.g. `quoteId` clearing pattern) doesn't
    // retroactively change our verdict.
    val resumed = remember { !AegisLaunchContext.sessionId.isNullOrBlank() }
    var welcomeBackShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (resumed) {
            // Give the VM a moment to actually restore the state before
            // showing the banner — otherwise we'd announce "picked up where
            // you left off" while still parked on Landing.
            delay(800)
            welcomeBackShown = true
        }
    }
    // Auto-hide the banner after 6 seconds so it doesn't clutter the form.
    LaunchedEffect(welcomeBackShown) {
        if (welcomeBackShown) {
            delay(6_000)
            welcomeBackShown = false
        }
    }

    // Browser back-button guard. Once the customer has a session AND is past
    // the marketing Landing but not yet on a terminal screen, we hook
    // `window.onbeforeunload` so the browser shows its native "Leave site?"
    // prompt. JVM is a no-op (no browser back button). The 1.5s-debounced
    // server save catches anything that *did* land before they confirmed
    // leaving; this dialog just prevents accidental ejection mid-form.
    val shouldGuard = vm.sessionId.isNotBlank() &&
            vm.currentScreen !is BuyOnlineScreen.Landing &&
            vm.currentScreen !is BuyOnlineScreen.ApplicationComplete &&
            vm.currentScreen !is BuyOnlineScreen.Satisfaction
    DisposableEffect(shouldGuard) {
        setBeforeLeaveHandler(shouldGuard)
        onDispose { setBeforeLeaveHandler(false) }
    }

    PRUHealthTheme {
        // The ResumeBanner is suppressed on Landing (no session yet to brag
        // about) and on the terminal screens (the journey is done — pushing
        // the resume URL would be confusing). Everything else gets the slim
        // banner above the active screen body.
        val showBanner = vm.sessionId.isNotBlank() && when (vm.currentScreen) {
            is BuyOnlineScreen.Landing,
            is BuyOnlineScreen.ApplicationComplete,
            is BuyOnlineScreen.Satisfaction -> false
            else -> true
        }
        // Step indicator follows the same suppression rules: skipped on the
        // marketing Landing page (no journey yet) and on the two terminal
        // screens (ApplicationComplete + Satisfaction — the journey is done).
        // Sits *below* the ResumeBanner so the slim banner stays the very top
        // surface and the indicator anchors the journey content underneath.
        val showSteps = when (vm.currentScreen) {
            is BuyOnlineScreen.Landing,
            is BuyOnlineScreen.ApplicationComplete,
            is BuyOnlineScreen.Satisfaction -> false
            else -> true
        }

        // Only show the welcome-back callout once we're off Landing — the
        // banner only makes sense when actual restored state is on display.
        // (The 800ms delay above usually means the VM has already navigated
        // past Landing by the time we flip the flag, but on a slow restore
        // we suppress here as a safety net.)
        val showWelcomeBack = welcomeBackShown &&
                vm.currentScreen !is BuyOnlineScreen.Landing

        // Expires-soon callout — only renders once a saved session crosses
        // the 25-day mark. Sits above the ResumeBanner so it's the very
        // first surface the customer sees on re-entry; the WelcomeBackCallout
        // and StepIndicator follow underneath.
        val showExpires = ageDays != null && ageDays >= 25 &&
                vm.currentScreen !is BuyOnlineScreen.ApplicationComplete &&
                vm.currentScreen !is BuyOnlineScreen.Satisfaction

        Column(Modifier.fillMaxSize()) {
            if (showExpires && ageDays != null) {
                val daysLeft = (30 - ageDays).coerceAtLeast(0)
                SessionExpiresCallout(ageDays = ageDays, daysLeft = daysLeft)
            }
            if (showBanner) {
                ResumeBanner(sessionId = vm.sessionId)
            }
            if (showWelcomeBack) {
                WelcomeBackCallout(label = screenLabel(vm.currentScreen))
            }
            if (showSteps) {
                StepIndicator(currentScreen = vm.currentScreen)
            }
            // The screen body takes the remaining vertical space. We rely on
            // each screen managing its own internal scroll — wrapping in
            // another scroll container here would double-scroll.
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
    }
}

/**
 * Small inline "Welcome back" callout used when the customer arrived via a
 * `?session=` resume URL. Visually we use the PRUHealth chrome (PruRed accent
 * on the existing PruBackground card surface) rather than the Aegis callout
 * chrome so the banner reads as part of the customer journey rather than the
 * operator console.
 *
 * Sits between [ResumeBanner] and [StepIndicator] in the buyonline shell. The
 * caller is responsible for auto-hiding after a few seconds — this composable
 * is intentionally stateless.
 */
@Composable
private fun WelcomeBackCallout(label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(PruBackground, shape)
                .border(1.dp, PruRed, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PruRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Welcome back",
                    color = PruRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "We picked up where you left off. Continue from $label.",
                    color = PruText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Hand-written label mapping for [BuyOnlineScreen] used by the "Welcome back"
 * callout. The StepIndicator's `stageFor` collapses 22 screens into 5 phases,
 * which is too coarse here — the customer wants to see the specific screen
 * they're resuming on, not "Plan" for everything from PlanLoading through
 * PlanSummary. Exhaustive `when` on the sealed class so the compiler flags
 * any future screens that need a friendly label.
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
