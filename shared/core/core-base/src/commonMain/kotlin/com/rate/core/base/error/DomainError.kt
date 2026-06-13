package com.rate.core.base.error

/**
 * Transport-agnostic domain error taxonomy. The server maps each variant to an
 * HTTP status; the client maps it to a user-facing message. Pure-KMP so the same
 * errors flow through handlers on both sides.
 */
sealed class DomainError(open val msg: String) {
    data class Validation(val errors: List<String>) :
        DomainError("Validation failed: ${errors.joinToString("; ")}")

    data class NotFound(val entity: String, val id: String) :
        DomainError("$entity '$id' not found")

    /** Optimistic-concurrency / unique-key / draft-publish conflict. */
    data class Conflict(override val msg: String) : DomainError(msg)

    data class Unauthorized(override val msg: String = "Unauthorized") : DomainError(msg)

    data class Forbidden(override val msg: String = "Forbidden") : DomainError(msg)

    data class Internal(override val msg: String, val cause: String? = null) : DomainError(msg)
}

class DomainException(val error: DomainError) : RuntimeException(error.msg)

fun DomainError.raise(): Nothing = throw DomainException(this)

/** Lightweight result wrapper for handler boundaries that prefer values over throws. */
sealed interface AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>
    data class Err(val error: DomainError) : AppResult<Nothing>

    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> error.raise()
    }
}
