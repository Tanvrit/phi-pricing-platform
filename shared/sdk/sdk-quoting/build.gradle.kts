plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-quoting ──────────────────────────────────────────────────────────────
// The QUOTING feature: turn a QuoteRequest into a priced, persisted Quote and the
// IRDAI documents that must accompany it.
//
//   model/Quote.kt           — sealed Quote : RetailQuote | GroupQuote (a BaseDataClass
//                              envelope wrapping the QuoteRequest + QuoteResult + meta).
//   handler/QuoteHandler.kt  — calculate(request) via the injected RatingPort (core) and
//                              persist via the QuoteRepository (core).
//   handler/doc/*            — RELOCATED IRDAI doc builders (Sales Illustration, CIS,
//                              Prospectus) + a new GroupBenefitScheduleBuilder. They consume
//                              QuoteResult + RenewalIllustrationLine from core.
//   network/*                — Ktor-client read/calculate surface (QuoteApi + QuoteDtos).
//   di/QuotingModule.kt      — Koin wiring (handler from the injected PORTs).
//
// Depends on core (the rating contract + Plan/QuoteRequest/QuoteResult) plus the sibling
// SDK features it composes documents from: sdk-catalog (Cover/Product/AddOn/BenefitSchedule)
// and sdk-party (Member/PolicyHolder identity). PURE-KMP: only kotlinx + koin + ktor-client.
// NO Mongo / Ktor-server / Compose — the QuoteRepository / RatingPort actuals are bound by
// the app layer (server-persistence over the Mongo snapshot, sdk-rating's PricingEngine).
kotlin {
    jvmToolchain(21)
    jvm()
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) { iosArm64(); iosX64(); iosSimulatorArm64() }
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:core"))
            implementation(project(":shared:sdk:sdk-catalog"))
            implementation(project(":shared:sdk:sdk-party"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Ktor client read/calculate surface (QuoteApi) — multiplatform engine.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json.mp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
