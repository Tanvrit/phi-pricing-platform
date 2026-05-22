plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    // ── JVM (server + desktop) ─────────────────────────────────────────────
    jvm {
        // Use JUnit 5 platform for jvmTest
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // ── iOS targets (requires Xcode on macOS) ─────────────────────────────
    // Only declare iOS targets on macOS hosts so non-macOS CI can still build :shared.
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) {
        iosArm64()
        iosX64()
        iosSimulatorArm64()
    }

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
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.junit.platform.launcher)
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
        }
    }
}
