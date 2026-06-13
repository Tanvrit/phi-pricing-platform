package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.PageRequest
import com.rate.core.money.Money
import com.rate.core.money.sumMoney
import com.rate.sdk.catalog.model.rfq.ExperienceRating
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.rfq.RfqStatus
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Load lifecycle of the sales dashboard. */
enum class SalesDashboardSource { LOADING, LIVE, ERROR }

/**
 * The aggregated KPIs that headline the sales dashboard — every rupee value is held as a
 * [Money] (canonical integer paise) and is ONLY ever rendered through [Money.formatIndian], so the
 * "87105064608 raw-paise" display bug the competitor shipped is impossible by construction here.
 *
 * - [totalRfqs] — count of RFQs in the pipeline.
 * - [statusCounts] — RFQ count keyed by lifecycle [RfqStatus] (every status present, zero-filled).
 * - [totalRequestedSi] — sum of every RFQ's requestedSumInsured.
 * - [winRatePct] — MANAGED / (MANAGED + LOST), expressed 0..100 (null when no decided RFQs yet).
 * - [avgLossRatioPct] — mean of every experience-rating lossRatio, expressed as a percent
 *   (null when there are no experience ratings).
 * - [pipelinePremium] — sum of every experience-rating premiumCollected.
 */
data class SalesKpis(
    val totalRfqs: Int = 0,
    val statusCounts: Map<RfqStatus, Int> = emptyMap(),
    val totalRequestedSi: Money = Money.ZERO,
    val winRatePct: Double? = null,
    val avgLossRatioPct: Double? = null,
    val pipelinePremium: Money = Money.ZERO,
)

/**
 * Drives the sales dashboard: pulls the RFQ pipeline + experience-rating ledger via the generic
 * [ConfigAdminApi] and folds them into [SalesKpis] plus a "recently modified" RFQ list. State is
 * plain Compose snapshot state so the surface recomposes on load; the scope is supplied by the
 * composable (pure-KMP, no Android ViewModel dependency) following the [DashboardViewModel] idiom.
 */
class SalesDashboardViewModel(
    private val api: ConfigAdminApi,
    private val scope: CoroutineScope,
) {
    var source by mutableStateOf(SalesDashboardSource.LOADING)
        private set
    var kpis by mutableStateOf(SalesKpis())
        private set
    var recentRfqs by mutableStateOf<List<Rfq>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load() {
        source = SalesDashboardSource.LOADING
        errorMessage = null
        scope.launch {
            runCatching {
                // Load a deep page of each so the KPIs aggregate the full pipeline, not page 1.
                val rfqPage = api.list("rfqs", Rfq.serializer(), PageRequest(size = 500))
                val ratingPage = api.list("experience-ratings", ExperienceRating.serializer(), PageRequest(size = 500))
                rfqPage.items to ratingPage.items
            }.onSuccess { (rfqs, ratings) ->
                kpis = computeKpis(rfqs, ratings)
                recentRfqs = rfqs.sortedByDescending { it.updatedAt }.take(8)
                source = SalesDashboardSource.LIVE
                errorMessage = null
            }.onFailure { t ->
                source = SalesDashboardSource.ERROR
                errorMessage = t.message ?: t::class.simpleName ?: "Server unreachable"
            }
        }
    }

    private fun computeKpis(rfqs: List<Rfq>, ratings: List<ExperienceRating>): SalesKpis {
        // Zero-fill every status so the breakdown always shows the full funnel.
        val counts = RfqStatus.entries.associateWith { status -> rfqs.count { it.rfqStatus == status } }
        val managed = counts[RfqStatus.MANAGED] ?: 0
        val lost = counts[RfqStatus.LOST] ?: 0
        val decided = managed + lost
        return SalesKpis(
            totalRfqs = rfqs.size,
            statusCounts = counts,
            totalRequestedSi = rfqs.map { it.requestedSumInsured }.sumMoney(),
            winRatePct = if (decided == 0) null else managed * 100.0 / decided,
            avgLossRatioPct = if (ratings.isEmpty()) null else ratings.map { it.lossRatio }.average() * 100.0,
            pipelinePremium = ratings.map { it.premiumCollected }.sumMoney(),
        )
    }
}
