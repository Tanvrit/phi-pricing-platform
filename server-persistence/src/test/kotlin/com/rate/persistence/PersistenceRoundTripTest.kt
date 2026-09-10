package com.rate.persistence

import com.rate.core.base.model.EntityStatus
import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.model.PlanType
import com.rate.persistence.base.IndexBootstrap
import com.rate.persistence.config.MongoClientProvider
import com.rate.persistence.config.MongoConfig
import com.rate.persistence.rating.MongoRateDataProvider
import com.rate.persistence.rating.RateTableCache
import com.rate.persistence.repository.CoverRepositoryImpl
import com.rate.persistence.repository.PlanRepositoryImpl
import com.rate.persistence.repository.RateImportRepositoryImpl
import com.rate.persistence.repository.RateMetaRepositoryImpl
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.core.rating.ports.model.Plan
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration round-trips against an ephemeral MongoDB (Testcontainers). Verifies the codec
 * registry, the generic admin-CRUD (create/update/optimistic-concurrency/list/softDelete), and
 * the rate-import → cache → provider path.
 *
 * Requires Docker, and skips itself where Docker is unavailable. That skip used to be claimed
 * here in prose and implemented nowhere: [setUp] called `container.start()` unconditionally, so
 * on a machine without a usable Docker daemon the whole class failed as `initializationError`
 * rather than skipping, and took `./gradlew build` down with it. The assumption below is what
 * this comment always said was happening.
 *
 * Note this means the round-trips do NOT run on a CI runner without Docker — they are skipped,
 * not passed. Provisioning Docker on the runner is what turns this coverage back on.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceRoundTripTest {

    private val container = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
    private lateinit var provider: MongoClientProvider

    @BeforeAll
    fun setUp() = runTest {
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable,
            "No usable Docker daemon — skipping the Testcontainers MongoDB round-trips.",
        )
        container.start()
        provider = MongoClientProvider(MongoConfig(uri = container.replicaSetUrl, database = "rate_test"))
        IndexBootstrap.ensureIndexes(provider.database)
    }

    @AfterAll
    fun tearDown() {
        // setUp may have bailed at the assumption above, in which case `provider` was never
        // assigned and the container was never started; neither is safe to touch unguarded.
        if (::provider.isInitialized) provider.close()
        if (container.isRunning) container.stop()
    }

    @Test
    fun `config CRUD round-trips with optimistic concurrency`() = runTest {
        val repo = CoverRepositoryImpl(provider.database)
        val created = repo.create(
            Cover(code = "test_cover", name = "Test Cover", sectionRef = "S1"),
            actor = "tester",
        )
        assertEquals("tester", created.createdBy)
        assertEquals(1L, created.v)

        val fetched = repo.get(created.id)
        assertNotNull(fetched)
        assertEquals("Test Cover", fetched!!.name)

        val updated = repo.update(fetched.copy(name = "Renamed"), expectedV = fetched.v, actor = "tester2")
        assertEquals("Renamed", updated.name)
        assertEquals(2L, updated.v)

        // Stale version → Conflict.
        var conflict = false
        try {
            repo.update(updated.copy(name = "X"), expectedV = 1L, actor = "tester2")
        } catch (e: com.rate.core.base.error.DomainException) {
            conflict = e.error is com.rate.core.base.error.DomainError.Conflict
        }
        assertTrue(conflict)

        val page = repo.list(PageRequest(size = 10))
        assertTrue(page.items.any { it.id == created.id })

        assertTrue(repo.softDelete(created.id, "tester2"))
        assertEquals(null, repo.get(created.id))
        assertTrue(repo.restore(created.id, "tester2"))
        assertNotNull(repo.get(created.id))
    }

    @Test
    fun `publishDraft promotes draft and retires parent`() = runTest {
        val repo = CoverRepositoryImpl(provider.database)
        val published = repo.create(Cover(code = "pub", name = "Published"), actor = "a")
        val draft = repo.create(
            Cover(code = "pub", name = "Draft edit", status = EntityStatus.DRAFT, draftOf = published.id),
            actor = "a",
        )
        val promoted = repo.publishDraft(draft.id, actor = "a")
        assertEquals(EntityStatus.PUBLISHED, promoted.status)
        assertEquals(EntityStatus.RETIRED, repo.get(published.id)?.status)
    }

    @Test
    fun `rate import feeds cache and provider`() = runTest {
        val rows = RateImportRepositoryImpl(provider.database)
        val meta = RateMetaRepositoryImpl(provider.database)
        val plansRepo = PlanRepositoryImpl(provider.database)
        plansRepo.upsertPlan(Plan(id = "PHI_BASIC", name = "Basic", planType = PlanType.DOMESTIC))

        val batch = RateRowBatch(
            version = "v-test-1",
            baseRates = listOf(
                BaseRateRow.of("v-test-1", "PHI_BASIC", "1A", "Zone 1", 26, 1_000_000L, 12345.0),
            ),
        )
        rows.bulkUpsert(batch)
        meta.upsert(
            com.rate.sdk.ingestion.model.RateMeta(version = "v-test-1", active = false),
        )
        assertNotNull(meta.activate("v-test-1"))

        val cache = RateTableCache(rows, meta, plans = plansRepo.getAllPlans())
        assertEquals("v-test-1", cache.load())
        val provider = MongoRateDataProvider(cache)
        assertEquals(
            12345.0,
            provider.getBasePremium("PHI_BASIC", "1A", "Zone 1", 26, 1_000_000L),
        )
        assertEquals("v-test-1", provider.rateTableVersion())
    }
}
