plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-catalog ──────────────────────────────────────────────────────────────
// The product/benefit CATALOG — the biggest admin-CRUD surface of the platform.
// RETAIL + GROUP product configuration via ProductLine (from core):
//   Product, Section, Cover/Benefit, CriticalIllnessList, Annexure, AddOn, Tenure,
//   PincodeZone (+ resolver) and the full GROUP config tree (GroupProductConfig,
//   GroupGrade, BenefitSchedule, WaitingPeriod, EligibilityCriteria, PpdPtdTable,
//   DayCareProcedure, ConsumablesList, HealthCheckupPackage, ChronicOpdGrid).
//
// Each persisted entity is an @Serializable ConfigEntity; each gets a PORT repository
// extending core's generic ConfigRepository<T>. Mongo-backed actuals live later in
// server-persistence. Pure-KMP: only kotlinx + koin + ktor-client (read surface).
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Ktor client read surface (CatalogApi) — multiplatform engine.
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
