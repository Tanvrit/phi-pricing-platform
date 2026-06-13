plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-ui-buyonline ─────────────────────────────────────────────────────────
// The PUBLIC customer journey — the 22-screen buy-online flow rendered with
// Compose Multiplatform (jvm operator-embed + wasmJs Cloudflare-Pages customer
// app). Relocated from the monolith's `aegis/customer/buyonline` package.
//
// Layering: this is a top sdk-ui-* leaf. It reuses the design system + PRU
// customer brand + EN/HI i18n from sdk-ui-kit rather than re-rolling widgets,
// and drives the whole flow through the feature SDKs' Ktor-client surfaces
// (sdk-proposal `BuyOnlineApi`, sdk-quoting `QuoteApi`) layered on
// core-network's `TanvritClient`. NO Mongo driver, NO Ktor-server, NO POI; the
// concrete PricingEngine + repositories are server-only — every rupee the
// customer sees comes back over the wire from the server-side handlers.
//
// Exposes a single composable BuyOnlineApp(client, …) + a Koin BuyOnlineUiModule.
kotlin {
    jvmToolchain(21)
    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:sdk:sdk-ui-kit"))
            implementation(project(":shared:sdk:sdk-proposal"))
            implementation(project(":shared:sdk:sdk-quoting"))
            implementation(project(":shared:sdk:sdk-rating"))
            implementation(project(":shared:core:core-network"))
            implementation(project(":shared:core:core"))
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
