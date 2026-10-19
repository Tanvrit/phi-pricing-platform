// Root build script.
//
// NOTE on Kover (code coverage): we deferred Kover wiring until it ships a release that
// works with both Gradle 8.x (the wrapper's pinned version) and Gradle 9.x (commonly
// installed locally). Tests + JUnit 5 + Kotest are wired and run via `./gradlew test`;
// coverage reporting is the only missing piece until Kover compatibility settles.
// See CHANGELOG.md → Unreleased → Deferred.

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Compose selects compatible lifecycle and collection artifacts transitively.

// Mocha 12 removes vulnerable serializer/diff dependencies and supports the managed Node runtimes.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
        versions.mocha.version = "12.0.3"
        versions.kotlinWebHelpers.version = "3.5.4"
    }
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension> {
        versions.mocha.version = "12.0.3"
        versions.kotlinWebHelpers.version = "3.5.4"
    }
}

// Use Ktor's compatible Node WebSocket security patch in generated web graphs.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
        resolution("ws", "8.21.0")
    }
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension> {
        resolution("ws", "8.21.0")
    }
}

// Preserve Kotlin's full TeamCity event reporting with Mocha's ES-class Base.
val mochaReporterBridge = layout.projectDirectory.file("tools/mocha12-kotlin-reporter.cjs")
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
        if (name.endsWith("NodeTest")) {
            nodeJsArgs.addAll(listOf("--require", mochaReporterBridge.asFile.absolutePath))
            inputs.file(mochaReporterBridge)
        }
    }
}
