plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── core-base ────────────────────────────────────────────────────────────────
// Pure-KMP foundation leaf: BaseDataClass / ConfigEntity wire envelope, AppJson,
// ULID id generation, Clock abstraction, DomainError, Page. ZERO project deps.
// NO Mongo / Ktor-server / Compose / POI — consumed identically by backend + frontend.
kotlin {
    jvmToolchain(21)
    jvm()
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) {
        iosArm64(); iosX64(); iosSimulatorArm64()
    }
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
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
