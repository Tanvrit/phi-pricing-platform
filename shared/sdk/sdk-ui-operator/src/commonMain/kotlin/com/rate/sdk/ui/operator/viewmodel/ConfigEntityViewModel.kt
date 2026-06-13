package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.base.model.SortDir
import com.rate.core.base.model.SortSpec
import com.rate.sdk.ui.operator.model.EntityDescriptor
import com.rate.sdk.ui.operator.model.FormBuffer
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.registry.TypedEntityDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlin.jvm.JvmName

/**
 * The ONE generic admin-CRUD ViewModel. It drives [com.rate.sdk.ui.operator.screen.ConfigEntityScreen]
 * for ANY admin entity by holding the [descriptor] (typed-erased to [EntityDescriptor] for the UI,
 * typed for the API encode/decode) and the [ConfigAdminApi] transport.
 *
 * State is plain Compose snapshot state so the screen recomposes on mutation; the VM is created
 * per-entity by the screen via [rememberCoroutineScope] (no Android ViewModel dependency — pure-KMP).
 *
 * Every mutation is optimistic-version-aware (passes the loaded `v` on update) and stamps the
 * [actor] into the `X-Aegis-Actor` header so the server's audit chain attributes the change.
 */
class ConfigEntityViewModel(
    private val descriptor: TypedEntityDescriptor<*>,
    private val api: ConfigAdminApi,
    private val scope: CoroutineScope,
    private val actor: String?,
) {
    val spec: EntityDescriptor get() = descriptor

    // ── List state ──────────────────────────────────────────────────────────
    var items by mutableStateOf<List<Any>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var search by mutableStateOf("")
        private set
    var includeDeleted by mutableStateOf(false)
        private set
    var page by mutableStateOf(0)
        private set
    val pageSize = 50

    // ── Editor state ──────────────────────────────────────────────────────────
    /** The entity currently being edited (null = no form open). */
    var editing by mutableStateOf<Any?>(null)
        private set
    /** True when [editing] is a brand-new (unsaved) instance. */
    var isNew by mutableStateOf(false)
        private set
    /** The mutable form buffer the inputs bind to. */
    var buffer by mutableStateOf<FormBuffer>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set
    var formErrors by mutableStateOf<List<String>>(emptyList())
        private set

    /** Debounce handle for live search — a new keystroke cancels the pending refresh. */
    private var searchJob: Job? = null

    /**
     * Live search setter. Updates the query on every keystroke and, after a short debounce,
     * resets to the first page and refreshes the list — so results update in real time as the
     * user types, with NO Search button required. The backend matches the term as a case-
     * insensitive substring across ALL properties of the entity.
     */
    @JvmName("applySearch") fun setSearch(value: String) {
        search = value
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            page = 0
            refresh()
        }
    }

    @JvmName("applyIncludeDeleted") fun setIncludeDeleted(value: Boolean) { includeDeleted = value; refresh() }

    private fun pageRequest() = PageRequest(
        page = page,
        size = pageSize,
        sort = listOf(SortSpec("updatedAt", SortDir.DESC)),
        filter = if (search.isBlank()) emptyMap() else mapOf("q" to search.trim()),
        includeDeleted = includeDeleted,
    )

    /** (Re)load the current page. */
    @Suppress("UNCHECKED_CAST")
    fun refresh() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                api.list(
                    descriptor.id,
                    descriptor.serializer as KSerializer<ConfigEntity>,
                    pageRequest(),
                )
            }.onSuccess { p: Page<*> ->
                items = p.items.filterNotNull()
                total = p.total
            }.onFailure { t ->
                error = t.message ?: t::class.simpleName ?: "Failed to load ${descriptor.plural}"
            }
            loading = false
        }
    }

    fun nextPage() { if ((page + 1).toLong() * pageSize < total) { page += 1; refresh() } }
    fun prevPage() { if (page > 0) { page -= 1; refresh() } }

    /** Immediate search (e.g. Search button / Enter) — cancels any pending debounce. */
    fun applySearch() { searchJob?.cancel(); page = 0; refresh() }

    // ── Editor actions ──────────────────────────────────────────────────────
    fun startCreate() {
        val fresh = descriptor.newInstance()
        editing = fresh
        isNew = true
        buffer = descriptor.read(fresh)
        formErrors = emptyList()
    }

    fun startEdit(entity: Any) {
        editing = entity
        isNew = false
        buffer = descriptor.read(entity)
        formErrors = emptyList()
    }

    fun closeEditor() {
        editing = null
        isNew = false
        buffer = emptyMap()
        formErrors = emptyList()
    }

    fun setField(key: String, value: String) {
        buffer = buffer + (key to value)
    }

    /** Validate + create or update the editing entity, then refresh the list. */
    @Suppress("UNCHECKED_CAST")
    fun save(onDone: (Boolean) -> Unit = {}) {
        val base = editing ?: return
        val errs = descriptor.validate(buffer)
        if (errs.isNotEmpty()) { formErrors = errs; return }
        val toPersist = try {
            descriptor.applyEdit(base, buffer)
        } catch (e: IllegalArgumentException) {
            formErrors = listOf(e.message ?: "Invalid input")
            return
        }
        val ser = descriptor.serializer as KSerializer<ConfigEntity>
        val entity = toPersist as ConfigEntity
        saving = true
        formErrors = emptyList()
        scope.launch {
            runCatching {
                if (isNew) api.create(descriptor.id, ser, entity, actor)
                else api.update(descriptor.id, ser, entity, descriptor.versionOf(base), actor)
            }.onSuccess {
                saving = false
                closeEditor()
                refresh()
                onDone(true)
            }.onFailure { t ->
                saving = false
                formErrors = listOf(t.message ?: "Save failed")
                onDone(false)
            }
        }
    }

    fun softDelete(entity: Any, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            runCatching { api.softDelete(descriptor.id, descriptor.idOf(entity), actor) }
                .onSuccess { ok -> refresh(); onDone(ok) }
                .onFailure { t -> error = t.message; onDone(false) }
        }
    }

    fun restore(entity: Any, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            runCatching { api.restore(descriptor.id, descriptor.idOf(entity), actor) }
                .onSuccess { ok -> refresh(); onDone(ok) }
                .onFailure { t -> error = t.message; onDone(false) }
        }
    }

    /**
     * Soft-delete a batch of entities (the table's current selection), refreshing
     * the list once at the end rather than per row. [onDone] reports how many of
     * [entities] were deleted successfully so the caller can toast a precise
     * result ("3 of 4 deleted"). Failures are tolerated per-row — one bad delete
     * does not abort the rest.
     */
    fun bulkSoftDelete(entities: Collection<Any>, onDone: (succeeded: Int, total: Int) -> Unit = { _, _ -> }) {
        val targets = entities.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        scope.launch {
            var ok = 0
            for (e in targets) {
                runCatching { api.softDelete(descriptor.id, descriptor.idOf(e), actor) }
                    .onSuccess { if (it) ok += 1 }
                    .onFailure { t -> error = t.message }
            }
            refresh()
            onDone(ok, targets.size)
        }
    }

    /**
     * Restore a batch of soft-deleted entities; mirror of [bulkSoftDelete].
     */
    fun bulkRestore(entities: Collection<Any>, onDone: (succeeded: Int, total: Int) -> Unit = { _, _ -> }) {
        val targets = entities.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        scope.launch {
            var ok = 0
            for (e in targets) {
                runCatching { api.restore(descriptor.id, descriptor.idOf(e), actor) }
                    .onSuccess { if (it) ok += 1 }
                    .onFailure { t -> error = t.message }
            }
            refresh()
            onDone(ok, targets.size)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun publishDraft(entity: Any, onDone: (Boolean) -> Unit = {}) {
        val ser = descriptor.serializer as KSerializer<ConfigEntity>
        scope.launch {
            runCatching { api.publishDraft(descriptor.id, ser, descriptor.idOf(entity), actor) }
                .onSuccess { refresh(); onDone(true) }
                .onFailure { t -> error = t.message; onDone(false) }
        }
    }

    companion object {
        /** Live-search debounce: refresh ~250ms after the last keystroke. */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
