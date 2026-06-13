package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.catalog.model.LoginAudit
import com.rate.sdk.catalog.repository.LoginAuditRepository
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [LoginAuditRepository] — append-only console login-attempt log. Although
 * [LoginAudit] implements [com.rate.core.base.model.ConfigEntity] (to share the console descriptor),
 * it is append-only with no draft/publish lifecycle, so this extends the plain [MongoRepository]
 * base rather than the config-CRUD generic.
 *
 * [append] is a verbatim insert (rows are never mutated after write). [list] is newest-first by
 * [LoginAudit.at] and reuses the base [paged] machinery so admins get the same paging/sort/filter/
 * global-search semantics as every other admin list, but defaulting the sort to `at` descending.
 */
class LoginAuditRepositoryImpl(db: MongoDatabase) :
    MongoRepository<LoginAudit>(db, CollectionNames.LOGIN_AUDITS, LoginAudit::class.java),
    LoginAuditRepository {

    override suspend fun append(audit: LoginAudit): LoginAudit = insert(audit)

    override suspend fun list(req: PageRequest): Page<LoginAudit> {
        // Default to newest-attempt-first when the caller gave no explicit sort.
        val effective = if (req.sort.isEmpty()) {
            req.copy(
                sort = listOf(
                    com.rate.core.base.model.SortSpec("at", com.rate.core.base.model.SortDir.DESC),
                ),
            )
        } else {
            req
        }
        return paged(effective)
    }

    override suspend fun get(id: String): LoginAudit? = findById(id)

    override suspend fun countFailuresSince(email: String, sinceEpochMillis: Long): Long {
        // The entity's `at` is a plain (non-@Contextual) Instant, so kotlinx encodes it as an
        // ISO-8601 STRING (not a BSON Date — see BsonCodec). UTC ISO-8601 strings sort
        // chronologically, so a `$gt` against the ISO form of the cutoff is a real time comparison.
        val sinceIso = kotlinx.datetime.Instant.fromEpochMilliseconds(sinceEpochMillis).toString()
        return collection.countDocuments(
            Filters.and(
                Filters.eq("email", email),
                Filters.eq("success", false),
                Filters.gt("at", sinceIso),
                notDeleted,
            ),
        )
    }
}
