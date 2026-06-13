plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── core-network ─────────────────────────────────────────────────────────────
// Pure-KMP HTTP client BASE shared by BOTH the backend (server-side calls) and the
// frontend (Compose). Wraps a Ktor *client* (ContentNegotiation(AppJson.json),
// logging, timeouts), a transport-agnostic NetworkError taxonomy mapped from
// DomainError, a safeCall() boundary returning ApiResponse<T>, an AuthProvider
// port (bearer token + onUnauthorized), an exponential-backoff RetryPolicy, and a
// CertPin descriptor. The Ktor *engine* is the only platform seam — supplied via
// the expect/actual httpClientEngineFactory() (CIO on JVM, Js on WASM, Darwin on
// iOS). NO Mongo / Ktor-server / Compose / POI — depends only on core-base.
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json.mp)
            implementation(libs.ktor.client.logging)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        if (os.isMacOsX) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}
