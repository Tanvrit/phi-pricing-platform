plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// ── sdk-proposal ─────────────────────────────────────────────────────────────
// The BUY-ONLINE journey feature: drive a customer from OTP login through eligibility,
// premium, KYC and payment to a submitted Proposal, plus the save+resume session.
//
//   model/Proposal.kt            — Proposal (: BaseDataClass) — proposalNumber + status +
//                                  members + plan/quote refs + payment + kyc. ProposalStatus.
//   model/BuyOnlineSessionState  — RELOCATED in-flight journey snapshot (save+resume).
//   model/Kyc.kt                 — KycState / KycDocRef over core-auth KycMethod.
//   model/Payment.kt             — PaymentInfo + BankDetails value objects.
//   model/journey/JourneyContracts.kt — Eligibility / Premium / Hospital request+result DTOs.
//   network/BuyOnlineApi.kt      — Ktor-client for /api/buy-online/* (paths preserved).
//   network/BuyOnlineDtos.kt     — wire DTOs for the journey endpoints.
//   repository/*                 — ProposalRepository (admin list + getByProposalNumber) and
//                                  SessionRepository (TTL save+resume) PORTs.
//   handler/*                    — OtpHandler (core-auth OtpStore + TokenSigner ports),
//                                  KycHandler, EligibilityHandler, PremiumHandler (delegates
//                                  to sdk-quoting QuoteHandler), ProposalHandler.
//   event/ProposalEvents.kt      — ProposalEvent + ProposalEventSink PORT (NOOP).
//   di/ProposalModule.kt         — Koin wiring (handlers from the injected PORTs).
//
// Depends on core (the rating contract + core-auth OTP/KYC/token ports) plus the sibling
// SDK features it composes: sdk-catalog (AddOn/PincodeZone resolution), sdk-party
// (RetailProposer/PartyMember identity) and sdk-quoting (QuoteHandler for premium).
// PURE-KMP: only kotlinx + koin + ktor-client. NO Mongo / Ktor-server / Compose / POI —
// the repository, OtpStore, TokenSigner and RatingPort actuals (secrets included) are bound
// by the app layer (server-persistence, server).
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
            implementation(project(":shared:sdk:sdk-party"))
            implementation(project(":shared:sdk:sdk-quoting"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Ktor client journey surface (BuyOnlineApi) — multiplatform engine.
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
