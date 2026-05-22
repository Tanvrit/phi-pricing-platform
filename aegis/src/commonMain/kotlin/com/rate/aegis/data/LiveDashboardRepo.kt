package com.rate.aegis.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.rate.aegis.business.calculator.api.ApiClient
import com.rate.domain.model.Plan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.coroutineContext

/**
 * Source of the data the Aegis dashboard is rendering. Surfaced as a callout on
 * the screen so operators (and screenshots) know whether the numbers are real
 * or a demo fallback.
 */
enum class DashboardSource {
    /** Initial state before the server has answered. */
    LOADING,

    /** Server responded with the quotes table — the dashboard shows live data. */
    LIVE,

    /** Server unreachable or empty; we show a deterministic synthetic dataset
     *  (FakeAegisRepo) so the surfaces aren't blank during demos / offline dev. */
    DEMO
}

/**
 * Snapshot of everything the operator dashboard needs to render. Surfaces consume
 * this as a single value rather than 3 separate states so a partial update can't
 * mix live KPIs with demo quotes.
 */
data class DashboardData(
    val quotes: List<FakeAegisRepo.FakeQuote>,
    val kpis: FakeAegisRepo.HomeKpis,
    val source: DashboardSource,
    /** Human-readable explanation when [source] is DEMO. Empty otherwise. */
    val fallbackReason: String = "",
    /** ISO-8601 timestamp of the last successful fetch, empty on initial LOADING. */
    val refreshedAt: String = ""
)

/**
 * Live-data side of the dashboard. Calls the server, projects its `QuoteListItem`
 * shape onto the existing `FakeAegisRepo.FakeQuote` shape (so surfaces don't have
 * to be refactored), computes KPIs from those real rows, and falls back to the
 * synthetic repo on any failure.
 *
 * Future work: subscribe to server-sent events or poll periodically — today a
 * single fetch on screen open is enough.
 */
class LiveDashboardRepo(private val client: ApiClient) {

    suspend fun load(): DashboardData {
        // Fetch quotes + plans in parallel — fail-soft: if plans alone fails we still
        // render with FakeAegisRepo.plans as the cross-reference for lifecycle metadata.
        val plansResult = runCatching { client.getPlans() }
        val quotesResult = runCatching { client.listQuotes(limit = 200) }
        return when {
            quotesResult.isFailure ->
                demo("Server at ${client.baseUrl} is unreachable (${quotesResult.exceptionOrNull()?.message ?: "unknown"}). Showing a synthetic dataset.")
            quotesResult.getOrNull().isNullOrEmpty() ->
                demo("Server reachable, but the quotes table is empty. Showing a synthetic dataset.")
            else -> {
                val rows = quotesResult.getOrThrow()
                val plans = plansResult.getOrNull().orEmpty()
                val mapped = rows.mapNotNull { it.toFakeQuote() }
                if (mapped.isEmpty()) {
                    demo("Server responded but no row could be parsed. Showing a synthetic dataset.")
                } else {
                    DashboardData(
                        quotes = mapped,
                        kpis = computeKpis(mapped, plans),
                        source = DashboardSource.LIVE,
                        refreshedAt = nowIsoZ()
                    )
                }
            }
        }
    }

    private fun demo(reason: String) = DashboardData(
        quotes = FakeAegisRepo.quotes,
        kpis = FakeAegisRepo.homeKpis,
        source = DashboardSource.DEMO,
        fallbackReason = reason,
        refreshedAt = nowIsoZ()
    )

    /** Map server's `QuoteListItem` JSON shape → existing `FakeQuote` row. */
    private fun Map<String, JsonElement>.toFakeQuote(): FakeAegisRepo.FakeQuote? {
        val id            = str("id") ?: return null
        val planId        = str("planId") ?: return null
        val age           = int("age") ?: return null
        val sumInsured    = long("sumInsured") ?: return null
        val familyType    = str("familyType") ?: return null
        val zone          = str("zone") ?: return null
        val tenure        = str("tenure") ?: return null
        val createdAt     = str("createdAt") ?: ""
        val total         = dbl("totalIncludingGst") ?: 0.0
        val isValid       = bool("isValid") ?: true
        val planName      = FakeAegisRepo.plans.firstOrNull { it.id == planId }?.name ?: planId
        return FakeAegisRepo.FakeQuote(
            id                = id,
            createdAt         = createdAt.take(10),  // YYYY-MM-DD prefix
            planId            = planId,
            planName          = planName,
            primaryAge        = age,
            sumInsured        = sumInsured,
            familyType        = familyType,
            zone              = zone,
            tenureLabel       = tenure,
            totalIncludingGst = if (isValid) total else 0.0,
            isValid           = isValid
        )
    }

