package com.rate.server.routes

import com.rate.core.auth.otp.OtpPurpose
import com.rate.core.auth.otp.OtpRecord
import com.rate.core.auth.otp.OtpStore
import com.rate.core.auth.rbac.AuthRole
import com.rate.core.auth.rbac.Scope
import com.rate.core.auth.rbac.toStringSet
import com.rate.core.auth.session.SessionRecord
import com.rate.core.auth.session.SessionStore
import com.rate.core.auth.token.JwtClaims
import com.rate.core.auth.token.TokenSigner
import com.rate.core.base.time.Now
import com.rate.server.audit.ServerAuditService
import com.rate.server.email.EmailMessage
import com.rate.server.email.EmailSender
import com.rate.server.auth.requireScope
import com.rate.server.security.AppSecrets
import com.rate.server.security.PasswordHasher
import com.rate.sdk.audit.model.AuditActor
import com.rate.sdk.catalog.model.LoginAudit
import com.rate.sdk.catalog.model.User
import com.rate.sdk.catalog.repository.LoginAuditRepository
import com.rate.sdk.catalog.repository.UserRepository
import com.rate.sdk.proposal.crypto.sha256Hex
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// ── Wire shapes (the WS0b/WS1 contract — see contractNotes) ──────────────────

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class ResetRequestRequest(val email: String)

@Serializable
data class ResetConfirmRequest(val email: String, val code: String, val newPassword: String)

/**
 * Successful login/refresh result. [token] is the signed access JWT (Bearer); [refreshToken] is the
 * opaque rotation handle to present to /api/auth/refresh; [role] is the principal's [AuthRole] name;
 * [displayName] is for the console header. [expiresAt] is the access token expiry (epoch seconds).
 */
@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val role: String,
    val displayName: String,
    val userId: String,
    val email: String,
    val expiresAt: Long,
)

@Serializable
data class AuthMessageResponse(
    val status: String,
    val message: String = "",
    /** Populated ONLY in the dev profile so automated tests can read the reset code. Never in prod. */
    val devCode: String? = null,
)

@Serializable
data class AuthErrorResponse(val errorCode: String, val message: String)

private val log = LoggerFactory.getLogger("com.rate.server.routes.Auth")

// Token lifetimes — access is short (auto-refreshed), refresh = session TTL.
private val ACCESS_TTL = 1.hours
private val REFRESH_TTL = 30.days
private val RESET_CODE_TTL = 15.minutes

/**
 * Email/password authentication for the OPERATOR CONSOLE (the customer journey authenticates by
 * mobile OTP via the buy-online routes and never reaches here). The pure crypto/decision lives in the
 * core ports — this file is the Ktor glue:
 *
 *   POST /api/auth/login           {email,password}                 -> AuthResponse | 401
 *   POST /api/auth/refresh         {refreshToken}                   -> AuthResponse | 401
 *   POST /api/auth/logout          {refreshToken?} (or Bearer)      -> AuthMessageResponse
 *   POST /api/auth/reset/request   {email}                          -> AuthMessageResponse (always ok)
 *   POST /api/auth/reset/confirm   {email,code,newPassword}         -> AuthMessageResponse | 4xx
 *
 * On login the password is checked against [User.passwordHash] via [PasswordHasher]; on success a
 * signed access JWT (sub = User.id, role = User.role, scopes = AuthRole.bundleFor) is issued, an
 * opaque refresh handle is stored against a [SessionRecord] (only its SHA-256 persists), the user's
 * lastLoginAt is stamped, and a [LoginAudit] row is appended. Every attempt — success OR failure —
 * is recorded to the login-audit log.
 *
 * Password-reset uses the core [OtpStore] (6-digit code, keyed by email) + the [EmailSender]; the
 * request step NEVER reveals whether the email exists (anti-enumeration).
 */
