package com.rate.sdk.ui.operator.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rate.core.base.model.PageRequest
import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.catalog.model.rfq.Rfq
import com.rate.sdk.catalog.model.rfq.RfqStatus
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * State + actions for the RFQ SALES pipeline kanban (Dorian slides 16-20). Plain-KMP Compose VM:
 * holds snapshot state, takes the [ConfigAdminApi] transport + a [CoroutineScope] from the surface.
 *
 * Responsibilities:
 *  - Load the full RFQ book (one page, size 500) and the plan catalog for the comparison strip.
 *  - Group RFQs into the [RfqStatus] columns that make up the board.
 *  - Advance / regress an RFQ's status with an optimistic-version-aware update (audit-attributed).
 *  - Parse a pasted member census into a [DemographySummary] preview (display only — no persistence).
 */
class SalesPipelineViewModel(
    private val api: ConfigAdminApi,
    private val scope: CoroutineScope,
    private val actor: String?,
) {
    // ── Board state ─────────────────────────────────────────────────────────
    var rfqs by mutableStateOf<List<Rfq>>(emptyList())
        private set
    var plans by mutableStateOf<List<Plan>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** The RFQ whose detail panel is open (null = board only). */
    var selectedId by mutableStateOf<String?>(null)
        private set

    /** True while a status transition is in flight (disables the transition buttons). */
    var transitioning by mutableStateOf(false)
        private set

    // ── Census paste / demography preview ──────────────────────────────────
    var censusText by mutableStateOf("")
        private set
    var demography by mutableStateOf<DemographySummary?>(null)
        private set

    val selected: Rfq? get() = rfqs.firstOrNull { it.id == selectedId }

    /** RFQs in the given column, newest-updated first. */
    fun column(status: RfqStatus): List<Rfq> =
        rfqs.filter { it.rfqStatus == status }.sortedByDescending { it.updatedAt }

    /** Board KPI: total RFQs across every column. */
    val totalRfqs: Int get() = rfqs.size

    /** Board KPI: total requested sum-insured across the whole pipeline (paise-precise). */
    val totalPipelineSi: Money
        get() = rfqs.fold(Money.ZERO) { acc, r -> acc + r.requestedSumInsured }

    /** (Re)load the RFQ book + plan catalog. */
    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching {
                val rfqPage = api.list("rfqs", Rfq.serializer(), PageRequest(size = 500))
                val planPage = api.list("plans", Plan.serializer(), PageRequest(size = 500))
                rfqPage.items.filterNotNull() to planPage.items.filterNotNull()
            }.onSuccess { (loadedRfqs, loadedPlans) ->
                rfqs = loadedRfqs
                plans = loadedPlans
            }.onFailure { t ->
                error = t.message ?: t::class.simpleName ?: "Failed to load the RFQ pipeline"
            }
            loading = false
        }
    }

    fun select(rfq: Rfq) {
        selectedId = rfq.id
        // Reset the census preview when switching RFQs so stale demography never bleeds across.
        censusText = ""
        demography = null
    }

    fun closeDetail() {
        selectedId = null
        censusText = ""
        demography = null
    }

    /**
     * Move [rfq] to [next] (advance or regress) via an optimistic update keyed on its loaded `v`.
     * On success the board reloads so the card lands in the right column with the bumped version.
     */
    fun transitionTo(rfq: Rfq, next: RfqStatus, onDone: (Boolean) -> Unit = {}) {
        if (next == rfq.rfqStatus) { onDone(true); return }
        transitioning = true
        error = null
        scope.launch {
            runCatching {
                api.update("rfqs", Rfq.serializer(), rfq.copy(rfqStatus = next), rfq.v, actor)
            }.onSuccess {
                transitioning = false
                load()
                onDone(true)
            }.onFailure { t ->
                transitioning = false
                error = t.message ?: "Status update failed"
                onDone(false)
            }
        }
    }

    /** Update the raw census text and recompute the demography preview from it. */
    fun setCensus(text: String) {
        censusText = text
        demography = parseCensusDemography(text)
    }
}

/** One age-band row of the demography preview: a half-open band plus its rolled-up head count. */
data class DemographyBand(
    val label: String,
    val lives: Int,
)

/** The parsed census preview — bands plus the totals shown above/below the table. */
data class DemographySummary(
    val bands: List<DemographyBand>,
    val totalLives: Int,
    val rowsParsed: Int,
    val rowsSkipped: Int,
)

/**
 * Standard 10-year age bands used to roll up a pasted census into a demography preview. The last
 * band is open-ended (61+). Kept module-private + pure so it stays testable and KMP-safe.
 */
private val AGE_BANDS: List<Triple<String, Int, Int>> = listOf(
    Triple("0-17", 0, 17),
    Triple("18-25", 18, 25),
    Triple("26-35", 26, 35),
    Triple("36-45", 36, 45),
    Triple("46-60", 46, 60),
    Triple("61+", 61, Int.MAX_VALUE),
)

/**
 * Parse a pasted member census into an age-banded demography preview. Each non-empty line is
 * "age,count" or "age,gender,count" (a leading header line starting with "age" is skipped). The
 * count is taken from the LAST numeric column so both shapes work; unparseable lines are counted
 * as skipped rather than failing the whole paste. Pure — no platform IO, display/preview only.
 */
internal fun parseCensusDemography(text: String): DemographySummary? {
    val tally = IntArray(AGE_BANDS.size)
    var parsed = 0
    var skipped = 0
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEachIndexed { idx, line ->
            if (idx == 0 && line.startsWith("age", ignoreCase = true)) return@forEachIndexed
            val cols = line.split(',').map { it.trim() }
            val age = cols.getOrNull(0)?.toIntOrNull()
            // count = last numeric column (handles "age,count" AND "age,gender,count").
            val count = cols.lastOrNull { it.toIntOrNull() != null && it != cols.firstOrNull() }
                ?.toIntOrNull()
                ?: if (cols.size == 1) 1 else null
            if (age == null || age < 0 || count == null || count <= 0) {
                skipped += 1
                return@forEachIndexed
            }
            val bandIdx = AGE_BANDS.indexOfFirst { age in it.second..it.third }
            if (bandIdx < 0) { skipped += 1; return@forEachIndexed }
            tally[bandIdx] += count
            parsed += 1
        }
    if (parsed == 0 && skipped == 0) return null
    val bands = AGE_BANDS.mapIndexed { i, b -> DemographyBand(b.first, tally[i]) }
    return DemographySummary(
        bands = bands,
        totalLives = tally.sum(),
        rowsParsed = parsed,
        rowsSkipped = skipped,
    )
}
