package com.rate.sdk.ui.operator.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.PageRequest
import com.rate.sdk.ui.kit.components.AegisBadge
import com.rate.sdk.ui.kit.components.AegisBadgeTone
import com.rate.sdk.ui.kit.components.AegisButton
import com.rate.sdk.ui.kit.components.AegisButtonSize
import com.rate.sdk.ui.kit.components.AegisButtonVariant
import com.rate.sdk.ui.kit.components.AegisCard
import com.rate.sdk.ui.kit.components.AegisInfoTooltip
import com.rate.sdk.ui.kit.i18n.GlossaryTerm
import com.rate.sdk.ui.kit.i18n.glossaryDef
import com.rate.sdk.ui.kit.i18n.t
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisMasterDataStyle
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.model.EntityDescriptor
import com.rate.sdk.ui.operator.network.ConfigAdminApi
import com.rate.sdk.ui.operator.registry.ConfigHub
import com.rate.sdk.ui.operator.registry.TypedEntityDescriptor
import kotlinx.serialization.KSerializer

/** Number of hub cards per row in the grid. */
private const val CARDS_PER_ROW = 3

/**
 * The live count state for one hub card: total + active (non-deleted) rows, or an error.
 *  - [loading] while the first fetch is in flight.
 *  - [total] all rows incl. soft-deleted; [active] non-deleted; [failed] when the API was unreachable.
 */
