package com.rate.core.auth.rbac

import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.base.error.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeTest {

    @Test
    fun exactScopeMatches() {
        val granted = Scope.config("plan", Scope.WRITE)
        assertTrue(granted.matches(Scope.config("plan", Scope.WRITE)))
        assertFalse(granted.matches(Scope.config("plan", Scope.READ)))
        assertFalse(granted.matches(Scope.config("cover", Scope.WRITE)))
    }

    @Test
    fun wildcardSegmentMatches() {
        val granted = Scope("config.*.read")
        assertTrue(granted.matches(Scope.config("plan", Scope.READ)))
        assertTrue(granted.matches(Scope.config("cover", Scope.READ)))
        assertFalse(granted.matches(Scope.config("plan", Scope.WRITE)))
        // segment-count mismatch must not match
        assertFalse(granted.matches(Scope("config.plan")))
    }

    @Test
    fun globalGrantMatchesEverything() {
        val admin = Scope(Scope.ALL)
        assertTrue(admin.matches(Scope.config("plan", Scope.DELETE)))
        assertTrue(admin.matches(Scope.QUOTE_RUN))
        assertTrue(admin.matches(Scope.RBAC_MANAGE))
    }

    @Test
    fun roleBundlesGrantExpectedAccess() {
        val customer = AuthRole.CUSTOMER.defaultScopes
        assertTrue(Authorization.hasScope(customer, Scope.config("plan", Scope.READ)))
        assertTrue(Authorization.hasScope(customer, Scope.QUOTE_RUN))
        assertFalse(Authorization.hasScope(customer, Scope.config("plan", Scope.WRITE)))
        assertFalse(Authorization.hasScope(customer, Scope.IMPORT_RUN))

        val business = AuthRole.BUSINESS.defaultScopes
        assertTrue(Authorization.hasScope(business, Scope.config("plan", Scope.WRITE)))
        assertTrue(Authorization.hasScope(business, Scope.IMPORT_RUN))
        assertFalse(Authorization.hasScope(business, Scope.RBAC_MANAGE))

        val admin = AuthRole.ADMIN.defaultScopes
        assertTrue(Authorization.hasScope(admin, Scope.RBAC_MANAGE))
        assertTrue(Authorization.hasScope(admin, Scope.config("zone", Scope.DELETE)))
    }

    @Test
    fun requireScopeReturnsForbiddenWhenMissing() {
        val customer = AuthRole.CUSTOMER.defaultScopes
        val ok = Authorization.requireScope(customer, Scope.QUOTE_RUN)
        assertTrue(ok is AppResult.Ok)

        val denied = Authorization.requireScope(customer, Scope.config("plan", Scope.WRITE))
        assertTrue(denied is AppResult.Err)
    }

    @Test
    fun scopeStringRoundTrip() {
        val set = AuthRole.BUSINESS.defaultScopes
        val flat = set.toStringSet()
        val back = flat.toScopeSet()
        assertEquals(set, back)
    }

    @Test
    fun otpPurposeLengths() {
        assertEquals(4, OtpPurpose.LOGIN.length)
        assertEquals(6, OtpPurpose.KYC.length)
        assertEquals(1000, OtpPurpose.LOGIN.minCodeInclusive)
        assertEquals(10000, OtpPurpose.LOGIN.maxCodeExclusive)
        assertEquals(100000, OtpPurpose.KYC.minCodeInclusive)
        assertEquals(1000000, OtpPurpose.KYC.maxCodeExclusive)
    }
}
