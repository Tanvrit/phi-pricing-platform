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
