plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── core-auth ─────────────────────────────────────────────────────────────────
// The AUTH contract: pure-KMP wire models + PORT interfaces for OTP, JWT-style
// tokens, sessions, KYC and RBAC. NO crypto secret, NO JWT library, NO Mongo, NO
// HMAC key material — those live ONLY in the server app. This module declares the
// shapes that flow over the wire and the ports the app implements.
//   - OtpStore / SessionStore: stateful store ports (in-memory now, Mongo later).
//   - TokenSigner: sign/verify port; the real HMAC/JWT signer is a server actual.
//   - Scope / Authorization: pure RBAC value-types + a pure requireScope check.
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
            implementation(project(":shared:core:core-network"))
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
