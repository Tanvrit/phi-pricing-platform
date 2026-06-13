package com.rate.sdk.ingestion

import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.ingestion.handler.RateImportHandler
import com.rate.sdk.ingestion.model.RateMeta
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.MemberLevelRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.sdk.ingestion.repository.RateImportRepository
import com.rate.sdk.ingestion.repository.RateMetaRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateImportTest {

    // ── Deterministic composite ids → idempotent upsert ─────────────────────
    @Test
    fun rateRowIdsAreDeterministic() {
        val a = BaseRateRow.of("v1", "PHI_BASIC", "2A", "Zone 1", 26, 1_000_000, 12345.0)
        val b = BaseRateRow.of("v1", "PHI_BASIC", "2A", "Zone 1", 26, 1_000_000, 99999.0)
        assertEquals(a.id, b.id) // same key → same id regardless of value
        val c = BaseRateRow.of("v1", "PHI_BASIC", "2A", "Zone 2", 26, 1_000_000, 12345.0)
        assertTrue(a.id != c.id) // different zone → different id
    }

    @Test
    fun coverRowNullSegmentsDoNotCollide() {
        val withParam = CoverRateRow.of("v1", "co_pay", rate = 0.1, param1 = "0.05")
        val withoutParam = CoverRateRow.of("v1", "co_pay", rate = 0.1)
        assertTrue(withParam.id != withoutParam.id)
        // null param1 vs blank param2 must not collide.
        val a = CoverRateRow.of("v1", "x", rate = 1.0, param1 = "a", param2 = null)
        val b = CoverRateRow.of("v1", "x", rate = 1.0, param1 = null, param2 = "a")
        assertTrue(a.id != b.id)
    }

    @Test
    fun batchCountsAndTotal() {
        val batch = RateRowBatch(
            version = "v1",
            baseRates = listOf(BaseRateRow.of("v1", "P", "2A", "Z1", 26, 1_000_000, 1.0)),
            coverRates = listOf(CoverRateRow.of("v1", "c", 0.1)),
            memberLevelRates = listOf(MemberLevelRow.of("v1", "m", 0.2)),
            discountRates = listOf(DiscountRow.of("v1", "d", 0.1)),
            instalmentConfig = listOf(InstalmentRow.of("v1", Tenure.ONE_YEAR, Tenure.ONE_YEAR, PaymentMode.ANNUAL, 1)),
            coverAvailability = listOf(CoverAvailabilityRow.of("v1", "P", "c")),
        )
        assertEquals(6, batch.totalRows)
        assertEquals(1, batch.counts()["baseRates"])
    }

    @Test
    fun instalmentComputeMatchesMonolithRules() {
        assertEquals(1, InstalmentRow.computeCount(Tenure.FIVE_YEARS, PaymentMode.SINGLE_PREMIUM))
        assertEquals(3, InstalmentRow.computeCount(Tenure.THREE_YEARS, PaymentMode.ANNUAL))
        assertEquals(6, InstalmentRow.computeCount(Tenure.THREE_YEARS, PaymentMode.HALF_YEARLY))
        assertEquals(24, InstalmentRow.computeCount(Tenure.TWO_YEARS, PaymentMode.MONTHLY))
    }

    // ── Handler flow over in-memory fakes ───────────────────────────────────
    @Test
    fun importWritesRowsAndActivatesVersion() = runTest {
        val rows = FakeRateImportRepository()
        val meta = FakeRateMetaRepository()
        val handler = RateImportHandler(rows, meta)

        val batch = RateRowBatch(
            version = "v13.0",
            baseRates = listOf(BaseRateRow.of("v13.0", "PHI_BASIC", "2A", "Zone 1", 26, 1_000_000, 12345.0)),
        )
        val summary = handler.importBatch(batch, sha = "deadbeef", sourceFileName = "rates.xlsx")
        assertTrue(summary.ok)
        assertFalse(summary.deduped)
        assertEquals("v13.0", summary.version)
        assertEquals(1, rows.countForVersion("v13.0"))

        val active = meta.getActive()
        assertNotNull(active)
        assertEquals("v13.0", active.version)
        assertTrue(active.active)
    }

    @Test
    fun reuploadSameShaIsDedupedNoOp() = runTest {
        val rows = FakeRateImportRepository()
        val meta = FakeRateMetaRepository()
        val handler = RateImportHandler(rows, meta)
        val batch = RateRowBatch(version = "v1", baseRates = listOf(BaseRateRow.of("v1", "P", "2A", "Z1", 26, 1_000_000, 1.0)))

        handler.importBatch(batch, sha = "samehash")
        val rowsAfterFirst = rows.totalRows()
        val second = handler.importBatch(batch.copy(version = "v2"), sha = "samehash")
        assertTrue(second.deduped)
        assertEquals(rowsAfterFirst, rows.totalRows()) // no new rows written
    }

    @Test
    fun activatingSecondVersionRetiresFirst() = runTest {
        val rows = FakeRateImportRepository()
        val meta = FakeRateMetaRepository()
        val handler = RateImportHandler(rows, meta)
        handler.importBatch(RateRowBatch(version = "v1"), sha = "h1")
        handler.importBatch(RateRowBatch(version = "v2"), sha = "h2")
        assertEquals("v2", meta.getActive()?.version)
        assertFalse(meta.getByVersion("v1")!!.active)
    }

    @Test
    fun blankVersionFails() = runTest {
        val handler = RateImportHandler(FakeRateImportRepository(), FakeRateMetaRepository())
        val summary = handler.importBatch(RateRowBatch(version = ""), sha = "h")
        assertFalse(summary.ok)
        assertTrue(summary.errors.isNotEmpty())
    }

    // ── In-memory fakes (the Mongo actuals live in server-persistence) ──────
    private class FakeRateImportRepository : RateImportRepository {
        private val base = LinkedHashMap<String, BaseRateRow>()
        private val cover = LinkedHashMap<String, CoverRateRow>()
        private val member = LinkedHashMap<String, MemberLevelRow>()
        private val discount = LinkedHashMap<String, DiscountRow>()
        private val instalment = LinkedHashMap<String, InstalmentRow>()
        private val avail = LinkedHashMap<String, CoverAvailabilityRow>()

        fun totalRows() = base.size + cover.size + member.size + discount.size + instalment.size + avail.size

        override suspend fun bulkUpsert(batch: RateRowBatch): Int =
            upsertBaseRates(batch.baseRates) + upsertCoverRates(batch.coverRates) +
                upsertMemberLevelRates(batch.memberLevelRates) + upsertDiscountRates(batch.discountRates) +
                upsertInstalmentConfig(batch.instalmentConfig) + upsertCoverAvailability(batch.coverAvailability)

        override suspend fun upsertBaseRates(rows: List<BaseRateRow>): Int { rows.forEach { base[it.id] = it }; return rows.size }
        override suspend fun upsertCoverRates(rows: List<CoverRateRow>): Int { rows.forEach { cover[it.id] = it }; return rows.size }
        override suspend fun upsertMemberLevelRates(rows: List<MemberLevelRow>): Int { rows.forEach { member[it.id] = it }; return rows.size }
        override suspend fun upsertDiscountRates(rows: List<DiscountRow>): Int { rows.forEach { discount[it.id] = it }; return rows.size }
        override suspend fun upsertInstalmentConfig(rows: List<InstalmentRow>): Int { rows.forEach { instalment[it.id] = it }; return rows.size }
        override suspend fun upsertCoverAvailability(rows: List<CoverAvailabilityRow>): Int { rows.forEach { avail[it.id] = it }; return rows.size }

        override suspend fun deleteVersion(version: String): Int {
            val before = totalRows()
            base.values.removeAll { it.version == version }
            cover.values.removeAll { it.version == version }
            member.values.removeAll { it.version == version }
            discount.values.removeAll { it.version == version }
            instalment.values.removeAll { it.version == version }
            avail.values.removeAll { it.version == version }
            return before - totalRows()
        }

        override suspend fun countForVersion(version: String): Int =
            listBaseRates(version).size + listCoverRates(version).size + listMemberLevelRates(version).size +
                listDiscountRates(version).size + listInstalmentConfig(version).size + listCoverAvailability(version).size

        override suspend fun listBaseRates(version: String) = base.values.filter { it.version == version }
        override suspend fun listCoverRates(version: String) = cover.values.filter { it.version == version }
        override suspend fun listMemberLevelRates(version: String) = member.values.filter { it.version == version }
        override suspend fun listDiscountRates(version: String) = discount.values.filter { it.version == version }
        override suspend fun listInstalmentConfig(version: String) = instalment.values.filter { it.version == version }
        override suspend fun listCoverAvailability(version: String) = avail.values.filter { it.version == version }
    }

    private class FakeRateMetaRepository : RateMetaRepository {
        private val byId = LinkedHashMap<String, RateMeta>()

        override suspend fun upsert(meta: RateMeta): RateMeta { byId[meta.id] = meta; return meta }
        override suspend fun get(id: String): RateMeta? = byId[id]
        override suspend fun getActive(productLine: ProductLine): RateMeta? =
            byId.values.firstOrNull { it.productLine == productLine && it.active }
        override suspend fun getByVersion(version: String, productLine: ProductLine): RateMeta? =
            byId.values.firstOrNull { it.productLine == productLine && it.version == version }
        override suspend fun findBySha(sha: String, productLine: ProductLine): RateMeta? =
            byId.values.firstOrNull { it.productLine == productLine && it.sourceFileSha256 == sha && sha.isNotBlank() }
        override suspend fun activate(version: String, productLine: ProductLine): RateMeta? {
            val target = getByVersion(version, productLine) ?: return null
            byId.values.filter { it.productLine == productLine }.forEach { byId[it.id] = it.copy(active = false) }
            val activated = byId[target.id]!!.copy(active = true)
            byId[target.id] = activated
            return activated
        }
        override suspend fun listVersions(productLine: ProductLine): List<RateMeta> =
            byId.values.filter { it.productLine == productLine }.sortedByDescending { it.importedAt }
    }

    @Test
    fun unknownVersionActivationReturnsNull() = runTest {
        val handler = RateImportHandler(FakeRateImportRepository(), FakeRateMetaRepository())
        assertNull(handler.activateVersion("nope"))
    }
}
