package com.rate.aegis.customer.buyonline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rate.aegis.AegisLaunchContext
import com.rate.aegis.customer.buyonline.api.BuyOnlineApiClient
import com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen
import com.rate.aegis.customer.buyonline.ui.addons.AddOnsScreen
import com.rate.aegis.customer.buyonline.ui.complete.ApplicationCompleteScreen
import com.rate.aegis.customer.buyonline.ui.complete.SatisfactionScreen
import com.rate.aegis.customer.buyonline.ui.components.ResumeBanner
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
import com.rate.aegis.customer.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun BuyOnlineApp() {
    val client = remember { BuyOnlineApiClient() }
    val vm     = remember { BuyOnlineViewModel(client) }

    // Save+resume bootstrap. Reads the one-shot `session=` id stashed by the
    // platform `main` (wasmJs URL query / jvm -D property). Null = brand-new
    // customer; the VM mints a fresh hex id and stays quiet until they interact.
    LaunchedEffect(Unit) { vm.loadOrCreateSession(AegisLaunchContext.sessionId) }

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

        Column(Modifier.fillMaxSize()) {
            if (showBanner) {
                ResumeBanner(sessionId = vm.sessionId)
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
