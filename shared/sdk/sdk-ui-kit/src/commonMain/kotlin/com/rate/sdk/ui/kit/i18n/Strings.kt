package com.rate.sdk.ui.kit.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Supported customer-journey locales. A persisted locale setting stores the
 * [code] (e.g. "en", "hi"); the human-facing chip uses [displayName].
 *
 * Scope is intentionally narrow — this catalog covers the buyonline journey
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
 * Composition local that surfaces the active locale to every composable below an
 * [com.rate.sdk.ui.kit.theme.AegisTheme]. Defaults to [AegisLocale.EN] so any
 * composable mounted outside the provider (previews, tests) still renders.
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

        // Config hub (operator) — titles read from i18n where practical.
        "confighub.empty" to "Nothing configured in this hub yet.",
        "confighub.glossary.title" to "Glossary",
        "confighub.glossary.hint" to "Hover the (i) icons to read definitions of insurance and actuarial terms used across this hub.",

        // Glossary — insurance & actuarial jargon. Keys are `glossary.<term>`; the
        // value is a one-line plain-English definition shown inside an info tooltip.
        "glossary.eva" to "Economic Value Added — profit a policy or portfolio generates after subtracting the cost of the capital it ties up. Positive EVA means the business earns more than its capital costs.",
        "glossary.rba" to "Risk-Based Approach — sizing scrutiny, capital and pricing to each account's measured risk rather than applying one flat rule to everyone.",
        "glossary.ibnr" to "Incurred But Not Reported — a reserve set aside for claims that have already happened but the insurer has not yet been told about, so the books reflect the true liability.",
        "glossary.ufactor" to "U-factor (Utilisation factor) — how heavily a group actually uses its cover (claims per member). A high U-factor pushes the renewal premium up.",
        "glossary.obligatoryri" to "Obligatory Reinsurance — the share of every policy an insurer must, by regulation, cede to the national reinsurer before placing the rest in the market.",
        "glossary.pec" to "Pre-Existing Condition — an ailment a member already had before the policy started; it is typically subject to a waiting period before it is covered.",
        "glossary.lossratio" to "Loss Ratio — claims paid divided by premium earned. Below 100% the book is underwriting-profitable; above it, claims are outrunning premium.",
        "glossary.credibility" to "Credibility Factor — how much weight a group's own claims history gets versus the wider market table. Larger, longer-running groups earn higher credibility.",
        "glossary.mtf" to "Medical Trend Factor — the annual inflation in healthcare costs (treatment prices, utilisation, new procedures) baked into next year's premium.",
        "glossary.copayment" to "Co-payment — the fixed percentage of each claim the member pays themselves; the insurer covers the rest. A higher co-pay lowers the premium.",
        "glossary.sublimit" to "Sub-limit — a cap on a specific benefit (e.g. room rent or a named procedure) that sits inside the overall sum insured.",
        "glossary.ncb" to "No-Claim Bonus — an increase in sum insured (or a premium discount) granted for each claim-free year, rewarding low-utilisation members.",
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

        // Config hub (operator)
        "confighub.empty" to "इस हब में अभी कुछ भी कॉन्फ़िगर नहीं किया गया है।",
        "confighub.glossary.title" to "शब्दावली",
        "confighub.glossary.hint" to "इस हब में उपयोग किए गए बीमा और बीमांकिक शब्दों की परिभाषाएँ पढ़ने के लिए (i) आइकन पर होवर करें।",

        // Glossary — बीमा और बीमांकिक शब्दावली
        "glossary.eva" to "इकोनॉमिक वैल्यू एडेड — किसी पॉलिसी या पोर्टफोलियो द्वारा उपयोग की गई पूँजी की लागत घटाने के बाद अर्जित लाभ। धनात्मक EVA का अर्थ है कि व्यवसाय अपनी पूँजी लागत से अधिक कमा रहा है।",
        "glossary.rba" to "जोखिम-आधारित दृष्टिकोण — सभी पर एक समान नियम लागू करने के बजाय प्रत्येक खाते के मापे गए जोखिम के अनुसार जाँच, पूँजी और मूल्य निर्धारण तय करना।",
        "glossary.ibnr" to "हुए लेकिन सूचित नहीं — ऐसे दावों के लिए रखी गई आरक्षित राशि जो घटित हो चुके हैं पर बीमाकर्ता को अभी तक नहीं बताए गए, ताकि बहीखाते वास्तविक देयता दर्शाएँ।",
        "glossary.ufactor" to "यू-फैक्टर (उपयोग कारक) — कोई समूह अपने कवर का वास्तव में कितना उपयोग करता है (प्रति सदस्य दावे)। उच्च यू-फैक्टर नवीनीकरण प्रीमियम बढ़ाता है।",
        "glossary.obligatoryri" to "अनिवार्य पुनर्बीमा — हर पॉलिसी का वह हिस्सा जिसे बीमाकर्ता को नियमानुसार, शेष को बाज़ार में देने से पहले, राष्ट्रीय पुनर्बीमाकर्ता को सौंपना अनिवार्य है।",
        "glossary.pec" to "पूर्व-मौजूदा स्थिति — कोई बीमारी जो सदस्य को पॉलिसी शुरू होने से पहले से थी; इसे कवर होने से पहले आमतौर पर प्रतीक्षा अवधि लागू होती है।",
        "glossary.lossratio" to "लॉस रेशियो — चुकाए गए दावे बँटे अर्जित प्रीमियम। 100% से कम होने पर बही लाभदायक है; अधिक होने पर दावे प्रीमियम से आगे निकल रहे हैं।",
        "glossary.credibility" to "क्रेडिबिलिटी फैक्टर — किसी समूह के अपने दावा इतिहास को व्यापक बाज़ार तालिका की तुलना में कितना महत्व दिया जाए। बड़े और पुराने समूहों को अधिक विश्वसनीयता मिलती है।",
        "glossary.mtf" to "मेडिकल ट्रेंड फैक्टर — स्वास्थ्य सेवा लागत में वार्षिक वृद्धि (उपचार मूल्य, उपयोग, नई प्रक्रियाएँ) जिसे अगले वर्ष के प्रीमियम में शामिल किया जाता है।",
        "glossary.copayment" to "सह-भुगतान — प्रत्येक दावे का निश्चित प्रतिशत जो सदस्य स्वयं चुकाता है; शेष बीमाकर्ता वहन करता है। अधिक सह-भुगतान प्रीमियम घटाता है।",
        "glossary.sublimit" to "उप-सीमा — किसी विशिष्ट लाभ (जैसे कमरे का किराया या नामित प्रक्रिया) पर एक सीमा जो कुल बीमा राशि के भीतर होती है।",
        "glossary.ncb" to "नो-क्लेम बोनस — प्रत्येक दावा-मुक्त वर्ष के लिए बीमा राशि में वृद्धि (या प्रीमियम छूट), जो कम उपयोग वाले सदस्यों को पुरस्कृत करती है।",
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
 * (or simply set [com.rate.sdk.ui.kit.theme.AegisTheme]'s `locale`) to switch
 * languages.
 */
@Composable
fun t(key: String): String = Strings[key, LocalAegisLocale.current]

/**
 * The set of jargon terms that have a glossary definition, keyed by a short,
 * stable token. Use [GlossaryTerm.key] with [glossaryDefinition] /
 * [com.rate.sdk.ui.kit.i18n.glossaryDef] to fetch the localised definition, and
 * [GlossaryTerm.label] as the human-facing term shown in a tooltip header.
 *
 * Centralising the term list here (rather than scattering raw `"glossary.xyz"`
 * strings across surfaces) keeps the glossary auditable and lets a single
 * "Glossary" affordance enumerate every term.
 */
enum class GlossaryTerm(val key: String, val label: String) {
    EVA("glossary.eva", "EVA"),
    RBA("glossary.rba", "RBA"),
    IBNR("glossary.ibnr", "IBNR"),
    U_FACTOR("glossary.ufactor", "U-factor"),
    OBLIGATORY_RI("glossary.obligatoryri", "Obligatory RI"),
    PEC("glossary.pec", "PEC"),
    LOSS_RATIO("glossary.lossratio", "Loss Ratio"),
    CREDIBILITY("glossary.credibility", "Credibility Factor"),
    MTF("glossary.mtf", "Medical Trend Factor"),
    CO_PAYMENT("glossary.copayment", "Co-payment"),
    SUB_LIMIT("glossary.sublimit", "Sub-limit"),
    NCB("glossary.ncb", "No-Claim Bonus"),
}

/**
 * Fetch a glossary definition by its `glossary.<term>` [key] for [locale]. Falls
 * back the same way [Strings.get] does (HI → EN → key), so an unknown key returns
 * the key itself instead of crashing.
 */
fun glossaryDefinition(key: String, locale: AegisLocale = AegisLocale.EN): String =
    Strings[key, locale]

/** [glossaryDefinition] overload keyed by the typed [GlossaryTerm]. */
fun glossaryDefinition(term: GlossaryTerm, locale: AegisLocale = AegisLocale.EN): String =
    Strings[term.key, locale]

/**
 * Composable shortcut for a glossary definition that reads the active locale from
 * the composition local, mirroring [t]. Pair it with an [AegisInfoTooltip].
 */
@Composable
fun glossaryDef(term: GlossaryTerm): String =
    Strings[term.key, LocalAegisLocale.current]
