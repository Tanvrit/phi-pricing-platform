package com.rate.core.network.error

/**
 * Transport-level failure taxonomy for client calls — the network-layer companion
 * to core-base's [com.rate.core.base.error.DomainError]. Where DomainError is the
 * *business* failure that flows through handlers on both sides, NetworkError is the
 * *wire/transport* failure the client observes (timeout, no connectivity, an HTTP
 * status, a body it couldn't decode).
 *
 * Every variant carries enough to render a sensible message AND to feed
 * [ErrorMapping.toDomainError] so callers that already speak DomainError can stay
 * uniform.
 */
sealed class NetworkError(open val message: String, open val cause: Throwable? = null) {

    /** No connectivity / DNS failure / connection refused or reset. */
    data class Connectivity(
        override val message: String = "Network unreachable",
        override val cause: Throwable? = null,
    ) : NetworkError(message, cause)

    /** Connect/request/socket budget exceeded. */
    data class Timeout(
        override val message: String = "Request timed out",
        override val cause: Throwable? = null,
    ) : NetworkError(message, cause)

    /** TLS/certificate-pinning failure — surfaced separately so the app can hard-fail. */
    data class Tls(
        override val message: String = "TLS/certificate validation failed",
        override val cause: Throwable? = null,
    ) : NetworkError(message, cause)

    /** 401 — credentials missing/expired/invalid. [AuthProvider.onUnauthorized] fired. */
    data class Unauthorized(
        override val message: String = "Unauthorized",
        val body: String? = null,
    ) : NetworkError(message)

    /** 403 — authenticated but lacking the required scope. [scope] parsed from the body when present. */
    data class Forbidden(
        override val message: String = "Forbidden",
        val scope: String? = null,
        val body: String? = null,
    ) : NetworkError(message)

    /** 404 — resource not found. */
    data class NotFound(
        override val message: String = "Not found",
        val body: String? = null,
    ) : NetworkError(message)

    /** 409 — optimistic-concurrency / uniqueness / draft-publish conflict. */
    data class Conflict(
        override val message: String = "Conflict",
        val body: String? = null,
    ) : NetworkError(message)

    /** 422 / 400 — server rejected the request payload. [errors] parsed from the body when present. */
    data class Validation(
        override val message: String = "Validation failed",
        val errors: List<String> = emptyList(),
        val body: String? = null,
    ) : NetworkError(message)

    /** Any non-success status not captured by a more specific variant. */
    data class Http(
        val status: Int,
        val statusText: String = "",
        val body: String? = null,
    ) : NetworkError("HTTP $status${if (statusText.isNotBlank()) " $statusText" else ""}${if (!body.isNullOrBlank()) " — $body" else ""}")

    /** Status was 2xx but the body couldn't be deserialized into the expected type. */
    data class Serialization(
        override val message: String = "Failed to decode response",
        override val cause: Throwable? = null,
    ) : NetworkError(message, cause)

    /** Catch-all for faults that don't fit the buckets above. */
    data class Unknown(
        override val message: String = "Unexpected network error",
        override val cause: Throwable? = null,
    ) : NetworkError(message, cause)
}

/** Carrier exception so a NetworkError can be thrown across a non-Result boundary. */
class NetworkException(val networkError: NetworkError) :
    RuntimeException(networkError.message, networkError.cause)
