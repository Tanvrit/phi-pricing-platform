plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-ui-kit ───────────────────────────────────────────────────────────────
// The DESIGN SYSTEM + i18n home for the whole UI layer. Compose Multiplatform
// lives ONLY in sdk-ui-* modules; this is the lowest one — pure tokens,
// reusable primitives (buttons, table, cards, top bar, drawer, toasts,
// command palette, …), the Aegis light/dark theme, the PRU customer brand, and
// the EN/HI string catalog with a per-target host-locale probe.
//
// Other -ui modules (sdk-ui-operator, sdk-ui-buyonline) depend on this and
// reuse its component API rather than re-rolling their own widgets. Depends only
// on :shared:core:core (Money/regulatory tokens) + Compose; ktor client is here
// so kit-level networked widgets stay possible without forcing each -ui module
// to re-declare the engine.
kotlin {
    jvmToolchain(21)
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
