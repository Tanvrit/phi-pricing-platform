package com.rate.sdk.party.di

import com.rate.sdk.party.event.PartyEventSink
import com.rate.sdk.party.handler.PartyHandler
import com.rate.sdk.party.handler.group.CensusHandler
import org.koin.dsl.module

/**
 * Koin wiring for sdk-party. Binds only this module's HANDLERS — the repository PORTs
 * ([com.rate.sdk.party.repository.PartyRepository] / CensusRepository) and the
 * [PartyEventSink] are provided by the app layer (server-persistence binds the Mongo
 * actuals), so this module stays pure-KMP and depends on nothing platform-specific.
 *
 * Usage in the app: `startKoin { modules(serverPersistenceModule, partyModule) }`.
 */
val partyModule = module {
    single { PartyHandler(repository = get(), events = getOrNull() ?: PartyEventSink.NOOP) }
    single { CensusHandler(repository = get(), events = getOrNull() ?: PartyEventSink.NOOP) }
}
