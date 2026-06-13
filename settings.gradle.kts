rootProject.name = "rate-calculator"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // mavenCentral first because dl.google.com intermittently times out for
    // artifacts (e.g. JetBrains Skiko) that only live on mavenCentral anyway.
    // google() stays for Android-specific deps; Aliyun's Google mirror is the
    // failover when dl.google.com connection-resets (Connection reset during TLS).
    repositories {
        mavenCentral()
        google()
        maven {
            name = "Google Maven Mirror (Aliyun)"
            url = uri("https://maven.aliyun.com/repository/google")
            content { includeGroupByRegex("androidx\\..*") }
        }
    }
}

// ── Re-architecture: layered core → sdk → sdk-ui → app DAG ────────────────────
// The old monolithic :shared / :server / :aegis are decomposed into pure-KMP
// shared/core/* + shared/sdk/* feature modules (consumed identically by the JVM
// backend and the Compose frontend) plus thin JVM/platform app shells. The Mongo
// driver, secrets and POI live ONLY in the app layer (:server-persistence, :server).

// CORE — pure-KMP foundation (shared/core/*)
include(
    ":shared:core:core-base",
    ":shared:core:core-money",
    ":shared:core:core-network",
    ":shared:core:core-auth",
    ":shared:core:core-regulatory",
    ":shared:core:core-rating-ports",
    ":shared:core:core",
)

// SDK — pure-KMP feature modules (shared/sdk/*)
include(
    ":shared:sdk:sdk-catalog",
    ":shared:sdk:sdk-party",
    ":shared:sdk:sdk-rating",
    ":shared:sdk:sdk-quoting",
    ":shared:sdk:sdk-proposal",
    ":shared:sdk:sdk-policy",
    ":shared:sdk:sdk-audit",
    ":shared:sdk:sdk-ingestion",
)

// SDK-UI — Compose Multiplatform feature UI (shared/sdk/*-ui)
include(
    ":shared:sdk:sdk-ui-kit",
    ":shared:sdk:sdk-ui-buyonline",
    ":shared:sdk:sdk-ui-operator",
)

// APP — platform shells (Mongo + secrets confined here)
include(
    ":server-persistence",
    ":server",
    ":aegis",
)
