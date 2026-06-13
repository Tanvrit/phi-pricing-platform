package com.rate.persistence.base

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.rating.ports.model.ProductLine
import com.rate.persistence.rating.RateTableCache

/**
 * One-shot boot sequence for the persistence layer, run once at server start (before serving
 * traffic):
 *  1. [IndexBootstrap.ensureIndexes] — idempotent index creation (lookup tuples, unique keys,
 *     TTLs, dashboard sorts);
 *  2. [RateTableCache.load] — load the ACTIVE rate-table version into the in-RAM engine
 *     snapshot so the very first quote is served without a cold DB read.
 *
 * Both steps are idempotent and safe to re-run.
 */
object MongoBootstrap {

    suspend fun run(
        db: MongoDatabase,
        rateCache: RateTableCache,
        productLine: ProductLine = ProductLine.RETAIL,
    ): String {
        IndexBootstrap.ensureIndexes(db)
        return rateCache.load(productLine)
    }
}
