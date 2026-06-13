package com.rate.core.auth.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the auth feature. core-auth is a PURE contract module — it defines
 * PORTS ([com.rate.core.auth.otp.OtpStore], [com.rate.core.auth.session.SessionStore],
 * [com.rate.core.auth.token.TokenSigner]) but holds NO actuals (no crypto secret, no
 * Mongo, no JWT lib). The bindings that satisfy these ports are registered by the
 * server app's persistence/security modules at composition time.
 *
 * This module is therefore intentionally empty: it exists so the DI wiring is uniform
 * across features and so app composition can `modules(authModule, …)` without a
 * special case. Pure stateless helpers ([com.rate.core.auth.rbac.Authorization]) are
 * objects and need no DI.
 */
fun authModule(): Module = module {
    // Port → actual bindings are contributed by the app layer (server-persistence,
    // server-security). Nothing to register from the pure contract module.
}
