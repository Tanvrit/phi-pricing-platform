package com.rate.persistence.base

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.rate.core.base.error.DomainError
import com.rate.core.base.error.raise
import com.rate.core.base.model.BaseDataClass
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.model.SortDir
import com.rate.core.base.time.Now
import com.rate.persistence.codec.BsonCodec
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.BsonDateTime
import org.bson.BsonString
import org.bson.BsonDocument
import org.bson.BsonInt64
import org.bson.conversions.Bson

/**
 * Abstract MongoDB base for any [BaseDataClass] entity. Centralises the cross-cutting
 * persistence concerns so every concrete repository (config or transactional) is uniform:
 *
 *  - default queries filter `isDeleted == false` (soft-delete aware), unless includeDeleted;
 *  - [upsert] stamps `updatedAt = Now` and bumps `v` at write time, while PRESERVING the
 *    original `createdAt` of an existing document (so re-saving a value object never resets
 *    its first-write instant) — done at the BSON-document level via a replace;
 *  - [findByIdAndV] / [replaceWithVersionCheck] implement optimistic concurrency on `_id`+`v`;
 *  - [paged] turns a [PageRequest] into a filtered, sorted, skip/limit query + total count.
 *
 * Subclasses supply the [collectionName] and the entity [serializer] (a typed read collection
 * is derived from the codec registry). Metadata stamping uses a parallel BSON view of the
 * same collection so the driver's kotlinx codec encodes/decodes the entity transparently.
 *
 * `T` MUST be a `@Serializable` data class implementing [BaseDataClass] with `@SerialName`
 * on every field (the `_id`/`createdAt`/`updatedAt`/`v`/`isDeleted` envelope), per the core
 * contract.
 */
