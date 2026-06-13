package com.rate.sdk.ui.operator.network

import com.rate.core.base.json.AppJson
import com.rate.core.network.client.TanvritClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Request body for `POST /api/auth/login`. The WS0a server contract is the simple
 * email + password exchange; OTP / device binding live behind the buy-online OTP
 * routes, not this operator login.
 */
@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

/**
 * Response from a successful login (`POST /api/auth/login`). Matches the WS0a server
 * `AuthResponse`: the server mints a signed access JWT ([token]) + an opaque
 * [refreshToken] rotation handle, and echoes the principal's coarse [role] (the
 * [com.rate.core.auth.rbac.AuthRole] name as a string on the wire), a human
 * [displayName] for the console chrome, the [userId]/[email], and the access-token
 * [expiresAt] (epoch SECONDS).
 *
 * [role] is kept as a raw string here (not the core-auth enum) so the network DTO
 * stays tolerant of an unknown/new role value — the app layer maps it to an
 * OperatorRole and degrades gracefully rather than failing deserialization. Every
 * non-credential field carries a default so the DTO survives a partial response
 * shape; the frozen [AppJson] also ignores unknown keys both ways.
 */
@Serializable
data class AuthResult(
    @SerialName("token") val token: String,
    @SerialName("role") val role: String,
    @SerialName("displayName") val displayName: String = "",
    @SerialName("refreshToken") val refreshToken: String = "",
    @SerialName("userId") val userId: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("expiresAt") val expiresAt: Long = 0L,
)

/** Request body for `POST /api/auth/reset/request` (forgot-password initiation). */
@Serializable
data class ResetRequest(
    @SerialName("email") val email: String,
)

/**
 * Request body for `POST /api/auth/reset/confirm` — set a new password using the
 * 6-digit [code] the server emailed (kept as `code` on the wire to match WS0a's
 * `ResetConfirmRequest`; the OTP is delivered by email, not an opaque token link).
 */
@Serializable
data class ResetConfirm(
    @SerialName("email") val email: String,
    @SerialName("code") val code: String,
    @SerialName("newPassword") val newPassword: String,
)

/**
 * Operator authentication client over the shared [TanvritClient]. Owns the
 * `/api/auth/...` routes (WS0a server contract) and their DTOs; the transport
 * (timeouts, retry, status → [com.rate.core.network.error.NetworkError] mapping,
 * the frozen [AppJson]) is the client's concern.
 *
 * On success the caller stores [AuthResult.token] into the app's
 * [com.rate.core.network.auth.TokenAuthProvider] so every later request carries
 * the Bearer header; this client itself holds no token state.
 *
 * A non-2xx (e.g. 401 on bad credentials) surfaces as a thrown
 * [com.rate.core.network.error.NetworkException] from [TanvritClient.execute],
 * which the [com.rate.sdk.ui.operator.viewmodel.LoginViewModel] catches and turns
 * into an inline error message.
 */
class AuthApi(
    private val client: TanvritClient,
    private val json: Json = AppJson.json,
) {
    /** Exchange email + password for a signed JWT + role + display name. */
    suspend fun login(email: String, password: String): AuthResult {
        val body = LoginRequest(email.trim(), password)
        val response = client.execute(HttpMethod.Post, "/api/auth/login") {
            setBody(TextContent(json.encodeToString(LoginRequest.serializer(), body), ContentType.Application.Json))
        }
        return json.decodeFromString(AuthResult.serializer(), response.bodyAsText())
    }

    /**
     * Kick off a password reset for [email]. The server emails a one-time token;
     * always 2xx (it must not leak whether the address exists), so this returns Unit
     * and the UI just shows a generic "check your email" confirmation.
     */
    suspend fun requestReset(email: String) {
        val body = ResetRequest(email.trim())
        client.execute(HttpMethod.Post, "/api/auth/reset/request") {
            setBody(TextContent(json.encodeToString(ResetRequest.serializer(), body), ContentType.Application.Json))
        }
    }

    /** Complete a reset: set [newPassword] using the 6-digit [code] the server emailed. */
    suspend fun confirmReset(email: String, code: String, newPassword: String) {
        val body = ResetConfirm(email.trim(), code.trim(), newPassword)
        client.execute(HttpMethod.Post, "/api/auth/reset/confirm") {
            setBody(TextContent(json.encodeToString(ResetConfirm.serializer(), body), ContentType.Application.Json))
        }
    }
}
