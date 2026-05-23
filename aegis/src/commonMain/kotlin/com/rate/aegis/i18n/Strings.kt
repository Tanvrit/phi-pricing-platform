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

        // Quote
        "quote.title" to "Your personalised plan",
        "quote.cta" to "Continue",

        // Add-ons
        "addons.title" to "Add-ons",
        "addons.cta" to "Continue",

        // Plan summary
        "summary.title" to "Plan Summary",
        "summary.cta" to "Continue to payment",

        // Payment
        "payment.title" to "Plan Summary",
        "payment.subtitle" to "Review the summary before proceeding to payment.",
        "payment.total" to "Total payable",
        "payment.cta" to "Pay now",
        "payment.secured" to "Your payment is protected by 256-bit SSL encryption",

        // Application complete
        "complete.title" to "Application submitted",
        "complete.body" to "You'll receive your policy document via email shortly.",
    )

    private val hi: Map<String, String> = mapOf(
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

        // Quote
        "quote.title" to "आपकी अनुकूलित योजना",
        "quote.cta" to "जारी रखें",

        // Add-ons
        "addons.title" to "ऐड-ऑन्स",
        "addons.cta" to "जारी रखें",

        // Plan summary
        "summary.title" to "योजना सारांश",
        "summary.cta" to "भुगतान पर जाएं",

        // Payment
        "payment.title" to "योजना सारांश",
        "payment.subtitle" to "भुगतान करने से पहले सारांश की समीक्षा करें।",
        "payment.total" to "कुल देय",
        "payment.cta" to "अभी भुगतान करें",
        "payment.secured" to "आपका भुगतान 256-बिट SSL एन्क्रिप्शन द्वारा सुरक्षित है",

        // Application complete
        "complete.title" to "आवेदन सबमिट किया गया",
        "complete.body" to "आपको शीघ्र ही ईमेल के माध्यम से आपका पॉलिसी दस्तावेज़ प्राप्त होगा।",
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
