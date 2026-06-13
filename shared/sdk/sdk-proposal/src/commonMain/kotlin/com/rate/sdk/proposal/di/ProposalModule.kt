package com.rate.sdk.proposal.di

import com.rate.sdk.proposal.event.ProposalEventSink
import com.rate.sdk.proposal.handler.EligibilityHandler
import com.rate.sdk.proposal.handler.KycHandler
import com.rate.sdk.proposal.handler.OtpConfig
import com.rate.sdk.proposal.handler.OtpHandler
import com.rate.sdk.proposal.handler.PremiumHandler
import com.rate.sdk.proposal.handler.ProposalHandler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-proposal. Binds only this module's HANDLERS — the PORTs they depend on are
 * provided by the app layer, keeping the module pure-KMP and secret-free:
 *
 *  - core `OtpStore` + `TokenSigner` (the OTP store + signing SECRET live ONLY in the app —
 *    server-persistence binds the Mongo `otp_records` store and the JWT/HMAC signer);
 *  - this module's `ProposalRepository` / `SessionRepository` (Mongo actuals in server-persistence);
 *  - sdk-catalog's `AddOnRepository` (for the tier→plan + add-on→cover resolution);
 *  - sdk-quoting's `QuoteHandler` (priced via the injected `RatingPort`);
 *  - the optional [ProposalEventSink] (audit/bus); the `NOOP` default means tests carry zero wiring.
 *
 * Usage in the app:
 * `startKoin { modules(serverPersistenceModule, authModule, ratingModule, quotingModule(),
 *                      catalogModule, proposalModule()) }`.
 */
fun proposalModule(): Module = module {
    single {
        OtpHandler(
            store = get(),
            tokenSigner = get(),
            events = getOrNull() ?: ProposalEventSink.NOOP,
            config = getOrNull() ?: OtpConfig(),
        )
    }
    single { KycHandler(otpHandler = get(), events = getOrNull() ?: ProposalEventSink.NOOP) }
    single { EligibilityHandler(events = getOrNull() ?: ProposalEventSink.NOOP) }
    single {
        PremiumHandler(
            quotes = get(),
            addOns = get(),
            events = getOrNull() ?: ProposalEventSink.NOOP,
        )
    }
    single { ProposalHandler(repository = get(), events = getOrNull() ?: ProposalEventSink.NOOP) }
}