fun Route.authRoutes(
    users: UserRepository,
    loginAudits: LoginAuditRepository,
    tokenSigner: TokenSigner,
    sessions: SessionStore,
    otpStore: OtpStore,
    emailSender: EmailSender,
    passwordHasher: PasswordHasher,
    secrets: AppSecrets,
    audit: ServerAuditService,
) {
    route("/api/auth") {

        // ── LOGIN ────────────────────────────────────────────────────────────
        post("/login") {
            val req = call.receive<LoginRequest>()
            val email = req.email.trim()
            val ip = call.clientIp()
            val ua = call.userAgent()

            val user = users.findByEmail(email)
            if (user == null) {
                loginAudits.append(LoginAudit(email = email, success = false, failureReason = LoginAudit.REASON_NO_SUCH_USER, ip = ip, userAgent = ua))
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_CREDENTIALS", "Invalid email or password."))
                return@post
            }
            if (!user.canLogin) {
                loginAudits.append(LoginAudit(email = email, success = false, failureReason = LoginAudit.REASON_DISABLED, ip = ip, userAgent = ua))
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("ACCOUNT_DISABLED", "This account is not active."))
                return@post
            }
            if (!passwordHasher.verify(req.password, user.passwordHash)) {
                loginAudits.append(LoginAudit(email = email, success = false, failureReason = LoginAudit.REASON_BAD_PASSWORD, ip = ip, userAgent = ua))
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_CREDENTIALS", "Invalid email or password."))
                return@post
            }

            // Success: stamp lastLoginAt, mint tokens + session, audit.
            val now = Now.instant()
            runCatching { users.update(user.copy(lastLoginAt = now), expectedV = user.v, actor = user.id) }
                .onFailure { log.warn("auth.login.lastLogin_stamp_failed user={} reason={}", user.id, it.message) }

            val response = issueTokens(user, tokenSigner, sessions, now, deviceId = call.deviceId())
            loginAudits.append(LoginAudit(email = email, userRef = user.id, success = true, ip = ip, userAgent = ua))
            audit.record(
                action = "auth.login",
                entity = "user",
                entityId = user.id,
                actor = AuditActor(subject = user.id, role = user.role.name, requestId = call.requestId()),
            )
            call.respond(HttpStatusCode.OK, response)
        }

        // ── REFRESH ──────────────────────────────────────────────────────────
        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val hash = sha256Hex(req.refreshToken)
            val session = sessions.findByRefreshHash(hash)
            if (session == null) {
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_REFRESH", "Session expired. Please log in again."))
                return@post
            }
            val user = users.get(session.subject)?.takeIf { it.canLogin }
            if (user == null) {
                sessions.revoke(session.id)
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("INVALID_REFRESH", "Session is no longer valid."))
                return@post
            }
            // Rotate: revoke the old session, issue a fresh access token + refresh handle.
            sessions.revoke(session.id)
            val response = issueTokens(user, tokenSigner, sessions, Now.instant(), deviceId = session.deviceId)
            call.respond(HttpStatusCode.OK, response)
        }

        // ── LOGOUT ───────────────────────────────────────────────────────────
        post("/logout") {
            val body = runCatching { call.receive<LogoutRequest>() }.getOrNull()
            val refresh = body?.refreshToken
            if (refresh != null) {
                sessions.findByRefreshHash(sha256Hex(refresh))?.let { sessions.revoke(it.id) }
            }
            // Best-effort: also revoke every active session for the bearer's subject (logout-everywhere).
            call.bearerSubject(tokenSigner)?.let { subject ->
                runCatching { sessions.revokeAllForSubject(subject) }
            }
            call.respond(HttpStatusCode.OK, AuthMessageResponse("ok", "Logged out."))
        }

        // ── RESET: request a code (anti-enumeration — always 200) ─────────────
        post("/reset/request") {
            val req = call.receive<ResetRequestRequest>()
            val email = req.email.trim()
            val user = users.findByEmail(email)?.takeIf { it.canLogin }
            var issuedCode: String? = null
            if (user != null) {
                val code = newResetCode()
                issuedCode = code
                val now = Now.instant()
                otpStore.put(
                    OtpRecord(
                        mobile = email,                 // store key is "<email>:KYC"
                        purpose = OtpPurpose.KYC,
                        codeHash = sha256Hex(code),
                        expiresAt = now + RESET_CODE_TTL,
                        attempts = 0,
                    ),
                )
                otpStore.recordSend(email, OtpPurpose.KYC, now)
                val sent = emailSender.send(
                    EmailMessage(
                        to = email,
                        subject = "Your Aegis password-reset code",
                        bodyText = "Your password-reset code is $code. It is valid for 15 minutes. If you did not request this, ignore this email.",
                        bodyHtml = "<p>Your password-reset code is <b>$code</b>.</p><p>It is valid for 15 minutes. If you did not request this, ignore this email.</p>",
                    ),
                )
                log.info("auth.reset.requested email={} emailed={}", email, sent)
            } else {
                log.info("auth.reset.requested email={} (no active user; suppressed)", email)
            }
            call.respond(
                HttpStatusCode.OK,
                AuthMessageResponse(
                    status = "ok",
                    message = "If that account exists, a reset code has been emailed.",
                    devCode = if (secrets.devProfile) issuedCode else null,
                ),
            )
        }

        // ── RESET: confirm code + set new password ────────────────────────────
        post("/reset/confirm") {
            val req = call.receive<ResetConfirmRequest>()
            val email = req.email.trim()
            if (req.newPassword.length < MIN_PASSWORD_LEN) {
                call.respond(HttpStatusCode.BadRequest, AuthErrorResponse("WEAK_PASSWORD", "Password must be at least $MIN_PASSWORD_LEN characters."))
                return@post
            }
            val record = otpStore.get(email, OtpPurpose.KYC)
            if (record == null || record.isExpired()) {
                otpStore.delete(email, OtpPurpose.KYC)
                call.respond(HttpStatusCode.Gone, AuthErrorResponse("CODE_EXPIRED", "Reset code expired or not found. Request a new one."))
                return@post
            }
            if (record.attempts >= MAX_RESET_ATTEMPTS) {
                otpStore.delete(email, OtpPurpose.KYC)
                call.respond(HttpStatusCode.TooManyRequests, AuthErrorResponse("CODE_LOCKED", "Too many attempts. Request a new reset code."))
                return@post
            }
            if (!constantTimeEquals(sha256Hex(req.code), record.codeHash)) {
                otpStore.update(record.copy(attempts = record.attempts + 1))
                call.respond(HttpStatusCode.Unauthorized, AuthErrorResponse("CODE_INVALID", "Invalid reset code."))
                return@post
            }
            // Code OK — consume it and update the user's password (no enumeration leak past this point).
            otpStore.delete(email, OtpPurpose.KYC)
            val user = users.findByEmail(email)?.takeIf { it.canLogin }
            if (user == null) {
                call.respond(HttpStatusCode.OK, AuthMessageResponse("ok", "Password updated."))
                return@post
            }
            val updated = user.copy(passwordHash = passwordHasher.hash(req.newPassword))
            runCatching { users.update(updated, expectedV = user.v, actor = user.id) }
                .onFailure {
                    log.warn("auth.reset.update_failed user={} reason={}", user.id, it.message)
                    call.respond(HttpStatusCode.Conflict, AuthErrorResponse("UPDATE_FAILED", "Could not update password; try again."))
                    return@post
                }
            // Force re-login everywhere after a password change.
            runCatching { sessions.revokeAllForSubject(user.id) }
            audit.record(
                action = "auth.password_reset",
                entity = "user",
                entityId = user.id,
                actor = AuditActor(subject = user.id, role = user.role.name, requestId = call.requestId()),
            )
            call.respond(HttpStatusCode.OK, AuthMessageResponse("ok", "Password updated. Please log in."))
        }
    }
}