    /**
     * Compute KPI tiles from the real quote stream. "Today" = max date in the
     * dataset (server `createdAt` is ISO-8601, sortable lexicographically).
     * "Yesterday" = preceding date. We don't rely on the host's wall clock
     * because that would skew demos and tests run on different days.
     */
    private fun computeKpis(
        quotes: List<FakeAegisRepo.FakeQuote>,
        plans: List<Plan>
    ): FakeAegisRepo.HomeKpis {
        val dates = quotes.asSequence().map { it.createdAt }.filter { it.isNotEmpty() }.distinct().sorted().toList()
        val today = dates.lastOrNull() ?: ""
        val yest  = dates.dropLast(1).lastOrNull() ?: ""
        val byDate = quotes.groupBy { it.createdAt }
        val quotesToday     = byDate[today]?.size ?: 0
        val quotesYesterday = byDate[yest]?.size ?: 0
        val thisMonth = today.take(7)
        val lastMonthPrefix = previousMonth(thisMonth)
        val gwpThisMonth = quotes.filter { it.createdAt.startsWith(thisMonth) && it.isValid }
            .sumOf { it.totalIncludingGst }
        val gwpLastMonth = quotes.filter { it.createdAt.startsWith(lastMonthPrefix) && it.isValid }
            .sumOf { it.totalIncludingGst }
        val validRatio = if (quotes.isNotEmpty())
            (quotes.count { it.isValid }.toDouble() / quotes.size) * 100.0
        else 0.0
        return FakeAegisRepo.HomeKpis(
            quotesToday            = quotesToday,
            quotesYesterday        = quotesYesterday,
            gwpThisMonth           = gwpThisMonth,
            gwpLastMonth           = gwpLastMonth,
            // We don't have policy-conversion data over the wire yet, so we use the
            // valid-quote ratio as a proxy. Will be replaced by real conversion once
            // a `policies` projection lands. Delta is set to 0 to avoid implying we
            // know the trend.
            conversionRatePct      = validRatio,
            conversionRateDeltaPct = 0.0,
            // Plan-lifecycle counts: cross-reference real plans from the server
            // against FakeAegisRepo's PlanMeta (the lifecycle field is still Aegis-
            // side until the domain Plan grows a status column). Plans the server
            // returns that aren't in PlanMeta default to LIVE — better than dropping.
            activePlans            = lifecycleCount(plans, PlanLifecycle.LIVE),
            draftPlans             = lifecycleCount(plans, PlanLifecycle.DRAFT),
            retiredPlans           = lifecycleCount(plans, PlanLifecycle.RETIRED),
            // UW queue isn't surfaced over the wire either — show the synthetic count
            // until the audit/UW endpoints land.
            uwBacklog              = FakeAegisRepo.homeKpis.uwBacklog,
            uwBreaches             = FakeAegisRepo.homeKpis.uwBreaches
        )
    }

    /**
     * Count how many plans have a given lifecycle. Reads `plan.lifecycle` directly
     * off the domain model (server-persisted as of V4). Falls back to FakeAegisRepo's
     * synthetic plans (which carry their own `lifecycle` via the typealiased enum)
     * when the server returned nothing, so demos still render.
     */
    private fun lifecycleCount(plans: List<Plan>, lifecycle: PlanLifecycle): Int {
        val source = if (plans.isNotEmpty()) plans else FakeAegisRepo.plans
        return source.count { it.lifecycle == lifecycle }
    }

    /** "2026-05" → "2026-04". Handles year boundary. */
    private fun previousMonth(yyyyMm: String): String {
        if (yyyyMm.length != 7) return ""
        val year  = yyyyMm.take(4).toIntOrNull() ?: return ""
        val month = yyyyMm.takeLast(2).toIntOrNull() ?: return ""
        return if (month == 1) "${year - 1}-12"
        else                   "$year-${(month - 1).toString().padStart(2, '0')}"
    }

    private fun Map<String, JsonElement>.str(k: String) = get(k)?.jsonPrimitive?.contentOrNull
    private fun Map<String, JsonElement>.int(k: String) = get(k)?.jsonPrimitive?.intOrNull
    private fun Map<String, JsonElement>.long(k: String) = get(k)?.jsonPrimitive?.longOrNull
    private fun Map<String, JsonElement>.dbl(k: String) = get(k)?.jsonPrimitive?.doubleOrNull
    private fun Map<String, JsonElement>.bool(k: String) =
        get(k)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}

/**
 * Composable wrapper — boots a [LiveDashboardRepo], fetches once on first composition,
 * and returns the resulting [DashboardData] (or LOADING placeholder until the call lands).
 *
 * Surfaces use this as their data source instead of going to FakeAegisRepo directly.
 */
@Composable
fun rememberDashboardData(
    baseUrl: String = "http://localhost:9090",
    /** Auto-refresh cadence. Set to 0 to fetch once. Default: 30s — generous enough that
     *  the dashboard updates between operator clicks without hammering the API. */
    refreshEverySeconds: Int = 30
): State<DashboardData> {
    val state = remember {
        mutableStateOf(
            DashboardData(
                quotes = emptyList(),
                kpis = FakeAegisRepo.homeKpis.copy(
                    quotesToday = 0, quotesYesterday = 0,
                    gwpThisMonth = 0.0, gwpLastMonth = 0.0,
                    conversionRatePct = 0.0, conversionRateDeltaPct = 0.0
                ),
                source = DashboardSource.LOADING
            )
        )
    }
    LaunchedEffect(baseUrl, refreshEverySeconds) {
        val repo = LiveDashboardRepo(ApiClient(baseUrl))
        while (coroutineContext.isActive) {
            state.value = repo.load()
            if (refreshEverySeconds <= 0) break
            delay(refreshEverySeconds * 1000L)
        }
    }
    return state
}

/** Local clock as ISO-8601 Z. Used to stamp [DashboardData.refreshedAt]. */
private fun nowIsoZ(): String = kotlinx.datetime.Clock.System.now().toString()
