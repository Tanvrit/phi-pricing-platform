package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.PageRequest
import com.rate.core.base.model.SortDir
import com.rate.core.base.model.SortSpec
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.quoting.network.QuoteSummaryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Loading state of the operator dashboard. */
enum class DashboardSource { LOADING, LIVE, ERROR }

/** Five headline KPIs computed from the saved-quote ledger. Relocated from the aegis FakeAegisRepo. */
data class DashboardKpis(
    val quotesToday: Int = 0,
    val gwpRecent: Double = 0.0,
    val validCount: Int = 0,
    val invalidCount: Int = 0,
    val totalQuotes: Int = 0,
) {
    val conversionRatePct: Double get() = if (totalQuotes == 0) 0.0 else validCount * 100.0 / totalQuotes
}

/**
 * Drives the operator dashboard: pulls the most recent saved-quote summaries via [QuoteApi] and
 * computes the headline KPIs + recent-quote table. Replaces the monolith's FakeAegisRepo synthetic
 * data path with the real wire surface (with a graceful ERROR state when the server is unreachable).
 */
class DashboardViewModel(
    private val quotes: QuoteApi,
    private val scope: CoroutineScope,
) {
    var source by mutableStateOf(DashboardSource.LOADING)
        private set
    var kpis by mutableStateOf(DashboardKpis())
        private set
    var recent by mutableStateOf<List<QuoteSummaryDto>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load() {
        source = DashboardSource.LOADING
        scope.launch {
            runCatching {
                quotes.list(
                    PageRequest(page = 0, size = 200, sort = listOf(SortSpec("createdAt", SortDir.DESC))),
                )
            }.onSuccess { page ->
                val items = page.items
                recent = items.take(12)
                kpis = computeKpis(items, page.total)
                source = DashboardSource.LIVE
                errorMessage = null
            }.onFailure { t ->
                source = DashboardSource.ERROR
                errorMessage = t.message ?: t::class.simpleName ?: "Server unreachable"
            }
        }
    }

    private fun computeKpis(items: List<QuoteSummaryDto>, total: Long): DashboardKpis {
        // createdAt is an Instant; "today" = same UTC date prefix as the newest item.
        val newestDay = items.firstOrNull()?.createdAt?.toString()?.take(10)
        val quotesToday = if (newestDay == null) 0 else items.count { it.createdAt.toString().take(10) == newestDay }
        val valid = items.filter { it.isValid }
        return DashboardKpis(
            quotesToday = quotesToday,
            gwpRecent = valid.sumOf { it.totalIncludingGst },
            validCount = valid.size,
            invalidCount = items.size - valid.size,
            totalQuotes = total.toInt(),
        )
    }
}
