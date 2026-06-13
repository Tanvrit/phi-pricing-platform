plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-rating ───────────────────────────────────────────────────────────────
// The RATING engine layer. Relocates the monolithic PricingEngine VERBATIM (Double
// math + MONEY_EPS preserved for byte-for-byte Excel parity) behind the core RatingPort,
// and adds the GROUP rating path (GroupPricingEngine over GroupRateDataProvider + Census).
//   handler/  PricingEngine (: RatingPort), GroupPricingEngine
//   model/    CoverIds + COVER_ACCUM_BASES (engine rate-row contract), QuoteValidators,
//             RatingStrategy, PremiumAllocation, GroupQuoteResult, ClaimYear
//   data/     InProcessRateDataProvider (offline WASM/desktop fallback provider)
//   repository/ RatingPortAdapters (RenewalRateProvider over RateDataProvider)
//   di/       RatingModule (binds RatingPort=PricingEngine, GroupPricingEngine, RenewalRateProvider)
//
// PURE-KMP: only kotlinx + koin in commonMain — NO Mongo / Ktor-server / Compose / POI.
// The Mongo-backed RateDataProvider actual lives later in server-persistence.
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
