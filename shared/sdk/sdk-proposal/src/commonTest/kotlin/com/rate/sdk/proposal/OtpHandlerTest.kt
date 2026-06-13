package com.rate.sdk.proposal

import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpRecord
import com.rate.core.auth.otp.OtpStatus
import com.rate.core.auth.otp.OtpStore
import com.rate.core.auth.token.JwtClaims
import com.rate.core.auth.token.TokenSigner
import com.rate.sdk.proposal.crypto.sha256Hex
import com.rate.sdk.proposal.handler.OtpConfig
import com.rate.sdk.proposal.handler.OtpHandler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory OtpStore matching the port contract (the server's Mongo actual). */
private class FakeOtpStore : OtpStore {
    val records = mutableMapOf<String, OtpRecord>()
    val sends = mutableListOf<Triple<String, OtpPurpose, Instant>>()
    private fun key(m: String, p: OtpPurpose) = "$m:$p"
    override suspend fun put(record: OtpRecord) { records[key(record.mobile, record.purpose)] = record }
    override suspend fun get(mobile: String, purpose: OtpPurpose): OtpRecord? = records[key(mobile, purpose)]
    override suspend fun update(record: OtpRecord) { records[key(record.mobile, record.purpose)] = record }
    override suspend fun delete(mobile: String, purpose: OtpPurpose) { records.remove(key(mobile, purpose)) }
    override suspend fun countSendsSince(mobile: String, purpose: OtpPurpose, since: Instant): Int =
        sends.count { it.first == mobile && it.second == purpose && it.third >= since }
    override suspend fun recordSend(mobile: String, purpose: OtpPurpose, at: Instant) {
        sends += Triple(mobile, purpose, at)
    }
}

/** Trivial deterministic signer for tests (the real one — secret-bearing — is in the app). */
private class FakeSigner : TokenSigner {
    override fun sign(claims: JwtClaims): String = "tok:${claims.sub}:${claims.scopes.joinToString(",")}"
    override fun verify(token: String): JwtClaims? = null
}

class OtpHandlerTest {

    private fun handler(store: OtpStore, code: String = "1234"): OtpHandler =
        OtpHandler(store = store, tokenSigner = FakeSigner(), randomCode = { code })

    @Test
    fun sendStoresOnlyTheHashNeverPlaintext() = runTest {
        val store = FakeOtpStore()
        val r = handler(store, code = "4321").send("9876543210", OtpPurpose.LOGIN, devProfile = true)
        assertEquals(OtpStatus.OK, r.status)
        assertEquals("4321", r.devCode) // dev profile surfaces the plaintext
        val rec = store.get("9876543210", OtpPurpose.LOGIN)
        assertNotNull(rec)
        assertEquals(sha256Hex("4321"), rec.codeHash)
        assertTrue(rec.codeHash != "4321") // never the plaintext
    }

    @Test
    fun verifySucceedsWithCorrectCodeAndMintsToken() = runTest {
        val store = FakeOtpStore()
        val h = handler(store, code = "1234")
        h.send("9876543210", OtpPurpose.LOGIN)
        val r = h.verify("9876543210", "1234", OtpPurpose.LOGIN)
        assertEquals(OtpStatus.OK, r.status)
        assertEquals("tok:9876543210:buyonline.login", r.token)
        // record dropped so it can't be replayed
        assertNull(store.get("9876543210", OtpPurpose.LOGIN))
    }

    @Test
    fun verifyMismatchBumpsAttemptsAndReportsRemaining() = runTest {
        val store = FakeOtpStore()
        val h = handler(store, code = "1234")
        h.send("9876543210", OtpPurpose.LOGIN)
        val r = h.verify("9876543210", "0000", OtpPurpose.LOGIN)
        assertEquals(OtpStatus.MISMATCH, r.status)
        assertEquals(4, r.attemptsRemaining)
        assertEquals(1, store.get("9876543210", OtpPurpose.LOGIN)?.attempts)
    }

    @Test
    fun rateLimitTriggersAfterWindowFull() = runTest {
        val store = FakeOtpStore()
        val h = OtpHandler(
            store = store,
            tokenSigner = FakeSigner(),
            config = OtpConfig(maxSendsPerWindow = 2),
            randomCode = { "1234" },
        )
        assertEquals(OtpStatus.OK, h.send("9876543210", OtpPurpose.LOGIN).status)
        assertEquals(OtpStatus.OK, h.send("9876543210", OtpPurpose.LOGIN).status)
        assertEquals(OtpStatus.RATE_LIMITED, h.send("9876543210", OtpPurpose.LOGIN).status)
    }

    @Test
    fun verifyWithNoActiveCodeReportsNoActiveCode() = runTest {
        val r = handler(FakeOtpStore()).verify("9876543210", "1234", OtpPurpose.LOGIN)
        assertEquals(OtpStatus.NO_ACTIVE_CODE, r.status)
    }

    @Test
    fun sha256MatchesKnownVector() {
        // SHA-256("abc") — FIPS 180-4 test vector.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }
}
