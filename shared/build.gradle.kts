plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // ── JVM (server + desktop) ─────────────────────────────────────────────
    jvm()

    // ── iOS targets (requires Xcode on macOS) ─────────────────────────────
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    // ── Web / WASM ─────────────────────────────────────────────────────────
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