abstract class MongoRepository<T : BaseDataClass>(
    protected val db: MongoDatabase,
    val collectionName: String,
    private val entityClass: Class<T>,
) {
    /** Typed collection — reads decode straight to [T] via the kotlinx BSON codec. */
    protected val collection: MongoCollection<T> = db.getCollection(collectionName, entityClass)

    /** Parallel BSON view of the SAME collection for document-level stamping/queries. */
    protected val raw: MongoCollection<BsonDocument> =
        db.getCollection(collectionName, BsonDocument::class.java)

    /** Encode an entity to its BSON document form (so we can stamp envelope fields). */
    protected fun toBson(entity: T): BsonDocument {
        val doc = BsonDocument()
        val writer = org.bson.BsonDocumentWriter(doc)
        val codec = BsonCodec.registry.get(entityClass)
        codec.encode(writer, entity, org.bson.codecs.EncoderContext.builder().build())
        return doc
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    protected fun byId(id: String): Bson = Filters.eq(BsonCodec.ID, id)

    protected val notDeleted: Bson = Filters.eq("isDeleted", false)

    protected fun activeById(id: String): Bson = Filters.and(byId(id), notDeleted)

    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Find a non-deleted entity by id, or null. */
    open suspend fun findById(id: String): T? =
        collection.find(activeById(id)).limit(1).firstOrNull()

    /** Find by id INCLUDING soft-deleted (restore/audit paths). */
    suspend fun findByIdIncludingDeleted(id: String): T? =
        collection.find(byId(id)).limit(1).firstOrNull()

    // ── Writes ──────────────────────────────────────────────────────────────────

    /**
     * Insert a fresh document verbatim (envelope already initialised by the entity defaults).
     * Use [upsert] for idempotent save-or-replace.
     */
    open suspend fun insert(entity: T): T {
        collection.insertOne(entity)
        return entity
    }

    /**
     * Idempotent save by `_id`: replace the existing document if present (preserving its
     * original `createdAt`, advancing `updatedAt = Now` and `v = v + 1`), else insert.
     *
     * Stamping happens at the BSON-document level so it is independent of whatever the caller
     * put in the entity's `updatedAt`/`v` — the database is the authority on those.
     *
     * Named `upsertEntity` (not `upsert`) to avoid a JVM signature clash with feature PORTS
     * that declare a single-arg `upsert(entity): Entity` of their own.
     */
    open suspend fun upsertEntity(entity: T): BsonDocument {
        val incoming = toBson(entity)
        val id = incoming.get(BsonCodec.ID)
        val existing = if (id != null) {
            raw.find(Filters.eq(BsonCodec.ID, id)).limit(1).firstOrNull()
        } else {
            null
        }

        val stamped = incoming.clone()
        stamped["updatedAt"] = BsonString(Now.instant().toString())
        if (existing != null) {
            // Preserve first-write instant; bump version off the STORED value.
            existing.get("createdAt")?.let { stamped["createdAt"] = it }
            val storedV = existing.get("v")?.asNumber()?.longValue() ?: 0L
            stamped["v"] = BsonInt64(storedV + 1L)
        }
        raw.findOneAndReplace(
            Filters.eq(BsonCodec.ID, id),
            stamped,
            FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
        )
        return stamped
    }

    /**
     * Optimistic replace: replace the document IFF its stored `v` equals [expectedV]. Stamps
     * `updatedAt = Now`, `v = expectedV + 1`, preserves `createdAt`. Returns the new entity, or
     * raises [DomainError.Conflict] when the version no longer matches (concurrent write) and
     * [DomainError.NotFound] when the id is absent.
     */
    suspend fun replaceWithVersionCheck(entity: T, expectedV: Long, entityName: String): T {
        val incoming = toBson(entity)
        val id = incoming.get(BsonCodec.ID)
            ?: DomainError.Validation(listOf("entity is missing _id")).raise()
        val existing = raw.find(Filters.eq(BsonCodec.ID, id)).limit(1).firstOrNull()
            ?: DomainError.NotFound(entityName, id.asString().value).raise()

        val stamped = incoming.clone()
        existing.get("createdAt")?.let { stamped["createdAt"] = it }
        stamped["updatedAt"] = BsonString(Now.instant().toString())
        stamped["v"] = BsonInt64(expectedV + 1L)

        val result = raw.findOneAndReplace(
            Filters.and(Filters.eq(BsonCodec.ID, id), Filters.eq("v", expectedV)),
            stamped,
            FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER),
        )
        if (result == null) {
            // Either the id vanished (handled above) or v drifted → conflict.
            DomainError.Conflict(
                "$entityName '${id.asString().value}' was modified concurrently " +
                    "(expected v=$expectedV)",
            ).raise()
        }
        return collection.find(Filters.eq(BsonCodec.ID, id)).limit(1).firstOrNull()
            ?: DomainError.NotFound(entityName, id.asString().value).raise()
    }

    /** Flip `isDeleted=true` (+ updatedAt). Returns true when a document was affected. */
    open suspend fun softDelete(id: String): Boolean {
        val result = raw.updateOne(
            byId(id),
            Updates.combine(
                Updates.set("isDeleted", true),
                Updates.set("updatedAt", BsonString(Now.instant().toString())),
                Updates.inc("v", 1L),
            ),
        )
        return result.modifiedCount > 0
    }

    /** Flip `isDeleted=false` (+ updatedAt). Returns true when a document was affected. */
    open suspend fun restore(id: String): Boolean {
        val result = raw.updateOne(
            byId(id),
            Updates.combine(
                Updates.set("isDeleted", false),
                Updates.set("updatedAt", BsonString(Now.instant().toString())),
                Updates.inc("v", 1L),
            ),
        )
        return result.modifiedCount > 0
    }

    // ── Paged list ──────────────────────────────────────────────────────────────

    /**
     * Filtered + sorted + paged query. [PageRequest.filter] entries are applied as case-
     * insensitive `contains` (regex) for strings; [PageRequest.sort] maps to a compound sort
     * (default `createdAt` descending when none given); [PageRequest.includeDeleted] drops the
     * soft-delete guard. Returns the page items plus a server-side total count.
     *
     * GLOBAL SEARCH: a free-text term carried under the `q` or `search` filter key is NOT treated
     * as a field-name filter — it is matched as a case-insensitive SUBSTRING against EVERY property
     * of the document (top-level and nested, strings/numbers/booleans alike). Since admin config
     * collections are small (hundreds of docs), this is done in-memory over the serialized BSON of
     * the candidate set, which is the only way to genuinely cover all properties. Pagination stays
     * correct: `total` is the filtered count and the page is sliced from the filtered set.
     */
    open suspend fun paged(
        req: PageRequest,
        extraFilter: Bson? = null,
    ): Page<T> {
        val term = searchTerm(req)
        val sort = buildSort(req)
        val skip = req.page.coerceAtLeast(0) * req.size.coerceAtLeast(1)
        val limit = req.size.coerceAtLeast(1)

        if (term == null) {
            // Fast path: pure Mongo filter/sort/skip/limit (unchanged behavior).
            val filter = buildFilter(req, extraFilter)
            val total = collection.countDocuments(filter)
            val items = collection.find(filter)
                .sort(sort)
                .skip(skip)
                .limit(limit)
                .toList()
            return Page(items = items, total = total, page = req.page, size = req.size)
        }

        // Global-search path: fetch the candidate set (respecting soft-delete + any field
        // filters + extraFilter), sort server-side, then substring-match every property in
        // memory and page the filtered result.
        val filter = buildFilter(req, extraFilter)
        val matched = collection.find(filter)
            .sort(sort)
            .toList()
            .filter { matchesAllProperties(it, term) }
        val total = matched.size.toLong()
        val items = matched.drop(skip).take(limit)
        return Page(items = items, total = total, page = req.page, size = req.size)
    }

    /**
     * Extract the global free-text search term from a [PageRequest]. Recognises both `q` and
     * `search` keys (first non-blank wins). Returns null when no global term is present.
     */
    protected fun searchTerm(req: PageRequest): String? =
        SEARCH_KEYS.firstNotNullOfOrNull { key ->
            req.filter[key]?.trim()?.takeIf { it.isNotBlank() }
        }

    /**
     * Build the Mongo-side filter: soft-delete guard + per-FIELD `contains` regexes for every
     * filter entry EXCEPT the global search keys (`q`/`search`), which are handled in-memory across
     * all properties and must not be regexed against a (non-existent) field of that name.
     */
    protected fun buildFilter(req: PageRequest, extraFilter: Bson?): Bson {
        val clauses = mutableListOf<Bson>()
        if (!req.includeDeleted) clauses += notDeleted
        req.filter.forEach { (field, value) ->
            if (field !in SEARCH_KEYS && value.isNotBlank()) {
                clauses += Filters.regex(field, java.util.regex.Pattern.quote(value), "i")
            }
        }
        extraFilter?.let { clauses += it }
        return if (clauses.isEmpty()) BsonDocument() else Filters.and(clauses)
    }

    /**
     * True when [term] (already trimmed) appears, case-insensitively, anywhere in the entity's
     * serialized BSON — i.e. as a substring of ANY of its property values (top-level or nested).
     * The entity is encoded to its document form so this covers every `@SerialName` field
     * uniformly, including numbers/booleans/enums rendered into the JSON.
     */
    protected fun matchesAllProperties(entity: T, term: String): Boolean {
        val haystack = toBson(entity).toJson()
        return haystack.contains(term, ignoreCase = true)
    }

    protected fun buildSort(req: PageRequest): Bson {
        if (req.sort.isEmpty()) return Sorts.descending("createdAt")
        return Sorts.orderBy(
            req.sort.map { spec ->
                if (spec.dir == SortDir.ASC) Sorts.ascending(spec.field)
                else Sorts.descending(spec.field)
            },
        )
    }

    /** Count of non-deleted documents (sanity / dashboards). */
    suspend fun count(): Long = collection.countDocuments(notDeleted)

    companion object {
        /** Filter keys carrying a global free-text term to match across ALL properties. */
        val SEARCH_KEYS = setOf("q", "search")
    }
}
