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

// Pin androidx.lifecycle to a version that's reliably in the local Gradle cache.
// Compose Material 1.7.3 transitively pulls androidx.lifecycle 2.8.5, which lives
// only on dl.google.com — a host that is intermittently unreachable in some dev
// environments (CI, restricted networks). Forcing 2.9.4 (cached locally on the
// reference machine and superset-compatible at the API level) keeps :desktop and
// :buyonline runnable without a working Google Maven mirror.
subprojects {
    configurations.all {
        resolutionStrategy {
            force(
                "androidx.lifecycle:lifecycle-common:2.9.4",
                "androidx.lifecycle:lifecycle-runtime:2.9.4",
                "androidx.lifecycle:lifecycle-viewmodel:2.9.4"
            )
        }
    }
}
