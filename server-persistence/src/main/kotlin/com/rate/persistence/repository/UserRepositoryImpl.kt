package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.GenericConfigRepository
import com.rate.sdk.catalog.model.User
import com.rate.sdk.catalog.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.regex.Pattern

/**
 * Mongo actual for [UserRepository] — console users. Inherits the full admin-CRUD surface from
 * [GenericConfigRepository] (so `/api/admin/users` list/create/update/delete/restore/publish work
 * with zero bespoke code) and adds the two auth-specific lookups the login + bootstrap flows need.
 *
 * [findByEmail] matches the [User.email] CASE-INSENSITIVELY by an anchored, literal-quoted regex
 * so `Owner@Rate.Local` and `owner@rate.local` resolve to the same row, and only non-deleted users
 * are considered (the soft-delete guard is part of `notDeleted`).
 */
class UserRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<User>(db, CollectionNames.USERS, User::class.java, "user"),
    UserRepository {

    override suspend fun findByEmail(email: String): User? {
        val anchored = "^${Pattern.quote(email.trim())}$"
        return collection
            .find(Filters.and(Filters.regex("email", anchored, "i"), notDeleted))
            .limit(1)
            .firstOrNull()
    }

    override suspend fun countAll(): Long = count()
}
