plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// ── server-persistence ────────────────────────────────────────────────────────
// The APP-layer persistence shell. THE ONLY module that depends on the MongoDB
// driver (mongodb-driver-kotlin-coroutine + bson-kotlinx) and Apache POI. It
// implements EVERY pure-KMP PORT declared in core + the sdk feature modules with
// concrete MongoDB-backed actuals, loads the active rate-table version into an
// in-RAM snapshot at boot, and runs the JVM-only Excel rate importer (POI).
//
// JVM-only: there is no commonMain here — Mongo + POI are JVM libraries and
// secrets/IO live exclusively in the app layer per the core→sdk→sdk-ui→app DAG.
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Core foundation + every SDK feature whose PORTS we implement.
    implementation(project(":shared:core:core"))
    implementation(project(":shared:sdk:sdk-catalog"))
    implementation(project(":shared:sdk:sdk-party"))
    implementation(project(":shared:sdk:sdk-rating"))
    implementation(project(":shared:sdk:sdk-quoting"))
    implementation(project(":shared:sdk:sdk-proposal"))
    implementation(project(":shared:sdk:sdk-policy"))
    implementation(project(":shared:sdk:sdk-audit"))
    implementation(project(":shared:sdk:sdk-ingestion"))

    // The ONLY place the Mongo driver + BSON codec live.
    implementation(libs.mongodb.driver.kotlin.coroutine)
    implementation(libs.bson.kotlinx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.core)

    // The ONLY place Apache POI lives (JVM Excel rate import). CSV stays in sdk-ingestion.
    implementation(libs.poi.ooxml)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
