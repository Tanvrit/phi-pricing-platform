package com.rate.sdk.catalog.handler

import com.rate.core.base.error.DomainError
import com.rate.core.base.error.raise
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.repository.ConfigRepository
import com.rate.core.money.Money
import com.rate.core.regulatory.Zone
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Annexure
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.RateKind
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Tenure
import com.rate.sdk.catalog.repository.AddOnRepository
import com.rate.sdk.catalog.repository.AnnexureRepository
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.catalog.repository.CriticalIllnessListRepository
import com.rate.sdk.catalog.repository.PincodeZoneRepository
import com.rate.sdk.catalog.repository.PincodeZoneResolver
import com.rate.sdk.catalog.repository.ProductRepository
import com.rate.sdk.catalog.repository.SectionRepository
import com.rate.sdk.catalog.repository.TenureRepository
import com.rate.sdk.catalog.repository.ZoneResolution
import com.rate.sdk.catalog.seed.CatalogSeed

/**
 * Admin-CRUD orchestration for the catalog: validates every entity before write, delegates to
 * the per-entity [ConfigRepository] ports, resolves zones from pincodes, and seeds the relocated
 * fallback defaults on first boot.
 *
 * Validation is pure (no IO) and returns [DomainError.Validation] (raised) on failure, so the
 * server maps it to HTTP 422 and the client to a field-level message. The handler is
 * transport-agnostic and pure-KMP — the same instance backs the server routes and (via the
 * read ports) the client.
 */
class CatalogHandler(
    val products: ProductRepository,
    val sections: SectionRepository,
    val covers: CoverRepository,
    val criticalIllnessLists: CriticalIllnessListRepository,
    val annexures: AnnexureRepository,
    val addOns: AddOnRepository,
    val tenures: TenureRepository,
    val pincodeZones: PincodeZoneRepository,
    val zoneResolver: PincodeZoneResolver,
) {
    // ── Zone resolution ──────────────────────────────────────────────────────
    suspend fun resolveZone(pincode: String): ZoneResolution = zoneResolver.resolve(pincode)
    suspend fun zoneFor(pincode: String): Zone = zoneResolver.resolve(pincode).zone

    // ── Generic validated CRUD over any entity (used by routes) ───────────────
    suspend fun <T : ConfigEntity> list(repo: ConfigRepository<T>, req: PageRequest): Page<T> =
        repo.list(req)

    suspend fun <T : ConfigEntity> get(repo: ConfigRepository<T>, entityName: String, id: String): T =
        repo.get(id) ?: DomainError.NotFound(entityName, id).raise()

    /** Validate then create. [validate] supplies the per-type rule. */
    suspend fun <T : ConfigEntity> create(
        repo: ConfigRepository<T>,
        entity: T,
        actor: String?,
        validate: (T) -> List<String>,
    ): T {
        val errors = validate(entity)
        if (errors.isNotEmpty()) DomainError.Validation(errors).raise()
        return repo.create(entity, actor)
    }

    /** Validate then optimistic-update. */
    suspend fun <T : ConfigEntity> update(
        repo: ConfigRepository<T>,
        entity: T,
        expectedV: Long,
        actor: String?,
        validate: (T) -> List<String>,
    ): T {
        val errors = validate(entity)
        if (errors.isNotEmpty()) DomainError.Validation(errors).raise()
        return repo.update(entity, expectedV, actor)
    }

    suspend fun <T : ConfigEntity> softDelete(repo: ConfigRepository<T>, id: String, actor: String?): Boolean =
        repo.softDelete(id, actor)

    suspend fun <T : ConfigEntity> restore(repo: ConfigRepository<T>, id: String, actor: String?): Boolean =
        repo.restore(id, actor)

    suspend fun <T : ConfigEntity> publish(repo: ConfigRepository<T>, draftId: String, actor: String?): T =
        repo.publishDraft(draftId, actor)

    // ── Typed convenience wrappers (wire the right validator automatically) ───
    suspend fun createProduct(p: Product, actor: String? = null): Product =
        create(products, p, actor, CatalogValidation::product)

    suspend fun updateProduct(p: Product, expectedV: Long, actor: String? = null): Product =
        update(products, p, expectedV, actor, CatalogValidation::product)

    suspend fun createSection(s: Section, actor: String? = null): Section =
        create(sections, s, actor, CatalogValidation::section)

    suspend fun createCover(c: Cover, actor: String? = null): Cover =
        create(covers, c, actor, CatalogValidation::cover)

    suspend fun updateCover(c: Cover, expectedV: Long, actor: String? = null): Cover =
        update(covers, c, expectedV, actor, CatalogValidation::cover)

    suspend fun createCriticalIllnessList(l: CriticalIllnessList, actor: String? = null): CriticalIllnessList =
        create(criticalIllnessLists, l, actor, CatalogValidation::ciList)

    suspend fun createAnnexure(a: Annexure, actor: String? = null): Annexure =
        create(annexures, a, actor, CatalogValidation::annexure)

    suspend fun createAddOn(a: AddOn, actor: String? = null): AddOn =
        create(addOns, a, actor, CatalogValidation::addOn)

    suspend fun createTenure(t: Tenure, actor: String? = null): Tenure =
        create(tenures, t, actor, CatalogValidation::tenure)

    suspend fun createPincodeZone(p: PincodeZone, actor: String? = null): PincodeZone =
        create(pincodeZones, p, actor, CatalogValidation::pincodeZone)

    // ── Seeding ────────────────────────────────────────────────────────────
    /**
     * Idempotently seed each empty collection with the relocated fallback defaults. Safe to call
     * on every boot — [ConfigRepository.bulkUpsert] is an upsert-by-id, and we only seed when a
     * collection has no rows so admin edits are never overwritten. Returns total rows written.
     */
    suspend fun seedDefaults(actor: String? = "seed"): Int {
        var written = 0
        if (covers.list(PageRequest(size = 1)).total == 0L)
            written += covers.bulkUpsert(CatalogSeed.covers(), actor)
        if (pincodeZones.list(PageRequest(size = 1)).total == 0L)
            written += pincodeZones.bulkUpsert(CatalogSeed.pincodeZones(), actor)
        if (addOns.list(PageRequest(size = 1)).total == 0L)
            written += addOns.bulkUpsert(CatalogSeed.addOns(), actor)
        if (tenures.list(PageRequest(size = 1)).total == 0L)
            written += tenures.bulkUpsert(CatalogSeed.tenures(), actor)
        if (criticalIllnessLists.list(PageRequest(size = 1)).total == 0L)
            written += criticalIllnessLists.bulkUpsert(CatalogSeed.criticalIllnessLists(), actor)
        return written
    }
}

