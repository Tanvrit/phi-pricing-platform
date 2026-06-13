package com.rate.core.network

import com.rate.core.base.error.DomainError
import com.rate.core.network.error.ErrorMapping
import com.rate.core.network.error.NetworkError
import com.rate.core.network.error.NetworkException

/**
 * Result of a client call: either a decoded [Success] value or a [Failure]
 * carrying a [NetworkError]. Mirrors core-base's `AppResult` but at the transport
 * boundary, so callers can choose between value-style handling here and the
 * exception-style of throwing clients.
 */
sealed interface ApiResponse<out T> {
    data class Success<out T>(val value: T) : ApiResponse<T>
    data class Failure(val error: NetworkError) : ApiResponse<Nothing>

    val isSuccess: Boolean get() = this is Success

    /** Value on success, null on failure. */
    fun getOrNull(): T? = (this as? Success)?.value

    /** Value on success, or throw a [NetworkException] carrying the error. */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw NetworkException(error)
    }

    /** Bridge to core-base — handy where the surrounding code speaks DomainError. */
    fun errorAsDomain(): DomainError? = (this as? Failure)?.let { ErrorMapping.toDomainError(it.error) }
}

inline fun <T, R> ApiResponse<T>.map(transform: (T) -> R): ApiResponse<R> = when (this) {
    is ApiResponse.Success -> ApiResponse.Success(transform(value))
    is ApiResponse.Failure -> this
}

inline fun <T> ApiResponse<T>.onSuccess(action: (T) -> Unit): ApiResponse<T> {
    if (this is ApiResponse.Success) action(value)
    return this
}

inline fun <T> ApiResponse<T>.onFailure(action: (NetworkError) -> Unit): ApiResponse<T> {
    if (this is ApiResponse.Failure) action(error)
    return this
}

/** Value on success, [fallback] on failure. */
fun <T> ApiResponse<T>.getOrElse(fallback: T): T = getOrNull() ?: fallback

/**
 * The call boundary: run [block] (one HTTP call that returns a decoded value) and
 * never let a transport exception escape — any throwable is classified into a
 * [NetworkError] via [ErrorMapping]. A thrown [NetworkException] (e.g. raised by
 * the client after mapping a non-2xx response or firing onUnauthorized) passes its
 * wrapped error straight through.
 *
 * The client's request methods raise non-2xx statuses as [NetworkException] so
 * status-aware errors are preserved here rather than collapsing to Unknown.
 */
suspend fun <T> safeCall(block: suspend () -> T): ApiResponse<T> =
    try {
        ApiResponse.Success(block())
    } catch (e: NetworkException) {
        ApiResponse.Failure(e.networkError)
    } catch (t: Throwable) {
        ApiResponse.Failure(ErrorMapping.fromThrowable(t))
    }
