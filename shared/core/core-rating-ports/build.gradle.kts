plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── core-rating-ports ────────────────────────────────────────────────────────
// The rating CONTRACT: the shared rating model (Plan, QuoteRequest/QuoteResult,
// Member, breakdowns, enums) + the PORT interfaces every feature depends on
// (RateDataProvider, RatingPort, RenewalRateProvider, GroupRateDataProvider,
// QuoteRepository, PlanRepository). Mongo-backed actuals live in server-persistence.
// Rate lookups return Double (INR) — Money conversion happens at QuoteResult assembly.
kotlin {
    jvmToolchain(21)
    jvm()
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) { iosArm64(); iosX64(); iosSimulatorArm64() }
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:core-base"))
            implementation(project(":shared:core:core-money"))
            implementation(project(":shared:core:core-regulatory"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
