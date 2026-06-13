package com.rate.sdk.quoting.di

import com.rate.sdk.quoting.event.QuoteEventSink
import com.rate.sdk.quoting.handler.QuoteHandler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for sdk-quoting. Binds only this module's HANDLER — the two PORTs it depends on
 * (core's `RatingPort` and `QuoteRepository`) and the [QuoteEventSink] are provided by the app
 * layer: server-persistence binds the Mongo-backed `QuoteRepository`, and sdk-rating's Koin
 * module binds the concrete `PricingEngine` as the `RatingPort`. This keeps sdk-quoting pure-KMP
 * and free of any platform-specific or same-layer concrete dependency.
 *
 * Usage in the app:
 * `startKoin { modules(serverPersistenceModule, ratingModule, quotingModule()) }`.
 */
fun quotingModule(): Module = module {
    single {
        QuoteHandler(
            rating = get(),
            repository = get(),
            events = getOrNull() ?: QuoteEventSink.NOOP,
        )
    }
}
