package com.rate.aegis.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Supported customer-journey locales. The persisted [AegisSettings.locale] stores
 * the [code] (e.g. "en", "hi"); the human-facing chip uses [displayName].
 *
 * Scope is intentionally narrow — this scaffolding covers the buyonline journey
 * only. Operator surfaces (Home / Quotes / Plans / …) stay English-only in
 * Phase 1; their localization budget is Phase 2.
 */
enum class AegisLocale(val code: String, val displayName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी");

    companion object {
        /** Parse a persisted code back into an enum, falling back to [EN]. */
        fun fromCode(code: String?): AegisLocale {
            if (code == null) return EN
            val normalised = code.lowercase()
            return entries.firstOrNull { it.code == normalised } ?: EN
        }
    }
}

/**
 * Composition local that surfaces the active locale to every composable below
 * [com.rate.aegis.AegisRoot]. Defaults to [AegisLocale.EN] so any composable
 * that's mounted outside the provider (previews, tests) still renders.
 */
val LocalAegisLocale = compositionLocalOf { AegisLocale.EN }

/**
 * Lookup table of customer-journey strings.
 *
 * Both maps share the same set of keys; the Hindi side may be partial — any
 * missing key falls through to English. Keys are dotted (`screen.role`) so they
 * sort sensibly in the source file and so grep across the codebase reads
 * naturally (`t("landing.cta")`).
 *
 * NOT a general-purpose i18n framework — there is no pluralisation, no number
 * formatting, no message-arg substitution. When we hit a string that needs
 * interpolation we'll add it then, not now.
 */
object Strings {
    private val en: Map<String, String> = mapOf(
        // Common
        "common.proceed" to "Proceed",

        // Landing
        "landing.hero.title" to "India's health insurance that puts your family first",
        "landing.hero.subtitle" to "Find the right plan in a few seconds",
        "landing.form.who" to "Who are you insuring?",
        "landing.form.member.self" to "Self",
        "landing.form.member.spouse" to "Spouse",
        "landing.form.member.kids" to "Kids",
        "landing.form.age" to "Age of eldest member",
        "landing.form.mobile" to "Mobile number",
        "landing.cta" to "Get the best offer",

        // OTP
        "otp.title" to "Verifying that it's you!",
        "otp.resend" to "Resend OTP",
        "otp.cta" to "Verify",

        // Get started (pincode)
        "getstarted.title" to "Let's get started...",
        "getstarted.pincode" to "Pincode",
        "getstarted.cta" to "Proceed",

        // Pre-existing disease
        "ped.title" to "Does any member(s) have any pre-existing disease? E.g., Diabetes, High cholesterol, Hypertension etc?",
        "ped.select.members" to "Please select applicable members:",
        "ped.cta" to "Proceed",

        // Critical illness
        "critical.title" to "Have any member(s) been treated for critical conditions?",
        "critical.view.conditions" to "View 13 covered conditions →",
        "critical.select.members" to "Please select applicable members:",
        "critical.cta" to "Proceed",

        // Plan loading
        "loading.title" to "Finding the most suitable plans..",
        "loading.subtitle" to "Analysing age and members",

        // Eligibility
        "eligibility.title" to "Great news!",
        "eligibility.subtitle" to "We can provide health coverage for the following members",
        "eligibility.cta" to "Check out the plans",

        // Quote
        "quote.title" to "Your personalised plan",
        "quote.cta" to "Continue",
        "quote.suminsured" to "Sum insured",
        "quote.tenure" to "Tenure",

        // Add-ons
        "addons.title" to "Any add-ons you'd like to include?",
        "addons.cta" to "Continue",
        "addons.skip" to "Skip add-ons",

        // Plan summary
        "summary.title" to "Here is your plan summary",
        "summary.cta" to "Proceed with payment",

        // Payment
        "payment.title" to "Plan Summary",
        "payment.subtitle" to "Review the summary before proceeding to payment.",
        "payment.total" to "Total payable",
        "payment.cta" to "Pay now",
        "payment.secured" to "Your payment is protected by 256-bit SSL encryption",

        // Payment success
        "paymentsuccess.title" to "Payment successful!",
        "paymentsuccess.subtitle" to "Please complete your KYC for policy approval",
        "paymentsuccess.cta.kyc" to "Complete KYC",
        "paymentsuccess.cta.health" to "Health questions",

        // Personal details
        "personal.helper" to "Please enter information as it appears on Aadhaar / PAN",
        "personal.cta.next" to "Next member →",
        "personal.cta.proceed" to "Proceed",

        // Lifestyle questions
        "lifestyle.title" to "Lifestyle questions",
        "lifestyle.cta" to "Proceed",

        // Medical questions
        "medical.title" to "Medical questions",
        "medical.subtitle" to "Have any of the family members been hospitalised or had any medical condition?",
        "medical.cta" to "Proceed",

        // KYC method
        "kyc.method.title" to "Choose your method of verification",
        "kyc.method.cta" to "Proceed",

        // KYC details
        "kyc.details.cta" to "Proceed",

        // KYC OTP
        "kyc.otp.title" to "Enter OTP",
        "kyc.otp.cta" to "Proceed",
        "kyc.otp.resend" to "Resend OTP",

        // Bank details
        "bank.title" to "Enter bank details",
        "bank.subtitle" to "Bank details are required for policy issuance and premium refunds.",
        "bank.cta" to "Verify",

        // KYC submitted
        "kyc.submitted.title" to "KYC Submitted",
        "kyc.submitted.body" to "To issue your policy, we need your bank details and insured member information. We will be in touch shortly.",
        "kyc.submitted.cta" to "Proceed",

        // Application complete
        "complete.title" to "Application complete!",
        "complete.body" to "Your proposal is in review. We will get in touch with you shortly.",
        "complete.cta" to "Track proposal",
        "complete.back" to "Back to home",

        // Satisfaction
        "satisfaction.title" to "How satisfied are you with this journey?",
        "satisfaction.subtitle" to "Your feedback helps us improve the experience for everyone.",
        "satisfaction.cta" to "Submit",
        "satisfaction.skip" to "Skip",
    )

