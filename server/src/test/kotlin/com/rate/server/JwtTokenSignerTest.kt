package com.rate.server

import com.rate.core.auth.rbac.AuthRole
import com.rate.core.auth.token.JwtClaims
import com.rate.core.base.time.Now
import com.rate.server.security.JwtTokenSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtTokenSignerTest {

    private val secret = "test-signing-secret-0123456789abcdef"
    private val signer = JwtTokenSigner(signingSecret = secret, keyId = "k1")

    @Test
    fun `sign then verify round-trips the claims`() {
        val claims = JwtClaims(
            sub = "9876543210",
            role = AuthRole.CUSTOMER,
            scopes = setOf("buyonline.login", "quote.run"),
            exp = Now.instant().epochSeconds + 900,
        )
        val token = signer.sign(claims)
        assertEquals(3, token.split('.').size, "JWT must have 3 dot-separated segments")

        val decoded = signer.verify(token)
        assertNotNull(decoded)
        assertEquals("9876543210", decoded.sub)
        assertEquals(AuthRole.CUSTOMER, decoded.role)
        assertTrue("quote.run" in decoded.scopes)
    }

    @Test
    fun `tampered payload fails verification`() {
        val claims = JwtClaims(sub = "u1", role = AuthRole.ADMIN, exp = Now.instant().epochSeconds + 600)
        val token = signer.sign(claims)
        val parts = token.split('.')
        // Flip the payload segment to a different (but well-formed base64url) value.
        val forged = parts[0] + "." + "ZXZpbA" + "." + parts[2]
        assertNull(signer.verify(forged))
    }

    @Test
    fun `expired token fails verification`() {
        val claims = JwtClaims(sub = "u1", role = AuthRole.BUSINESS, exp = Now.instant().epochSeconds - 1)
        val token = signer.sign(claims)
        assertNull(signer.verify(token))
    }

    @Test
    fun `token signed with a different secret fails verification`() {
        val claims = JwtClaims(sub = "u1", role = AuthRole.BUSINESS, exp = Now.instant().epochSeconds + 600)
        val token = JwtTokenSigner("another-secret-0123456789abcdef").sign(claims)
        assertNull(signer.verify(token))
    }
}
