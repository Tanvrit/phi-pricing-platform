package com.rate.core.base.model

import kotlinx.serialization.Serializable

@Serializable
enum class SortDir { ASC, DESC }

@Serializable
data class SortSpec(val field: String, val dir: SortDir = SortDir.ASC)

/** Paged + filtered list query, used uniformly by every admin-CRUD list endpoint. */
@Serializable
data class PageRequest(
    val page: Int = 0,
    val size: Int = 50,
    val sort: List<SortSpec> = emptyList(),
    /** field -> value equality/contains filters (interpreted by the repository). */
    val filter: Map<String, String> = emptyMap(),
    val includeDeleted: Boolean = false,
)

/** A page of results. `T` must be `@Serializable`. */
@Serializable
data class Page<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
) {
    val hasNext: Boolean get() = (page + 1).toLong() * size < total
    companion object {
        fun <T> empty(req: PageRequest = PageRequest()): Page<T> =
            Page(emptyList(), 0, req.page, req.size)
    }
}