    private val hi: Map<String, String> = mapOf(
        // Common
        "common.proceed" to "आगे बढ़ें",

        // Landing
        "landing.hero.title" to "भारत का स्वास्थ्य बीमा जो आपके परिवार को पहले रखता है",
        "landing.hero.subtitle" to "कुछ ही सेकंड में सही योजना खोजें",
        "landing.form.who" to "आप किसका बीमा कर रहे हैं?",
        "landing.form.member.self" to "स्वयं",
        "landing.form.member.spouse" to "जीवनसाथी",
        "landing.form.member.kids" to "बच्चे",
        "landing.form.age" to "सबसे बड़े सदस्य की आयु",
        "landing.form.mobile" to "मोबाइल नंबर",
        "landing.cta" to "सर्वोत्तम ऑफर प्राप्त करें",

        // OTP
        "otp.title" to "यह सत्यापित कर रहे हैं कि यह आप ही हैं!",
        "otp.resend" to "OTP पुनः भेजें",
        "otp.cta" to "सत्यापित करें",

        // Get started (pincode)
        "getstarted.title" to "चलिए शुरू करते हैं...",
        "getstarted.pincode" to "पिनकोड",
        "getstarted.cta" to "आगे बढ़ें",

        // Pre-existing disease
        "ped.title" to "क्या किसी सदस्य को कोई पूर्व-मौजूदा बीमारी है? जैसे मधुमेह, उच्च कोलेस्ट्रॉल, उच्च रक्तचाप आदि?",
        "ped.select.members" to "कृपया लागू सदस्यों का चयन करें:",
        "ped.cta" to "आगे बढ़ें",

        // Critical illness
        "critical.title" to "क्या किसी सदस्य का गंभीर बीमारी के लिए इलाज किया गया है?",
        "critical.view.conditions" to "13 कवर की गई स्थितियाँ देखें →",
        "critical.select.members" to "कृपया लागू सदस्यों का चयन करें:",
        "critical.cta" to "आगे बढ़ें",

        // Plan loading
        "loading.title" to "सबसे उपयुक्त योजनाएँ खोज रहे हैं..",
        "loading.subtitle" to "आयु और सदस्यों का विश्लेषण किया जा रहा है",

        // Eligibility
        "eligibility.title" to "खुशखबरी!",
        "eligibility.subtitle" to "हम निम्नलिखित सदस्यों को स्वास्थ्य कवरेज प्रदान कर सकते हैं",
        "eligibility.cta" to "योजनाएँ देखें",

        // Quote
        "quote.title" to "आपकी अनुकूलित योजना",
        "quote.cta" to "जारी रखें",
        "quote.suminsured" to "बीमा राशि",
        "quote.tenure" to "अवधि",

        // Add-ons
        "addons.title" to "क्या आप कोई ऐड-ऑन शामिल करना चाहेंगे?",
        "addons.cta" to "जारी रखें",
        "addons.skip" to "ऐड-ऑन छोड़ें",

        // Plan summary
        "summary.title" to "यह आपकी योजना का सारांश है",
        "summary.cta" to "भुगतान के साथ आगे बढ़ें",

        // Payment
        "payment.title" to "योजना सारांश",
        "payment.subtitle" to "भुगतान करने से पहले सारांश की समीक्षा करें।",
        "payment.total" to "कुल देय",
        "payment.cta" to "अभी भुगतान करें",
        "payment.secured" to "आपका भुगतान 256-बिट SSL एन्क्रिप्शन द्वारा सुरक्षित है",

        // Payment success
        "paymentsuccess.title" to "भुगतान सफल!",
        "paymentsuccess.subtitle" to "पॉलिसी अनुमोदन के लिए कृपया अपना केवाईसी पूरा करें",
        "paymentsuccess.cta.kyc" to "केवाईसी पूरा करें",
        "paymentsuccess.cta.health" to "स्वास्थ्य प्रश्न",

        // Personal details
        "personal.helper" to "कृपया जानकारी वैसी ही दर्ज करें जैसी आधार / पैन पर है",
        "personal.cta.next" to "अगला सदस्य →",
        "personal.cta.proceed" to "आगे बढ़ें",

        // Lifestyle questions
        "lifestyle.title" to "जीवनशैली प्रश्न",
        "lifestyle.cta" to "आगे बढ़ें",

        // Medical questions
        "medical.title" to "चिकित्सा प्रश्न",
        "medical.subtitle" to "क्या परिवार के किसी सदस्य को अस्पताल में भर्ती किया गया है या कोई चिकित्सीय स्थिति थी?",
        "medical.cta" to "आगे बढ़ें",

        // KYC method
        "kyc.method.title" to "सत्यापन की अपनी विधि चुनें",
        "kyc.method.cta" to "आगे बढ़ें",

        // KYC details
        "kyc.details.cta" to "आगे बढ़ें",

        // KYC OTP
        "kyc.otp.title" to "OTP दर्ज करें",
        "kyc.otp.cta" to "आगे बढ़ें",
        "kyc.otp.resend" to "OTP पुनः भेजें",

        // Bank details
        "bank.title" to "बैंक विवरण दर्ज करें",
        "bank.subtitle" to "पॉलिसी जारी करने और प्रीमियम वापसी के लिए बैंक विवरण आवश्यक हैं।",
        "bank.cta" to "सत्यापित करें",

        // KYC submitted
        "kyc.submitted.title" to "केवाईसी सबमिट किया गया",
        "kyc.submitted.body" to "आपकी पॉलिसी जारी करने के लिए, हमें आपके बैंक विवरण और बीमित सदस्य की जानकारी चाहिए। हम जल्द ही संपर्क करेंगे।",
        "kyc.submitted.cta" to "आगे बढ़ें",

        // Application complete
        "complete.title" to "आवेदन पूर्ण!",
        "complete.body" to "आपका प्रस्ताव समीक्षाधीन है। हम जल्द ही आपसे संपर्क करेंगे।",
        "complete.cta" to "प्रस्ताव ट्रैक करें",
        "complete.back" to "होम पर वापस जाएं",

        // Satisfaction
        "satisfaction.title" to "इस यात्रा से आप कितने संतुष्ट हैं?",
        "satisfaction.subtitle" to "आपकी प्रतिक्रिया हमें सभी के लिए अनुभव सुधारने में मदद करती है।",
        "satisfaction.cta" to "सबमिट करें",
        "satisfaction.skip" to "छोड़ें",
    )

    /**
     * Resolve [key] for [locale]. Hindi misses fall back to English; English
     * misses fall back to the key itself so a missing string is visible in dev
     * without crashing the screen.
     */
    operator fun get(key: String, locale: AegisLocale = AegisLocale.EN): String =
        when (locale) {
            AegisLocale.EN -> en[key] ?: key
            AegisLocale.HI -> hi[key] ?: en[key] ?: key
        }
}

/**
 * Composable shortcut so screens can write `t("landing.cta")` rather than
 * `Strings["landing.cta", LocalAegisLocale.current]`. Reads the locale from the
 * composition local — wrap content in `CompositionLocalProvider(LocalAegisLocale provides …)`
 * to switch languages.
 */
@Composable
fun t(key: String): String = Strings[key, LocalAegisLocale.current]
