package com.rate.core.network.security

/**
 * Platform-free certificate-pinning DESCRIPTOR. core-network is pure-KMP and
 * cannot itself enforce pinning (that needs the platform TLS stack), so it carries
 * the intent and the app/engine actuals enforce it where supported:
 *   - JVM/Darwin engines can install an X509 trust manager / pinner from these.
 *   - The WASM/browser engine cannot pin (the browser owns TLS) — descriptors are
 *     advisory there and silently not enforced.
 *
 * @property host     Host the pins apply to, e.g. `api.tanvrit.com`. `*.` prefix
 *                    matches one subdomain label.
 * @property pinSha256 Base64-encoded SHA-256 of the Subject Public Key Info
 *                     (the `sha256/AAAA…=` form). One or more for rotation overlap.
 * @property includeSubdomains Apply the pins to subdomains of [host] too.
 * @property enforced  When false the pin is observed/logged but not hard-failed
 *                     (useful for a staged rollout before flipping to enforce).
 */
data class CertPin(
    val host: String,
    val pinSha256: List<String>,
    val includeSubdomains: Boolean = false,
    val enforced: Boolean = true,
) {
    init {
        require(host.isNotBlank()) { "CertPin.host must not be blank" }
        require(pinSha256.isNotEmpty()) { "CertPin must carry at least one SHA-256 pin" }
        require(pinSha256.all { it.startsWith("sha256/") || it.length >= 44 }) {
            "CertPin pins must be base64 SHA-256 (optionally 'sha256/'-prefixed)"
        }
    }

    /** True when [candidateHost] is covered by this descriptor. */
    fun matches(candidateHost: String): Boolean {
        if (candidateHost.equals(host, ignoreCase = true)) return true
        return includeSubdomains && candidateHost.endsWith(".$host", ignoreCase = true)
    }
}
