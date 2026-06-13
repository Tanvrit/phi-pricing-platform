plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-ui-operator ────────────────────────────────────────────────────────
// The OPERATOR + ADMIN console. Compose Multiplatform; the top -ui module that
// composes the whole operator workbench: the relocated aegis surfaces
// (dashboard, quote explorer, calculator, configurator, import, audit) PLUS the
// generic, metadata-driven ADMIN-CRUD config editor wired to EVERY admin entity
// in sdk-catalog (+ rate tables) and the GROUP product-config / census surfaces.
//
// It depends on sdk-ui-kit (design system) + ALL feature SDKs (so ViewModels can
// speak each feature's network DTOs) + core-network (the shared TanvritClient
// transport) + core. NO MongoDB driver, NO Ktor-server, NO Apache POI here —
// admin mutations go to the server admin-CRUD REST over the ktor client.
kotlin {
    jvmToolchain(21)
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isMacOsX) { iosArm64(); iosX64(); iosSimulatorArm64() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:core"))
            implementation(project(":shared:core:core-network"))
            implementation(project(":shared:sdk:sdk-ui-kit"))
            implementation(project(":shared:sdk:sdk-catalog"))
            implementation(project(":shared:sdk:sdk-party"))
            implementation(project(":shared:sdk:sdk-rating"))
            implementation(project(":shared:sdk:sdk-quoting"))
            implementation(project(":shared:sdk:sdk-policy"))
            implementation(project(":shared:sdk:sdk-audit"))
            implementation(project(":shared:sdk:sdk-ingestion"))
            implementation(project(":shared:sdk:sdk-proposal"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
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
    }
}
