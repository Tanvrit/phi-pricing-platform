package com.rate.core.network

import com.rate.core.base.error.DomainError
import com.rate.core.network.client.TanvritClientConfig
import com.rate.core.network.error.ErrorMapping
import com.rate.core.network.error.NetworkError
import com.rate.core.network.security.CertPin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ErrorMappingTest {

    @Test
    fun status_maps_to_specific_variants() {
        assertTrue(ErrorMapping.fromStatus(401) is NetworkError.Unauthorized)
        assertTrue(ErrorMapping.fromStatus(403) is NetworkError.Forbidden)
        assertTrue(ErrorMapping.fromStatus(404) is NetworkError.NotFound)
        assertTrue(ErrorMapping.fromStatus(409) is NetworkError.Conflict)
        assertTrue(ErrorMapping.fromStatus(422) is NetworkError.Validation)
        assertTrue(ErrorMapping.fromStatus(400) is NetworkError.Validation)
        assertTrue(ErrorMapping.fromStatus(500) is NetworkError.Http)
        assertTrue(ErrorMapping.fromStatus(418) is NetworkError.Http)
    }

    @Test
    fun forbidden_body_yields_scope() {
        val err = ErrorMapping.fromStatus(403, body = """{"scope":"audit.verify","message":"nope"}""")
        val forbidden = err as NetworkError.Forbidden
        assertEquals("audit.verify", forbidden.scope)
        assertEquals("nope", forbidden.message)
    }

    @Test
    fun validation_body_yields_errors() {
        val err = ErrorMapping.fromStatus(422, body = """{"errors":["age required","si invalid"]}""")
        val v = err as NetworkError.Validation
        assertEquals(listOf("age required", "si invalid"), v.errors)
    }

    @Test
    fun plain_text_body_does_not_break_mapping() {
        val err = ErrorMapping.fromStatus(409, statusText = "Conflict", body = "version mismatch")
        assertTrue(err is NetworkError.Conflict)
    }

    @Test
    fun network_errors_bridge_to_domain_errors() {
        assertTrue(ErrorMapping.toDomainError(NetworkError.Unauthorized()) is DomainError.Unauthorized)
        assertTrue(ErrorMapping.toDomainError(NetworkError.Forbidden()) is DomainError.Forbidden)
        assertTrue(ErrorMapping.toDomainError(NetworkError.Conflict()) is DomainError.Conflict)
        assertTrue(ErrorMapping.toDomainError(NetworkError.NotFound()) is DomainError.NotFound)
        assertTrue(ErrorMapping.toDomainError(NetworkError.Validation()) is DomainError.Validation)
        assertTrue(ErrorMapping.toDomainError(NetworkError.Timeout()) is DomainError.Internal)
        assertTrue(ErrorMapping.toDomainError(NetworkError.Connectivity()) is DomainError.Internal)
    }

    @Test
    fun config_resolves_paths() {
        val cfg = TanvritClientConfig(baseUrl = "http://localhost:9090/")
        assertEquals("http://localhost:9090/api/plans", cfg.resolve("/api/plans"))
        assertEquals("http://localhost:9090/api/plans", cfg.resolve("api/plans"))
        assertEquals("https://x.com/y", cfg.resolve("https://x.com/y"))
    }

    @Test
    fun certpin_matching_and_validation() {
        val pin = CertPin(host = "api.tanvrit.com", pinSha256 = listOf("sha256/AAAA"), includeSubdomains = true)
        assertTrue(pin.matches("api.tanvrit.com"))
        assertTrue(pin.matches("edge.api.tanvrit.com"))
        assertTrue(!pin.matches("other.com"))
        assertFailsWith<IllegalArgumentException> { CertPin(host = "", pinSha256 = listOf("sha256/AAAA")) }
        assertFailsWith<IllegalArgumentException> { CertPin(host = "x.com", pinSha256 = emptyList()) }
    }
}