/**
 * Read-only admin access to the append-only login-attempt log at `/api/admin/login-audits`.
 *
 * [LoginAudit] implements [com.rate.core.base.model.ConfigEntity] only so it can share the operator
 * console's generic descriptor/registry, but it is an APPEND-ONLY security log — its publish/draft
 * lifecycle is never exercised. Rather than expose the full mutating [adminCrudRoutes] factory, this
 * dedicated group serves only the two read shapes admins need — paged list + single get — gated by
 * the `audit.read` scope (same scope that guards the business audit feed). Attempts are written by
 * the auth flow, never via the API.
 *
 *   GET /api/admin/login-audits        — newest-first paged list (page/size/sort/filter via query)
 *   GET /api/admin/login-audits/{id}   — single attempt
 */
fun Route.loginAuditRoutes(loginAudits: LoginAuditRepository) {
    val json = com.rate.core.base.json.AppJson.json
    val pageSerializer = com.rate.core.base.model.Page.serializer(LoginAudit.serializer())

    route("/api/admin/login-audits") {
        get {
            if (!requireScope(Scope.AUDIT_READ)) return@get
            val page = loginAudits.list(call.pageRequest())
            call.respondText(
                json.encodeToString(pageSerializer, page),
                io.ktor.http.ContentType.Application.Json,
            )
        }
        get("/{id}") {
            if (!requireScope(Scope.AUDIT_READ)) return@get
            val id = call.requireParam("id")
            val row = loginAudits.get(id) ?: throw NoSuchElementException("login-audit '$id' not found")
            call.respondText(
                json.encodeToString(LoginAudit.serializer(), row),
                io.ktor.http.ContentType.Application.Json,
            )
        }
    }
}

