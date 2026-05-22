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

include(":shared", ":server", ":desktop", ":buyonline", ":aegis")
