package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.rating.ports.model.ProductLine
import com.rate.persistence.base.CollectionNames
import com.rate.persistence.base.MongoRepository
import com.rate.sdk.ingestion.model.RateMeta
import com.rate.sdk.ingestion.repository.RateMetaRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [RateMetaRepository] — the version-pointer + SHA-dedupe store. Holds one
 * [RateMeta] per imported rate version and tracks exactly one ACTIVE version per
 * [ProductLine] (the version the engine reads).
 *
 * [activate] is the atomic pointer flip: it clears the `active` flag on every version of the
 * product line, then sets it on the requested version — so there is never more than one active
 * version per line. [findBySha] short-circuits a re-upload of a byte-identical file before any
 * rate rows are written.
 */
class RateMetaRepositoryImpl(db: MongoDatabase) :
    MongoRepository<RateMeta>(db, CollectionNames.RATE_META, RateMeta::class.java),
    RateMetaRepository {

    override suspend fun upsert(meta: RateMeta): RateMeta {
        upsertEntity(meta)
        return findByIdIncludingDeleted(meta.id) ?: meta
    }

    override suspend fun get(id: String): RateMeta? = findById(id)

    override suspend fun getActive(productLine: ProductLine): RateMeta? =
        collection.find(
            Filters.and(
                Filters.eq("productLine", productLine.name),
                Filters.eq("active", true),
                notDeleted,
            ),
        ).limit(1).firstOrNull()

    override suspend fun getByVersion(version: String, productLine: ProductLine): RateMeta? =
        collection.find(
            Filters.and(
                Filters.eq("version", version),
                Filters.eq("productLine", productLine.name),
                notDeleted,
            ),
        ).limit(1).firstOrNull()

    override suspend fun findBySha(sha: String, productLine: ProductLine): RateMeta? {
        if (sha.isBlank()) return null
        return collection.find(
            Filters.and(
                Filters.eq("sourceFileSha256", sha),
                Filters.eq("productLine", productLine.name),
                notDeleted,
            ),
        ).limit(1).firstOrNull()
    }

    override suspend fun activate(version: String, productLine: ProductLine): RateMeta? {
        val target = getByVersion(version, productLine) ?: return null
        // Clear active on all versions of this line, then set it on the target.
        raw.updateMany(
            Filters.eq("productLine", productLine.name),
            Updates.set("active", false),
        )
        raw.updateOne(byId(target.id), Updates.set("active", true))
        return findByIdIncludingDeleted(target.id)
    }

    override suspend fun listVersions(productLine: ProductLine): List<RateMeta> =
        collection.find(Filters.and(Filters.eq("productLine", productLine.name), notDeleted))
            .sort(Sorts.descending("importedAt"))
            .toList()
}
