package com.rate.server.di

import com.rate.core.auth.otp.OtpStore
import com.rate.core.auth.session.SessionStore
import com.rate.core.auth.token.TokenSigner
import com.rate.server.audit.ServerAuditService
import com.rate.server.email.EmailSender
import com.rate.server.email.FileSystemEmailSender
import com.rate.server.security.AppSecrets
import com.rate.server.security.HmacIdempotencyHasher
import com.rate.server.security.InMemoryOtpStore
import com.rate.server.security.InMemorySessionStore
import com.rate.server.security.JwtTokenSigner
import com.rate.server.security.PasswordHasher
import com.rate.sdk.audit.event.AuditBroadcast
import com.rate.sdk.audit.handler.AuditRecorder
import com.rate.sdk.audit.repository.AuditStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-layer Koin wiring for the server SHELL. Binds:
 *  - the SECRETS ([AppSecrets]) — the only place crypto keys live;
 *  - the core auth-PORT actuals that need the secret / are app-scoped: [TokenSigner]
 *    ([JwtTokenSigner]), [OtpStore] + [SessionStore] (in-memory now; Mongo later);
 *  - the idempotency body hasher ([HmacIdempotencyHasher]);
 *  - the [EmailSender] (Phase-1 filesystem outbox);
 *  - the server-side [ServerAuditService] facade over sdk-audit's [AuditRecorder] + [AuditBroadcast].
 *
 * The repository PORT actuals (Mongo) come from `persistenceModule`; the sdk feature HANDLERS come
 * from each sdk module's Koin module — this module supplies ONLY what the app layer must own.
 */
fun serverModule(secrets: AppSecrets): Module = module {
    single { secrets }

    // ── Auth port actuals (secret-bearing / app-scoped) ─────────────────────
    single<TokenSigner> { JwtTokenSigner(signingSecret = get<AppSecrets>().jwtSigningSecret, keyId = "k1") }
    single<OtpStore> { InMemoryOtpStore() }
    single<SessionStore> { InMemorySessionStore() }

    // ── Console password hashing (PBKDF2) — app-only crypto actual ────────────
    single { PasswordHasher() }

    // ── Idempotency body hasher ──────────────────────────────────────────────
    single { HmacIdempotencyHasher(secret = get<AppSecrets>().otpTokenSecret) }

    // ── Email outbox (Phase-1) ───────────────────────────────────────────────
    single<EmailSender> { FileSystemEmailSender() }

    // ── Server audit facade over the pure sdk-audit feature ──────────────────
    single {
        ServerAuditService(
            recorder = get<AuditRecorder>(),
            store = get<AuditStore>(),
            broadcast = get<AuditBroadcast>(),
        )
    }
}
