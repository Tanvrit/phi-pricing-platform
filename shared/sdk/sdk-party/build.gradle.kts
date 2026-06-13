plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-party ──────────────────────────────────────────────────────────────────
// Parties for RETAIL + GROUP: the proposer/employer/member identity layer.
// RetailProposer & GroupEmployer (sealed PolicyHolder, admin/transactional config),
// persisted PartyMember (floater members), and the GROUP census (Census + per-grade
// age-band aggregation feeding the group rating roll-up). Repository PORTs only
// (PartyRepository / CensusRepository) + pure validation/ingest handlers. PURE-KMP:
// NO Mongo / Ktor-server / Compose — Mongo-backed actuals live in server-persistence.
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
