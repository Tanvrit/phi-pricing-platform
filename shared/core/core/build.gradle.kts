plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// ── core (umbrella) ──────────────────────────────────────────────────────────
// Tanvrit-literal pattern: re-exports the six foundation leaves via api(project) so
// every sdk-* feature declares exactly ONE dependency — com.rate:core — and gets the
// whole foundation transitively. No domain types of its own.
kotlin {
    jvmToolchain(21)
    jvm()
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) { iosArm64(); iosX64(); iosSimulatorArm64() }
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:core-base"))
            api(project(":shared:core:core-money"))
            api(project(":shared:core:core-network"))
            api(project(":shared:core:core-auth"))
            api(project(":shared:core:core-regulatory"))
            api(project(":shared:core:core-rating-ports"))
        }
    }
}
