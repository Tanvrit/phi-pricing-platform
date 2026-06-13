package com.rate.sdk.catalog.repository

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.repository.ConfigRepository
import com.rate.sdk.catalog.model.LoginAudit
import com.rate.sdk.catalog.model.User

/**
 * Console USER repository PORT. Extends the generic [ConfigRepository] so admins manage users
 * through the SAME admin-CRUD wiring (`/api/admin/users`) as every other config entity, plus one
 * auth-specific lookup the login flow needs: [findByEmail].
 *
 * The Mongo-backed actual lives in server-persistence; the seed-OWNER bootstrap + login/refresh/
 * reset flows in the server's AuthRoutes consume this port.
 */
interface UserRepository : ConfigRepository<User> {
    /**
     * Resolve the (single) active user by login [email], matched CASE-INSENSITIVELY. Returns null
     * when no non-deleted user with that email exists. Used by the login + reset flows.
     */
    suspend fun findByEmail(email: String): User?

    /** Count of non-deleted users — drives the boot-time "seed an OWNER if none exist" decision. */
    suspend fun countAll(): Long
}

/**
 * LOGIN-AUDIT repository PORT — append-only security log of console login attempts. [LoginAudit]
 * implements [com.rate.core.base.model.ConfigEntity] only to share the console descriptor/registry,
 * but its draft/publish lifecycle is never used; this port is deliberately NOT a [ConfigRepository]
 * and exposes only the operations the system uses: [append] (written by the auth layer on every
 * attempt) and [list]/[get] (read by admins at `/api/admin/login-audits`).
 *
 * The Mongo-backed actual lives in server-persistence.
 */
interface LoginAuditRepository {
    /** Insert one attempt record (never updated afterwards). Returns the stored row. */
    suspend fun append(audit: LoginAudit): LoginAudit

    /** Newest-first page of attempts (admin security review). Supports paging/sort/filter. */
    suspend fun list(req: PageRequest = PageRequest()): Page<LoginAudit>

    /** Single attempt by id (admin drill-down). */
    suspend fun get(id: String): LoginAudit?

    /**
     * Count failed attempts for [email] strictly after [sinceEpochMillis] — lets the auth layer
     * apply a lockout / throttle policy on repeated failures.
     */
    suspend fun countFailuresSince(email: String, sinceEpochMillis: Long): Long
}
