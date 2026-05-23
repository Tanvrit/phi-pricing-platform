plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    jvm {
        // jvm() target produces the Aegis desktop binary — replaces what :desktop used to do.
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json.mp)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.coroutines.swing)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

compose.desktop {
    application {
        // JVM main — boots in BUSINESS mode by default. Customer mode (the buyonline
        // journey) is on the WASM target, served via Cloudflare Pages.
        mainClass = "com.rate.aegis.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "Aegis"
            packageVersion = "1.0.0"
        }
    }
}

// Post-process the Compose-WASM distribution to wire in the Aegis shield favicon.
// The Kotlin/Wasm webpack pipeline generates index.html itself (no source template
// is available to edit at build time), so we copy the SVG into the dist directory
// and rewrite the placeholder `<link rel="icon" href="data:,">` after distribution.
val injectAegisFavicon = tasks.register("injectAegisFavicon") {
    val distDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val svgSource = file("src/wasmJsMain/resources/aegis-icon.svg")
    inputs.file(svgSource)
    outputs.dir(distDir)
    doLast {
        val outDir = distDir.get().asFile
        if (!outDir.exists()) return@doLast
        // Copy the shield icon next to index.html so the favicon ref resolves.
        svgSource.copyTo(outDir.resolve("aegis-icon.svg"), overwrite = true)
        val indexHtml = outDir.resolve("index.html")
        if (indexHtml.exists()) {
            val original = indexHtml.readText()
            val faviconLink = "<link rel=\"icon\" type=\"image/svg+xml\" href=\"aegis-icon.svg\">"
            val updated = when {
                original.contains(faviconLink) -> original
                original.contains("<link rel=\"icon\" href=\"data:,\">") ->
                    original.replace("<link rel=\"icon\" href=\"data:,\">", faviconLink)
                original.contains("</head>") ->
                    original.replace("</head>", "    $faviconLink\n</head>")
                else -> original
            }
            if (updated != original) {
                indexHtml.writeText(updated)
            }
        }
    }
}

tasks.matching { it.name == "wasmJsBrowserDistribution" || it.name == "wasmJsBrowserProductionWebpack" }
    .configureEach { finalizedBy(injectAegisFavicon) }
