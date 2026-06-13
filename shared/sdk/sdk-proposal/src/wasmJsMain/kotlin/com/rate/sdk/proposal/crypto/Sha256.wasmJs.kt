package com.rate.sdk.proposal.crypto

/** WASM/JS SHA-256 — the pure-Kotlin implementation (no Web-Crypto async dependency needed). */
actual fun sha256Hex(input: String): String = sha256HexPure(input)
