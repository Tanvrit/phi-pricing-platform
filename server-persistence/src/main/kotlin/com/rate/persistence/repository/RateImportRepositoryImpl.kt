package com.rate.persistence.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.persistence.base.CollectionNames
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.MemberLevelRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.sdk.ingestion.repository.RateImportRepository
import kotlinx.coroutines.flow.toList

/**
 * Mongo actual for [RateImportRepository] — writes the IMMUTABLE actuarial rate rows, one
 * collection per kind. Each row carries a deterministic composite `_id`
 * ([BaseRateRow.stableId] etc.) so re-running the same import (same version) over-writes the
 * same documents rather than duplicating them — making [bulkUpsert] idempotent. Rows are never
 * mutated in place; a new version writes a fresh set and
 * [com.rate.sdk.ingestion.repository.RateMetaRepository.activate] flips the read pointer.
 *
 * Read paths feed [com.rate.persistence.rating.RateTableCache], which loads the active
 * version's rows into the in-RAM engine snapshot at boot.
 */
class RateImportRepositoryImpl(db: MongoDatabase) : RateImportRepository {

    private val base: MongoCollection<BaseRateRow> =
        db.getCollection(CollectionNames.BASE_RATES, BaseRateRow::class.java)
    private val cover: MongoCollection<CoverRateRow> =
        db.getCollection(CollectionNames.COVER_RATES, CoverRateRow::class.java)
    private val member: MongoCollection<MemberLevelRow> =
        db.getCollection(CollectionNames.MEMBER_LEVEL_RATES, MemberLevelRow::class.java)
    private val discount: MongoCollection<DiscountRow> =
        db.getCollection(CollectionNames.DISCOUNT_RATES, DiscountRow::class.java)
    private val instalment: MongoCollection<InstalmentRow> =
        db.getCollection(CollectionNames.INSTALMENT_CONFIG, InstalmentRow::class.java)
    private val availability: MongoCollection<CoverAvailabilityRow> =
        db.getCollection(CollectionNames.COVER_AVAILABILITY, CoverAvailabilityRow::class.java)

    // Document views (for id-keyed upsert by the rows' deterministic _id).
    private val baseRaw = db.getCollection(CollectionNames.BASE_RATES, org.bson.BsonDocument::class.java)
    private val coverRaw = db.getCollection(CollectionNames.COVER_RATES, org.bson.BsonDocument::class.java)
    private val memberRaw = db.getCollection(CollectionNames.MEMBER_LEVEL_RATES, org.bson.BsonDocument::class.java)
    private val discountRaw = db.getCollection(CollectionNames.DISCOUNT_RATES, org.bson.BsonDocument::class.java)
    private val instalmentRaw = db.getCollection(CollectionNames.INSTALMENT_CONFIG, org.bson.BsonDocument::class.java)
    private val availRaw = db.getCollection(CollectionNames.COVER_AVAILABILITY, org.bson.BsonDocument::class.java)

    override suspend fun bulkUpsert(batch: RateRowBatch): Int {
        var total = 0
        total += upsertBaseRates(batch.baseRates)
        total += upsertCoverRates(batch.coverRates)
        total += upsertMemberLevelRates(batch.memberLevelRates)
        total += upsertDiscountRates(batch.discountRates)
        total += upsertInstalmentConfig(batch.instalmentConfig)
        total += upsertCoverAvailability(batch.coverAvailability)
        return total
    }

    override suspend fun upsertBaseRates(rows: List<BaseRateRow>): Int =
        upsertById(base, rows) { it.id }

    override suspend fun upsertCoverRates(rows: List<CoverRateRow>): Int =
        upsertById(cover, rows) { it.id }

    override suspend fun upsertMemberLevelRates(rows: List<MemberLevelRow>): Int =
        upsertById(member, rows) { it.id }

    override suspend fun upsertDiscountRates(rows: List<DiscountRow>): Int =
        upsertById(discount, rows) { it.id }

    override suspend fun upsertInstalmentConfig(rows: List<InstalmentRow>): Int =
        upsertById(instalment, rows) { it.id }

    override suspend fun upsertCoverAvailability(rows: List<CoverAvailabilityRow>): Int =
        upsertById(availability, rows) { it.id }

    override suspend fun deleteVersion(version: String): Int {
        var removed = 0L
        removed += baseRaw.deleteMany(Filters.eq("version", version)).deletedCount
        removed += coverRaw.deleteMany(Filters.eq("version", version)).deletedCount
        removed += memberRaw.deleteMany(Filters.eq("version", version)).deletedCount
        removed += discountRaw.deleteMany(Filters.eq("version", version)).deletedCount
        removed += instalmentRaw.deleteMany(Filters.eq("version", version)).deletedCount
        removed += availRaw.deleteMany(Filters.eq("version", version)).deletedCount
        return removed.toInt()
    }

    override suspend fun countForVersion(version: String): Int {
        var count = 0L
        count += base.countDocuments(Filters.eq("version", version))
        count += cover.countDocuments(Filters.eq("version", version))
        count += member.countDocuments(Filters.eq("version", version))
        count += discount.countDocuments(Filters.eq("version", version))
        count += instalment.countDocuments(Filters.eq("version", version))
        count += availability.countDocuments(Filters.eq("version", version))
        return count.toInt()
    }

    override suspend fun listBaseRates(version: String): List<BaseRateRow> =
        base.find(Filters.eq("version", version)).toList()

    override suspend fun listCoverRates(version: String): List<CoverRateRow> =
        cover.find(Filters.eq("version", version)).toList()

    override suspend fun listMemberLevelRates(version: String): List<MemberLevelRow> =
        member.find(Filters.eq("version", version)).toList()

    override suspend fun listDiscountRates(version: String): List<DiscountRow> =
        discount.find(Filters.eq("version", version)).toList()

    override suspend fun listInstalmentConfig(version: String): List<InstalmentRow> =
        instalment.find(Filters.eq("version", version)).toList()

    override suspend fun listCoverAvailability(version: String): List<CoverAvailabilityRow> =
        availability.find(Filters.eq("version", version)).toList()

    /**
     * Idempotent id-keyed upsert. Encodes each typed row to its BSON via the typed collection's
     * codec by replacing on `_id` (deterministic composite id), `upsert(true)`. Returns the
     * number of rows processed.
     */
    private suspend fun <R : Any> upsertById(
        typedCollection: MongoCollection<R>,
        rows: List<R>,
        idOf: (R) -> String,
    ): Int {
        for (row in rows) {
            // replaceOne on the typed collection lets the codec serialize the row; upsert by _id.
            typedCollection.replaceOne(
                Filters.eq("_id", idOf(row)),
                row,
                ReplaceOptions().upsert(true),
            )
        }
        return rows.size
    }
}
