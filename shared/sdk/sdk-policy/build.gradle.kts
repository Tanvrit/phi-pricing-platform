plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-policy ───────────────────────────────────────────────────────────────
// The in-force policy lifecycle: Policy (+ PolicyStatus FSM) and PolicySeed,
// Nominee, Renewal (RenewalQuote), Endorsement, Portability, Claim — plus the pure
// lifecycle ENGINES relocated from the monolith: RenewalEngine (over the core
// RenewalRateProvider PORT, NOT the concrete pricing engine), Ncb, FreeLookEngine,
// PortabilityEngine, EndorsementEngine. Repository PORTs only (PolicyRepository with
// the renewal-due query + ClaimRepository). Depends on core + sdk-catalog (Plan
// availability / UIN) + sdk-party (proposer + members). PURE-KMP: NO Mongo /
// Ktor-server / Compose — Mongo-backed actuals live in server-persistence.
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
