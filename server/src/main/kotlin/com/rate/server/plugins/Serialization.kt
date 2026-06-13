package com.rate.server.plugins

import com.rate.core.base.json.AppJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/**
 * Wire (de)serialization. Uses the ONE frozen [AppJson.json] config shared across the whole
 * platform (REST wire + on-disk + the BSON codec registry in server-persistence) so the server,
 * the client SDK and the database all agree byte-for-byte on field names and null handling.
 *
 * RELOCATED from the monolith's ad-hoc per-plugin `Json { … }` — the re-arch centralises the
 * config in core-base so it can never drift.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(AppJson.json)
    }
}
