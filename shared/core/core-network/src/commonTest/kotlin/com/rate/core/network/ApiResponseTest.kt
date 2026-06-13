package com.rate.core.network

import com.rate.core.network.error.NetworkError
import com.rate.core.network.error.NetworkException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiResponseTest {

    @Test
    fun safeCall_wraps_success() = runTest {
        val r = safeCall { 42 }
        assertTrue(r is ApiResponse.Success)
        assertEquals(42, r.getOrNull())
    }

    @Test
    fun safeCall_preserves_network_exception_error() = runTest {
        val r = safeCall<Int> { throw NetworkException(NetworkError.Forbidden(scope = "x")) }
        val f = r as ApiResponse.Failure
        assertTrue(f.error is NetworkError.Forbidden)
    }

    @Test
    fun safeCall_classifies_generic_throwable() = runTest {
        val r = safeCall<Int> { throw RuntimeException("connection refused") }
        val f = r as ApiResponse.Failure
        assertTrue(f.error is NetworkError.Connectivity)
    }

    @Test
    fun getOrThrow_throws_on_failure() = runTest {
        val r = safeCall<Int> { throw NetworkException(NetworkError.NotFound()) }
        assertFailsWith<NetworkException> { r.getOrThrow() }
    }

    @Test
    fun map_transforms_success_passes_failure() = runTest {
        assertEquals(4, safeCall { 2 }.map { it * 2 }.getOrNull())
        val f = safeCall<Int> { throw NetworkException(NetworkError.Conflict()) }.map { it * 2 }
        assertTrue(f is ApiResponse.Failure)
    }
}
