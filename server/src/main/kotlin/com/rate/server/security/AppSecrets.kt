package com.rate.server.security

/**
 * The single home for app-layer SECRETS. The core → sdk → sdk-ui DAG is deliberately
 * secret-free — the crypto keys (HMAC/JWT signing secret, OTP-token secret) and the Mongo
 * URI live ONLY here in the app shell.
 *
 * FAIL-FAST: production deployments must supply the secrets via the environment; a blank or
 * placeholder secret is rejected at boot rather than silently signing tokens with a guessable
 * key. This mirrors the old server's "`DB_PASSWORD` has no default — fail fast" rule.
 *
 *  - `OTP_TOKEN_SECRET`     — HMAC secret for the legacy short-lived OTP-token machinery and the
 *                             post-verify bearer (used by [JwtTokenSigner] when a dedicated
 *                             [ENV_JWT_SECRET] is not configured).
 *  - `JWT_SIGNING_SECRET`   — HS256 signing secret for the access [com.rate.core.auth.token.JwtClaims].
 *                             Falls back to [ENV_OTP_SECRET] when unset so a single-secret dev
 *                             deployment still works, but the two are independent in production.
 */
data class AppSecrets(
    val otpTokenSecret: String,
    val jwtSigningSecret: String,
    /** Whether dev-only conveniences (e.g. surfacing the OTP plaintext) are allowed. */
    val devProfile: Boolean = false,
) {
    init {
        require(otpTokenSecret.length >= MIN_SECRET_LEN) {
            "$ENV_OTP_SECRET must be at least $MIN_SECRET_LEN chars (got ${otpTokenSecret.length})"
        }
        require(jwtSigningSecret.length >= MIN_SECRET_LEN) {
            "$ENV_JWT_SECRET must be at least $MIN_SECRET_LEN chars (got ${jwtSigningSecret.length})"
        }
        // Outside the dev profile, reject known placeholder secrets so a misconfigured
        // production deployment fails at boot rather than signing with a guessable key.
        if (!devProfile) {
            require(otpTokenSecret !in WEAK) {
                "$ENV_OTP_SECRET is a known placeholder — set a real secret in production"
            }
            require(jwtSigningSecret !in WEAK) {
                "$ENV_JWT_SECRET is a known placeholder — set a real secret in production"
            }
        }
    }

    companion object {
        const val ENV_OTP_SECRET = "OTP_TOKEN_SECRET"
        const val ENV_JWT_SECRET = "JWT_SIGNING_SECRET"
        const val ENV_DEV_PROFILE = "AEGIS_DEV_PROFILE"
        const val MIN_SECRET_LEN = 16

        /** Placeholder/dev secrets that must never be used in a non-dev profile. */
        private val WEAK = setOf(
            "development_only_32_byte_secret_change_me",
            "change_me",
            "changeme",
            "secret",
        )

        /**
         * Build from the process environment. [requireSecrets] = true (production) fail-fasts when
         * [ENV_OTP_SECRET] is unset; tests pass false to fall back to a dev secret + dev profile.
         */
        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            requireSecrets: Boolean = true,
        ): AppSecrets {
            val dev = env[ENV_DEV_PROFILE]?.equals("true", ignoreCase = true) ?: !requireSecrets
            val otp = env[ENV_OTP_SECRET]?.takeIf { it.isNotBlank() }
                ?: if (requireSecrets) {
                    error("$ENV_OTP_SECRET environment variable is required (no default in production)")
                } else {
                    "dev-otp-secret-0123456789abcdef"
                }
            val jwt = env[ENV_JWT_SECRET]?.takeIf { it.isNotBlank() }
                ?: otp // single-secret deployments reuse the OTP secret for JWT signing.
            return AppSecrets(otpTokenSecret = otp, jwtSigningSecret = jwt, devProfile = dev)
        }
    }
}