data class HubCount(
    val total: Long = 0,
    val active: Long = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * A count loader for one entity — returns its [HubCount]. The default ([apiCountLoader]) hits the
 * generic admin list endpoint twice (active + including-deleted) and derives active/total; tests or
 * offline shells can pass a synthetic loader.
 */
typealias HubCountLoader = suspend (TypedEntityDescriptor<*>) -> HubCount

/**
 * ConfigHubSurface — a generic card-grid hub for ONE [ConfigHub] (business group).
 *
 * Each registered entity in the hub renders a card with its title + description, a LIVE row count
 * (active / total) loaded via the [countLoader] (which by default calls [ConfigAdminApi.list] and
 * reads the page totals), and two affordances: "Manage" (open the entity's
 * [com.rate.sdk.ui.operator.screen.ConfigEntityScreen]) and "Quick add" (open it straight into the
 * create drawer). Clicking anywhere on the card body also opens Manage.
 *
 * The surface owns NO navigation state — it raises [onManage] / [onQuickAdd] with the entity id and
 * the console routes. This keeps the hub testable and reusable for any group.
 *
 * @param hub          the group + its descriptors to render.
 * @param api          admin client used by the default count loader.
 * @param onManage     open the entity's config screen (list mode).
 * @param onQuickAdd   open the entity's config screen straight into "New" (create) mode.
 * @param countLoader  override the per-entity count fetch (tests / offline).
 */
@Composable
fun ConfigHubSurface(
    hub: ConfigHub,
    api: ConfigAdminApi,
    onManage: (entityId: String) -> Unit,
    onQuickAdd: (entityId: String) -> Unit,
    modifier: Modifier = Modifier,
    countLoader: HubCountLoader = remember(api) { apiCountLoader(api) },
) {
    Column(
        modifier
            .fillMaxSize()
            .background(AegisColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s6),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s1)) {
            Text(hub.group.title, style = AegisTypography.h1.copy(color = AegisColors.textPrimary))
            Text(hub.group.subtitle, style = AegisTypography.body.copy(color = AegisColors.textSecondary))

            // Surface a localised glossary for any jargon terms that appear in this
            // hub's copy (title / subtitle / per-entity description). Operators hover
            // the (i) icons to read plain-English definitions of EVA, IBNR, … .
            val hubTerms = remember(hub) { detectGlossaryTerms(hub) }
            if (hubTerms.isNotEmpty()) {
                GlossaryStrip(terms = hubTerms)
            }
        }

        if (hub.descriptors.isEmpty()) {
            AegisCard {
                Text(
                    t("confighub.empty"),
                    style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                )
            }
            return@Column
        }

        // Fixed-column grid laid out as rows of [CARDS_PER_ROW]; the last row pads with
        // invisible weight so cards keep a consistent width.
        hub.descriptors.chunked(CARDS_PER_ROW).forEach { rowDescriptors ->
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s4)) {
                rowDescriptors.forEach { descriptor ->
                    HubCard(
                        descriptor = descriptor,
                        countLoader = countLoader,
                        onManage = { onManage(descriptor.id) },
                        onQuickAdd = { onQuickAdd(descriptor.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the trailing partial row so cards don't stretch.
                repeat(CARDS_PER_ROW - rowDescriptors.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HubCard(
    descriptor: EntityDescriptor,
    countLoader: HubCountLoader,
    onManage: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var count by remember(descriptor.id) { mutableStateOf(HubCount()) }
    LaunchedEffect(descriptor.id) {
        // descriptor is always a TypedEntityDescriptor at runtime (registry invariant).
        val typed = descriptor as TypedEntityDescriptor<*>
        count = runCatching { countLoader(typed) }
            .getOrElse { HubCount(loading = false, failed = true) }
    }

    AegisCard(
        modifier = modifier.clickable(onClick = onManage),
        padding = PaddingValues(AegisSpacing.s5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
            // A jargon term mentioned in the card title or description gets an inline
            // (i) explainer next to the title.
            val cardTerm = remember(descriptor.id) {
                firstGlossaryTermIn(descriptor.plural, descriptor.singular, descriptor.description)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        descriptor.plural,
                        style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
                    )
                    if (cardTerm != null) {
                        AegisInfoTooltip(
                            term = cardTerm.label,
                            definition = glossaryDef(cardTerm),
                        )
                    }
                }
                CountBadge(count)
            }
            Text(
                descriptor.description,
                style = AegisTypography.small.copy(color = AegisColors.textSecondary),
            )

            CountLine(descriptor = descriptor, count = count)

            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
                AegisButton(
                    label = "Manage",
                    onClick = onManage,
                    variant = AegisButtonVariant.Secondary,
                    size = AegisButtonSize.Sm,
                )
                AegisButton(
                    label = "Quick add",
                    onClick = onQuickAdd,
                    variant = AegisButtonVariant.Ghost,
                    size = AegisButtonSize.Sm,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

/** The big count badge top-right — brand when populated, neutral when empty/loading. */
@Composable
private fun CountBadge(count: HubCount) {
    when {
        count.loading -> AegisBadge(label = "…", tone = AegisBadgeTone.Neutral)
        count.failed -> AegisBadge(label = "—", tone = AegisBadgeTone.Warn, semanticLabel = "Count unavailable")
        else -> AegisBadge(
            count = count.active.toInt(),
            tone = if (count.active > 0) AegisBadgeTone.Brand else AegisBadgeTone.Neutral,
            semanticLabel = AegisMasterDataStyle.countLabel(count.active.toInt(), "record"),
        )
    }
}

/** The "12 active · 14 total" line under the description (or a friendly empty/loading/failed state). */
@Composable
private fun CountLine(descriptor: EntityDescriptor, count: HubCount) {
    val text = when {
        count.loading -> "Counting…"
        count.failed -> "Count unavailable — open to retry."
        count.total == 0L -> "No ${AegisMasterDataStyle.pluralize(0, descriptor.singular.lowercase(), descriptor.plural.lowercase())} yet"
        count.active == count.total ->
            AegisMasterDataStyle.countLabel(count.active.toInt(), descriptor.singular.lowercase(), descriptor.plural.lowercase())
        else ->
            "${count.active} active · ${count.total} total"
    }
    Box(
        Modifier
            .background(AegisColors.surfaceMuted, AegisRadii.shapeSm)
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s2),
    ) {
        Text(text, style = AegisTypography.small.copy(color = AegisColors.textBody))
    }
}

/**
 * The default [HubCountLoader] — derives active/total from the admin list endpoint.
 *
 * It asks for a single-row page (size = 1) twice: once normally (active total) and once with
 * `includeDeleted` (grand total). Only the page `total` is read, so the payload stays tiny even for
 * large collections. Any transport failure surfaces as a failed [HubCount].
 *
 * The descriptor's serializer is star-projected; we cast to `KSerializer<ConfigEntity>` (the same
 * pattern the ConfigEntityViewModel uses) since the admin list endpoint only reads the page totals.
 */
fun apiCountLoader(api: ConfigAdminApi): HubCountLoader = loader@{ descriptor ->
    @Suppress("UNCHECKED_CAST")
    val serializer = descriptor.serializer as KSerializer<ConfigEntity>
    runCatching {
        val active = api.list(descriptor.id, serializer, PageRequest(page = 0, size = 1)).total
        val total = api.list(
            descriptor.id,
            serializer,
            PageRequest(page = 0, size = 1, includeDeleted = true),
        ).total
        HubCount(total = total, active = active, loading = false, failed = false)
    }.getOrElse { HubCount(loading = false, failed = true) }
}

// --- Glossary detection + affordance -------------------------------------------------

/**
 * A compact, hover-revealable glossary of the jargon terms found in this hub. Each
 * chip-like entry is the term followed by an [AegisInfoTooltip] carrying its
 * localised definition, so an operator new to insurance can decode the hub at a
 * glance. Laid out as a wrapping set of [Row]s (no LazyRow) so it nests safely in
 * the parent verticalScroll.
 */
@Composable
private fun GlossaryStrip(terms: List<GlossaryTerm>) {
    Column(
        Modifier.padding(top = AegisSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
    ) {
        Text(
            t("confighub.glossary.hint"),
            style = AegisTypography.caption.copy(color = AegisColors.textTertiary),
        )
        // Manual wrap: chunk terms into rows so a long glossary doesn't overflow.
        terms.chunked(GLOSSARY_TERMS_PER_ROW).forEach { rowTerms ->
            Row(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3)) {
                rowTerms.forEach { term ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            term.label,
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                        )
                        AegisInfoTooltip(term = term.label, definition = glossaryDef(term))
                    }
                }
            }
        }
    }
}

private const val GLOSSARY_TERMS_PER_ROW = 4

/**
 * Find every [GlossaryTerm] whose label appears (as a whole, case-insensitive
 * token) anywhere in the hub's title, subtitle or descriptor copy. De-duplicated
 * and kept in [GlossaryTerm] declaration order so the strip reads consistently.
 */
private fun detectGlossaryTerms(hub: ConfigHub): List<GlossaryTerm> {
    val haystack = buildString {
        append(hub.group.title).append(' ').append(hub.group.subtitle)
        hub.descriptors.forEach { d ->
            append(' ').append(d.plural).append(' ').append(d.singular).append(' ').append(d.description)
        }
    }
    return GlossaryTerm.entries.filter { containsTerm(haystack, it.label) }
}

/** First [GlossaryTerm] (in declaration order) mentioned across the given copy, or null. */
private fun firstGlossaryTermIn(vararg copy: String): GlossaryTerm? {
    val haystack = copy.joinToString(" ")
    return GlossaryTerm.entries.firstOrNull { containsTerm(haystack, it.label) }
}

/**
 * Whole-token, case-insensitive match of [label] inside [text]. A bare alphanumeric
 * acronym (EVA, NCB) must not be a substring of a larger word, so we require the
 * characters on either side of a match to be non-alphanumeric (or the string edge).
 */
private fun containsTerm(text: String, label: String): Boolean {
    if (label.isEmpty()) return false
    val hay = text.lowercase()
    val needle = label.lowercase()
    var from = 0
    while (true) {
        val idx = hay.indexOf(needle, from)
        if (idx < 0) return false
        val before = if (idx == 0) null else hay[idx - 1]
        val afterIdx = idx + needle.length
        val after = if (afterIdx >= hay.length) null else hay[afterIdx]
        val leftOk = before == null || !before.isLetterOrDigit()
        val rightOk = after == null || !after.isLetterOrDigit()
        if (leftOk && rightOk) return true
        from = idx + 1
    }
}
