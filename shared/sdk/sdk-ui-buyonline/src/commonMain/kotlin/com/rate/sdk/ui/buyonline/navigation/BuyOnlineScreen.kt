package com.rate.sdk.ui.buyonline.navigation

/**
 * The 22 screens of the buy-online customer journey, as a closed sealed class.
 *
 * RELOCATED verbatim from the monolith's
 * `com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen` (only the
 * package changed). The [BuyOnlineViewModel] holds the active screen + a
 * back-stack; the [BuyOnlineApp] `when` renders the matching composable. Kept as
 * `data object`s (no payloads) so save+resume can round-trip a screen through a
 * stable `simpleName` string without losing arguments.
 */
sealed class BuyOnlineScreen {
    data object Landing            : BuyOnlineScreen()
    data object Otp                : BuyOnlineScreen()
    data object GetStarted         : BuyOnlineScreen()
    data object PreExistingDisease : BuyOnlineScreen()
    data object CriticalIllness    : BuyOnlineScreen()
    data object PlanLoading        : BuyOnlineScreen()
    data object Eligibility        : BuyOnlineScreen()
    data object Quote              : BuyOnlineScreen()
    data object AddOns             : BuyOnlineScreen()
    data object PlanSummary        : BuyOnlineScreen()
    data object PersonalDetails    : BuyOnlineScreen()
    data object LifestyleQuestions : BuyOnlineScreen()
    data object MedicalQuestions   : BuyOnlineScreen()
    data object Payment            : BuyOnlineScreen()
    data object PaymentSuccess     : BuyOnlineScreen()
    data object KycMethod          : BuyOnlineScreen()
    data object KycDetails         : BuyOnlineScreen()
    data object KycOtp             : BuyOnlineScreen()
    data object BankDetails        : BuyOnlineScreen()
    data object KycSubmitted       : BuyOnlineScreen()
    data object ApplicationComplete: BuyOnlineScreen()
    data object Satisfaction       : BuyOnlineScreen()
}
