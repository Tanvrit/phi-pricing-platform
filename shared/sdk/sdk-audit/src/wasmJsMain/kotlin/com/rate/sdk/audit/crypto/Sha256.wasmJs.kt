package com.rate.sdk.audit.crypto

/** wasmJs: pure-Kotlin SHA-256 (no WebCrypto async dependency). */
actual fun sha256Hex(input: String): String = Sha256Pure.hex(input)
