package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.sdk.ui.operator.OperatorRole
import com.rate.sdk.ui.operator.network.AuthApi
import com.rate.sdk.ui.operator.network.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Outcome of a finished login attempt, handed to the app shell so it can store the
 * token and route into the console. Carries the raw JWT, the mapped [OperatorRole]
 * and the operator's display name for the chrome.
 */
data class LoginSuccess(
    val token: String,
    val role: OperatorRole,
    val displayName: String,
)

/**
 * Drives the operator [com.rate.sdk.ui.operator.surface.LoginSurface]: email +
 * password fields, a password-visibility toggle, inline error + loading state, and
 * a lightweight forgot-password sub-flow — all over [AuthApi].
 *
 * It owns NO token storage and NO navigation: on success it invokes [onAuthenticated]
 * with the [LoginSuccess]; the app shell ([com.rate.aegis.AegisRoot]) is responsible
 * for pushing the token into the [com.rate.core.network.auth.TokenAuthProvider] and
 * swapping the gate for the console. This keeps the operator module independent of
 * the concrete credential store.
 *
 * Role mapping is defensive: the server's role string is matched case-insensitively
 * against [OperatorRole]; an unknown/blank value degrades to the least-privileged
 * [OperatorRole.CUSTOMER] rather than throwing, so a forward-compatible server role
 * never bricks login.
 */
class LoginViewModel(
    private val auth: AuthApi,
    private val scope: CoroutineScope,
    private val onAuthenticated: (LoginSuccess) -> Unit,
) {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var passwordVisible by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // ── Forgot-password sub-flow ──────────────────────────────────────────────
    var forgotMode by mutableStateOf(false)
        private set
    var resetSent by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun togglePasswordVisible() {
        passwordVisible = !passwordVisible
    }

    /** Switch into the forgot-password panel (keeps the typed email). */
    fun openForgot() {
        forgotMode = true
        resetSent = false
        error = null
    }

    /** Return from the forgot-password panel to the login form. */
    fun closeForgot() {
        forgotMode = false
        resetSent = false
        error = null
    }

    /** Whether the login button should be enabled (basic non-blank + not in-flight). */
    val canSubmit: Boolean get() = !loading && email.isNotBlank() && password.isNotBlank()

    /** Attempt login; on success calls [onAuthenticated], on failure sets [error]. */
    fun submit() {
        if (!canSubmit) return
        loading = true
        error = null
        scope.launch {
            runCatching { auth.login(email, password) }
                .onSuccess { result ->
                    loading = false
                    onAuthenticated(
                        LoginSuccess(
                            token = result.token,
                            role = mapRole(result.role),
                            displayName = result.displayName.ifBlank { email.trim() },
                        ),
                    )
                }
                .onFailure { t ->
                    loading = false
                    error = friendlyError(t)
                }
        }
    }

    /** Send a password-reset email for the typed address; always shows a generic confirmation. */
    fun requestReset() {
        if (loading || email.isBlank()) return
        loading = true
        error = null
        scope.launch {
            runCatching { auth.requestReset(email) }
                .onSuccess {
                    loading = false
                    resetSent = true
                }
                .onFailure { t ->
                    loading = false
                    error = friendlyError(t)
                }
        }
    }

    private fun friendlyError(t: Throwable): String {
        val raw = t.message ?: t::class.simpleName ?: "Login failed"
        // The 401 path comes through as an Unauthorized network error; give the
        // operator the actionable message rather than the transport's wording.
        return if (raw.contains("401") || raw.contains("Unauthorized", ignoreCase = true)) {
            "Incorrect email or password."
        } else {
            raw
        }
    }

    private companion object {
        /** Map the server role string onto [OperatorRole], defaulting to the least-privileged role. */
        fun mapRole(raw: String): OperatorRole =
            OperatorRole.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: OperatorRole.CUSTOMER
    }
}
