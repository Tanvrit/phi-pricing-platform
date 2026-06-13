package com.rate.server

import com.rate.server.security.AppSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppSecretsTest {

    @Test
    fun `production fail-fasts when OTP secret is missing`() {
        assertFailsWith<IllegalStateException> {
            AppSecrets.fromEnv(env = emptyMap(), requireSecrets = true)
        }
    }

    @Test
    fun `production rejects a known placeholder secret`() {
        assertFailsWith<IllegalArgumentException> {
            AppSecrets(
                otpTokenSecret = "development_only_32_byte_secret_change_me",
                jwtSigningSecret = "development_only_32_byte_secret_change_me",
                devProfile = false,
            )
        }
    }

    @Test
    fun `dev profile falls back to a usable secret`() {
        val s = AppSecrets.fromEnv(env = emptyMap(), requireSecrets = false)
        assertTrue(s.devProfile)
        assertTrue(s.otpTokenSecret.length >= AppSecrets.MIN_SECRET_LEN)
    }

    @Test
    fun `JWT secret falls back to OTP secret when unset`() {
        val s = AppSecrets.fromEnv(
            env = mapOf(AppSecrets.ENV_OTP_SECRET to "real-otp-secret-0123456789abcdef"),
            requireSecrets = true,
        )
        assertEquals(s.otpTokenSecret, s.jwtSigningSecret)
    }
}
