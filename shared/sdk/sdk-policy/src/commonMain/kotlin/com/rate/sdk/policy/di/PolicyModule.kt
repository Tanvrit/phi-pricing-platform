package com.rate.sdk.policy.di

import com.rate.sdk.policy.handler.EndorsementEngine
import com.rate.sdk.policy.handler.RenewalEngine
import org.koin.dsl.module

/**
 * Koin wiring for sdk-policy. Binds only this module's ENGINES that need injected collaborators
 * — [RenewalEngine] (over the core `RenewalRateProvider` + `PlanRepository` PORTs) and
 * [EndorsementEngine] (over `RenewalRateProvider`). The PORTs themselves, the
 * `PolicyRepository`/`ClaimRepository`, and the `PolicyEventSink` are provided by the app layer
 * (server-persistence binds the Mongo actuals), so this module stays pure-KMP.
 *
 * `Ncb`, `FreeLookEngine` and `PortabilityEngine` are stateless objects — no binding needed;
 * callers reference them directly.
 *
 * Usage in the app: `startKoin { modules(serverPersistenceModule, policyModule) }`.
 */
val policyModule = module {
    single { RenewalEngine(rates = get(), plans = get()) }
    single { EndorsementEngine(rates = get()) }
}
