package com.rate.buyonline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rate.buyonline.api.BuyOnlineApiClient
import com.rate.buyonline.navigation.BuyOnlineScreen
import com.rate.buyonline.ui.addons.AddOnsScreen
import com.rate.buyonline.ui.complete.ApplicationCompleteScreen
import com.rate.buyonline.ui.complete.SatisfactionScreen
import com.rate.buyonline.ui.details.PersonalDetailsScreen
import com.rate.buyonline.ui.getstarted.GetStartedScreen
import com.rate.buyonline.ui.health.CriticalIllnessScreen
import com.rate.buyonline.ui.health.PreExistingDiseaseScreen
import com.rate.buyonline.ui.kyc.BankDetailsScreen
import com.rate.buyonline.ui.kyc.KycDetailsScreen
import com.rate.buyonline.ui.kyc.KycMethodScreen
import com.rate.buyonline.ui.kyc.KycOtpScreen
import com.rate.buyonline.ui.kyc.KycSubmittedScreen
import com.rate.buyonline.ui.landing.LandingScreen
import com.rate.buyonline.ui.loading.EligibilityScreen
import com.rate.buyonline.ui.loading.PlanLoadingScreen
import com.rate.buyonline.ui.otp.OtpScreen
import com.rate.buyonline.ui.payment.PaymentScreen
import com.rate.buyonline.ui.payment.PaymentSuccessScreen
import com.rate.buyonline.ui.questionnaire.LifestyleQuestionsScreen
import com.rate.buyonline.ui.questionnaire.MedicalQuestionsScreen
import com.rate.buyonline.ui.quote.QuoteScreen
import com.rate.buyonline.ui.summary.PlanSummaryScreen
import com.rate.buyonline.ui.theme.PRUHealthTheme
import com.rate.buyonline.viewmodel.BuyOnlineViewModel

@Composable
fun BuyOnlineApp() {
    val client = remember { BuyOnlineApiClient() }
    val vm     = remember { BuyOnlineViewModel(client) }

    PRUHealthTheme {
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
