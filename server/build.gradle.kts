plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    alias(libs.plugins.shadow)
}

// ── server (app shell) ─────────────────────────────────────────────────────────
// The Ktor backend. APP layer of the core → sdk → sdk-ui → app DAG: it wires the
// pure-KMP sdk feature HANDLERS to HTTP and to the Mongo-backed actuals from
// :server-persistence (the ONLY module that touches the Mongo driver). Secrets
// (OTP_TOKEN_SECRET / JWT_SIGNING_SECRET / MONGO_URI) are read here and nowhere
// else. NO Exposed / Flyway / Hikari / Postgres / POI — Mongo only; the Excel
// importer lives in :server-persistence.
application {
    mainClass.set("com.rate.server.ApplicationKt")
}

dependencies {
    // App-layer persistence (Mongo actuals for every core + sdk PORT).
    implementation(project(":server-persistence"))
    // App layer may touch the Mongo driver directly (bootstrap, health check, DI of MongoDatabase).
    implementation(libs.mongodb.driver.kotlin.coroutine)

    // Core foundation slices used directly by the server shell (auth/token/rbac,
    // network error envelope, base models/json/errors).
    implementation(project(":shared:core:core"))
    implementation(project(":shared:core:core-network"))
    implementation(project(":shared:core:core-auth"))

    // Every sdk feature module — the server mounts their handlers as route groups.
    implementation(project(":shared:sdk:sdk-catalog"))
    implementation(project(":shared:sdk:sdk-party"))
    implementation(project(":shared:sdk:sdk-rating"))
    implementation(project(":shared:sdk:sdk-quoting"))
    implementation(project(":shared:sdk:sdk-proposal"))
    implementation(project(":shared:sdk:sdk-policy"))
    implementation(project(":shared:sdk:sdk-audit"))
    implementation(project(":shared:sdk:sdk-ingestion"))

    // Ktor server.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Koin (Ktor integration) — the server installs serverModule + persistenceModule.
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    implementation(libs.logback.classic)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