/** Pure validation rules — one function per entity, returning the list of field errors. */
internal object CatalogValidation {

    fun product(p: Product): List<String> = buildList {
        if (p.code.isBlank()) add("Product code is required")
        if (p.name.isBlank()) add("Product name is required")
    }

    fun section(s: Section): List<String> = buildList {
        if (s.sectionNumber.isBlank()) add("Section number is required")
        if (s.name.isBlank()) add("Section name is required")
    }

    fun cover(c: Cover): List<String> = buildList {
        if (c.code.isBlank()) add("Cover code is required")
        if (c.name.isBlank()) add("Cover name is required")
        if (c.minSumInsured.isNegative()) add("minSumInsured cannot be negative")
        if (c.maxSumInsured.isNegative()) add("maxSumInsured cannot be negative")
        if (positiveBoth(c.minSumInsured, c.maxSumInsured) && c.minSumInsured > c.maxSumInsured)
            add("minSumInsured must be ≤ maxSumInsured")
        // PERCENT_MULTIPLIER / UW_LOADING / POST covers must declare an accumulation base.
        if (c.rateKind in ACCUM_KINDS && c.accumBase.isEmpty())
            add("${c.rateKind} cover '${c.code}' requires a non-empty accumBase")
    }

    fun ciList(l: CriticalIllnessList): List<String> = buildList {
        if (l.listCode.isBlank()) add("CI list code is required")
        if (l.size != l.items.size) add("size (${l.size}) must equal items count (${l.items.size})")
    }

    fun annexure(a: Annexure): List<String> = buildList {
        if (a.annexureCode.isBlank()) add("Annexure code is required")
        if (a.title.isBlank()) add("Annexure title is required")
    }

    fun addOn(a: AddOn): List<String> = buildList {
        if (a.code.isBlank()) add("Add-on code is required")
        if (a.name.isBlank()) add("Add-on name is required")
        a.items.forEachIndexed { i, it ->
            if (it.coverCode.isBlank()) add("Add-on item #$i is missing a coverCode")
        }
    }

    fun tenure(t: Tenure): List<String> = buildList {
        if (t.years <= 0) add("Tenure years must be positive")
        if (t.multiTenureDiscount < 0.0 || t.multiTenureDiscount > 1.0)
            add("multiTenureDiscount must be between 0.0 and 1.0")
    }

    fun pincodeZone(p: PincodeZone): List<String> = buildList {
        if (p.prefix.isBlank() || !p.prefix.all { it.isDigit() })
            add("Pincode prefix must be a non-empty numeric string")
        if (p.prefixLength != p.prefix.length)
            add("prefixLength (${p.prefixLength}) must equal prefix length (${p.prefix.length})")
    }

    private val ACCUM_KINDS = setOf(RateKind.PERCENT_MULTIPLIER, RateKind.UW_LOADING, RateKind.POST)
    private fun positiveBoth(a: Money, b: Money) = a.paise > 0 && b.paise > 0
}