// ── token issuance ───────────────────────────────────────────────────────────

/**
 * Mint a fresh access JWT + opaque refresh handle for [user] and persist the session (storing only
 * the SHA-256 of the refresh handle). Returns the wire [AuthResponse].
 */
private suspend fun issueTokens(
    user: User,
    tokenSigner: TokenSigner,
    sessions: SessionStore,
    now: Instant,
    deviceId: String?,
): AuthResponse {
    val accessExp = (now + ACCESS_TTL).epochSeconds
    val refreshExp = now + REFRESH_TTL
    val scopes = Scope.bundleFor(user.role).toStringSet()
    val accessToken = tokenSigner.sign(
        JwtClaims(sub = user.id, role = user.role, scopes = scopes, deviceId = deviceId, exp = accessExp),
    )
    val refreshHandle = newRefreshHandle()
    sessions.put(
        SessionRecord(
            subject = user.id,
            role = user.role,
            deviceId = deviceId,
            refreshTokenHash = sha256Hex(refreshHandle),
            expiresAt = refreshExp,
        ),
    )
    return AuthResponse(
        token = accessToken,
        refreshToken = refreshHandle,
        role = user.role.name,
        displayName = user.effectiveDisplayName,
        userId = user.id,
        email = user.email,
        expiresAt = accessExp,
    )
}

// ── boot seed ─────────────────────────────────────────────────────────────────

/**
 * Seed a single OWNER user (owner@rate.local / "owner123") when the users collection is empty, so a
 * fresh deployment is immediately loginable. Idempotent: a no-op once any user exists. Called from
 * the application boot.
 */
suspend fun seedOwnerUserIfEmpty(users: UserRepository, passwordHasher: PasswordHasher) {
    if (users.countAll() > 0) return
    val owner = User(
        email = SEED_OWNER_EMAIL,
        passwordHash = passwordHasher.hash(SEED_OWNER_PASSWORD),
        role = AuthRole.OWNER,
        firstName = "Owner",
        createdBy = "system.seed",
        updatedBy = "system.seed",
    )
    users.create(owner, actor = "system.seed")
    log.info("auth.seed: created OWNER user {} (default password — change in production)", SEED_OWNER_EMAIL)
}

// ── helpers ─────────────────────────────────────────────────────────────────

private const val SEED_OWNER_EMAIL = "owner@rate.local"
private const val SEED_OWNER_PASSWORD = "owner123"
private const val MIN_PASSWORD_LEN = 6
private const val MAX_RESET_ATTEMPTS = 5

private val secureRandom = java.security.SecureRandom()

/** 6-digit numeric reset code with a guaranteed leading non-zero digit. */
private fun newResetCode(): String = (100_000 + secureRandom.nextInt(900_000)).toString()

/** Opaque 256-bit refresh handle, URL-safe base64. */
private fun newRefreshHandle(): String {
    val bytes = ByteArray(32).also(secureRandom::nextBytes)
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}

private fun ApplicationCall.clientIp(): String? =
    request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        ?: request.headers["X-Real-IP"]?.trim()?.takeIf { it.isNotBlank() }

private fun ApplicationCall.userAgent(): String? =
    request.headers["User-Agent"]?.trim()?.takeIf { it.isNotBlank() }

private fun ApplicationCall.deviceId(): String? =
    request.headers["X-Device-Id"]?.trim()?.takeIf { it.isNotBlank() }

private fun ApplicationCall.requestId(): String? =
    request.headers["X-Request-Id"]?.trim()?.takeIf { it.isNotBlank() }

private fun ApplicationCall.bearerSubject(tokenSigner: TokenSigner): String? {
    val bearer = request.headers["Authorization"]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")
        ?.trim()
        ?: return null
    return tokenSigner.verify(bearer)?.sub
}
