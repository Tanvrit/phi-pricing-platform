package com.rate.sdk.audit.crypto

/**
 * SHA-256 of [input] (UTF-8 encoded) as a lowercase hex string (64 chars).
 *
 * MUST return byte-identical results on every target so that a hash chained on the
 * server verifies on a wasmJs/iOS client. The JVM actual uses
 * `java.security.MessageDigest`; wasmJs and iOS use the shared pure-Kotlin
 * implementation in [Sha256Pure] (no platform crypto dependency required).
 */
expect fun sha256Hex(input: String): String
