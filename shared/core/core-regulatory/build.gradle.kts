plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── core-regulatory ──────────────────────────────────────────────────────────
// Pure-KMP constants + value-types: Zone, AgeBand, FamilyType, GST, discount cap,
// PayoutType/CoverageLogic, UIN, IRDAI numeric constants. NO doc builders (those
// consume QuoteResult and live in sdk-quoting). NO Mongo/Ktor/Compose.
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
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
