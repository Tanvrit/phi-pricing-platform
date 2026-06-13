plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-audit ────────────────────────────────────────────────────────────────
// Append-only, hash-chained audit log + idempotency. PURE-KMP so client and server
// compute byte-identical hashes: the canonicalizer is deterministic (sorted keys,
// integers without ".0", no scientific notation) and SHA-256 is provided per-target
// via expect/actual (JVM MessageDigest, pure-Kotlin on wasmJs/iOS). NO Mongo / Ktor-
// server / Compose — Mongo-backed AuditStore/IdempotencyStore actuals live in
// server-persistence; this module only declares the PORT interfaces + portable logic.
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
