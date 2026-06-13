plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-ingestion ────────────────────────────────────────────────────────────
// The DATA-INGESTION layer: turns the /data CSV exports + the actuarial rate sheets
// into catalog entities (via sdk-catalog repository PORTs) and immutable rate rows.
//
// Two distinct surfaces:
//  1. CATALOG ingestion — a PURE-KMP CSV parser (CsvCatalogParser) that reads the
//     Product-Benefit-Table / PBT CSV shapes (sections, benefits, CI tiers, PPD/PTD,
//     waiting periods, eligibility, day-care, consumables, health-checkup, chronic-OPD)
//     into sdk-catalog @Serializable entities, plus an idempotent CatalogSeeder that
//     writes them through the generic ConfigRepository<T> ports.
//  2. RATE ingestion — immutable value rows (BaseRateRow, CoverRateRow, MemberLevelRow,
//     DiscountRow, InstalmentRow, CoverAvailabilityRow) with deterministic composite ids,
//     a RateImportRepository PORT (bulkUpsert) + a RateMetaRepository PORT (active-version
//     pointer + SHA dedupe). RateMeta carries sourceFileSha256 so a re-upload of the same
//     file is a no-op.
//
// The Excel(POI) importer is JVM-only and lives later in server-persistence; here the
// CSV parsing is PURE-KMP. NO Mongo / Ktor-server / Apache-POI / Compose. Mongo-backed
// repository actuals live in server-persistence.
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
            implementation(project(":shared:sdk:sdk-catalog"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Ktor client surface (IngestionApi admin read/write) — multiplatform engine.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json.mp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
