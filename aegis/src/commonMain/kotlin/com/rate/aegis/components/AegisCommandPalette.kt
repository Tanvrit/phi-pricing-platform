package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography

/**
 * A single, addressable target in the global command palette.
 *
 * - [id] keys the LazyColumn row so highlight/animations survive list reshuffles.
 * - [title] is the bold first line.
 * - [subtitle] the muted second line (use it to disambiguate: "Surface · dashboard",
 *   "Plan · PHI-Basic", "Quote · #1041", etc.).
 * - [keywords] are extra search hits never rendered to the UI — useful for aliases
 *   ("home" → "dashboard overview" so typing either lands the same row).
 * - [action] runs when the operator picks the row. Callers wire this to whatever
 *   their navigation primitive is; the palette itself calls onDismiss before it
 *   invokes the action so callers don't have to remember to close.
 */
data class AegisCommand(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String = "",
    val action: () -> Unit,
)

private const val MAX_VISIBLE = 12

/**
 * Global command palette overlay (⌘K / Ctrl+K).
 *
 * The palette is a fully-modal layer: a semi-transparent black backdrop fills the
 * shell and a card sits 40dp from the top, centered horizontally at 640dp wide.
 * Tapping the backdrop dismisses; tapping inside the card is absorbed.
 *
 * Matching is case-insensitive substring over `title + subtitle + keywords`, with
 * relevance ranking that favours prefix-on-title matches first, then everything
 * else stable-sorted in input order. The empty query just shows the first
 * [MAX_VISIBLE] commands as-given.
 *
 * Keyboard:
 *  - Esc dismisses.
 *  - Enter triggers the currently highlighted row (or first if none highlighted).
 *  - Up / Down arrows move the highlight and scroll the list to keep it visible.
 *
 * This component is purely presentational — the parent shell owns [open] and
 * builds the [commands] list per-render.
 */
@Composable
fun AegisCommandPalette(
    open: Boolean,
    commands: List<AegisCommand>,
    onDismiss: () -> Unit,
    recentIds: List<String> = emptyList(),
    onCommandInvoked: ((AegisCommand) -> Unit)? = null,
) {
    if (!open) return

    var query by remember { mutableStateOf("") }
    var highlighted by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Reset transient state every time the palette is opened.
    LaunchedEffect(Unit) {
        query = ""
        highlighted = 0
        // requestFocus may throw if the requester isn't attached yet on some
        // targets; runCatching keeps WASM happy.
        runCatching { focusRequester.requestFocus() }
    }

    // `displayed` is the flat, ordered list backing both the LazyColumn rows and
    // the keyboard highlight index. When the query is empty AND we have any
    // recents, we splice the recents to the top and exclude them from the
    // "All" tail — so each command appears exactly once in the visible list.
    // (`sections` carries the section-header metadata for the renderer.)
    val sectioned = remember(query, commands, recentIds) {
        buildSections(query, commands, recentIds)
    }
    val displayed = sectioned.flatMap { it.commands }

    // If the result list shrinks, snap the highlight back into range.
    LaunchedEffect(displayed.size) {
        if (highlighted >= displayed.size) highlighted = 0
    }

    // Keep the highlighted row scrolled into view.
    LaunchedEffect(highlighted, displayed.size) {
        if (displayed.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(highlighted.coerceIn(0, displayed.lastIndex)) }
        }
    }

    fun fire(index: Int) {
        val target = displayed.getOrNull(index) ?: return
        onDismiss()
        onCommandInvoked?.invoke(target)
        target.action()
    }

    val backdropClick = remember { MutableInteractionSource() }
    val absorbClick = remember { MutableInteractionSource() }

    // Backdrop. Whole-screen, tappable to dismiss.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = backdropClick,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Card. We rebuild a card by hand here (instead of nesting AegisCard)
        // so the keyboard listener can wrap the whole surface — AegisCard doesn't
        // forward modifiers to its inner Column.
        Column(
            Modifier
                .padding(top = 40.dp)
                .width(640.dp)
                .shadow(16.dp, AegisRadii.shapeLg, clip = false)
                .background(AegisColors.surface, AegisRadii.shapeLg)
                .border(1.dp, AegisColors.border, AegisRadii.shapeLg)
                .clickable(
                    interactionSource = absorbClick,
                    indication = null,
                    onClick = {},
                ),
        ) {
            // Search field — owns the key listener via onPreviewKeyEvent so we
            // intercept Up/Down/Enter/Esc before BasicTextField's caret logic.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AegisSpacing.s4,
                        end = AegisSpacing.s4,
                        top = AegisSpacing.s4,
                        bottom = AegisSpacing.s3,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .background(AegisColors.surfaceMuted, AegisRadii.shapeMd)
                        .border(1.dp, AegisColors.border, AegisRadii.shapeMd)
                        .padding(horizontal = AegisSpacing.s3),
                ) {
                    Text(
                        text = ">",
                        style = AegisTypography.mono14.copy(color = AegisColors.textTertiary),
                        modifier = Modifier.padding(end = AegisSpacing.s2),
                    )
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                highlighted = 0
                            },
                            singleLine = true,
                            textStyle = AegisTypography.body.copy(color = AegisColors.textPrimary),
                            cursorBrush = SolidColor(AegisColors.brand),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.Escape -> {
                                            onDismiss()
                                            true
                                        }
                                        Key.Enter, Key.NumPadEnter -> {
                                            fire(highlighted)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (displayed.isNotEmpty()) {
                                                highlighted = (highlighted + 1) % displayed.size
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (displayed.isNotEmpty()) {
                                                highlighted =
                                                    if (highlighted <= 0) displayed.lastIndex
                                                    else highlighted - 1
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                },
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Jump to a surface, plan, quote…",
                                        style = AegisTypography.body.copy(color = AegisColors.textTertiary),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }

            AegisHDivider()

            // Results region — fixed max-row height so the palette never overgrows
            // the viewport on tiny windows. 56dp per row × 12 rows = ~672dp ceiling.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = (12 * 56).dp),
            ) {
                if (displayed.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(AegisSpacing.s5),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No matches. Try a different query.",
                            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AegisSpacing.s2),
                    ) {
                        // Walk the sections, emitting a header row before each
                        // labelled group. We track the running flat-index so
                        // every CommandRow knows its position in `displayed`
                        // for highlight / hover / Enter semantics.
                        var flatIndex = 0
                        sectioned.forEach { section ->
                            if (section.title != null) {
                                item(key = "section.${section.title}") {
                                    SectionHeader(section.title)
                                }
                            }
                            items(section.commands, key = { it.id }) { cmd ->
                                val index = flatIndex + section.commands.indexOf(cmd)
                                val isActive = index == highlighted
                                CommandRow(
                                    command = cmd,
                                    active = isActive,
                                    onClick = { fire(index) },
                                    onHover = { highlighted = index },
                                )
                            }
                            flatIndex += section.commands.size
                        }
                    }
                }
            }

            AegisHDivider()

            // Footer hint — tiny mono so it reads as keyboard documentation, not chrome.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            ) {
                AegisKbd("↑")
                AegisKbd("↓")
                Text(
                    "navigate",
                    style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                )
                Spacer(Modifier.width(AegisSpacing.s2))
                AegisKbd("↵")
                Text(
                    "open",
                    style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                )
                Spacer(Modifier.width(AegisSpacing.s2))
                AegisKbd("esc")
                Text(
                    "close",
                    style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                )
            }
        }
    }
}

@Composable
private fun CommandRow(
    command: AegisCommand,
    active: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit,
) {
    val bg = if (active) AegisColors.brandTint else Color.Transparent
    val titleColor = if (active) AegisColors.brandPressed else AegisColors.textPrimary
    val rowInteraction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AegisSpacing.s2)
            .background(bg, RoundedCornerShape(AegisRadii.rMd))
            .clickable(
                interactionSource = rowInteraction,
                indication = null,
                onClick = {
                    onHover()
                    onClick()
                },
            )
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = command.title,
                style = AegisTypography.body.copy(
                    color = titleColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                ),
            )
            if (command.subtitle.isNotBlank()) {
                Text(
                    text = command.subtitle,
                    style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                )
            }
        }
        // Right-side affordance: only the active row gets the ↵ chip; inactive
        // rows show a faint chevron so the list still has a strong vertical edge.
        if (active) {
            AegisKbd("↵")
        } else {
            Text(
                text = "›",
                style = AegisTypography.mono14.copy(color = AegisColors.textTertiary),
            )
        }
    }
}

/**
 * Rank commands by relevance for the given [query].
 *
 * Buckets (lower is better):
 *   0 — title starts with the query (case-insensitive).
 *   1 — title contains the query.
 *   2 — subtitle or keywords contain the query.
 *
 * Within a bucket, original input order is preserved (sortedBy is stable on
 * Kotlin's List<*> backing). An empty query short-circuits to the first
 * [MAX_VISIBLE] commands as-given, which is the documented behaviour callers
 * lean on so "no input shows everything important first" works.
 */
internal fun matchCommands(query: String, commands: List<AegisCommand>): List<AegisCommand> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return commands.take(MAX_VISIBLE)

    val scored = commands.mapIndexedNotNull { idx, cmd ->
        val title = cmd.title.lowercase()
        val sub = cmd.subtitle.lowercase()
        val kw = cmd.keywords.lowercase()
        val bucket = when {
            title.startsWith(q) -> 0
            title.contains(q) -> 1
            sub.contains(q) || kw.contains(q) -> 2
            else -> return@mapIndexedNotNull null
        }
        Triple(bucket, idx, cmd)
    }

    return scored
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(MAX_VISIBLE)
        .map { it.third }
}

/**
 * A labelled slice of the visible palette. `title == null` means "no header"
 * (used both for the no-recents single-section case and the trailing "All"
 * bucket when we want to keep the visual cleaner).
 */
internal data class PaletteSection(
    val title: String?,
    val commands: List<AegisCommand>,
)

/**
 * Splits [commands] into the sections the palette should render given the
 * current [query] and persisted [recentIds].
 *
 *   - When [query] is non-empty: a single un-labelled "matches" section
 *     produced by [matchCommands]. Recents are NOT surfaced — the operator
 *     has indicated intent, ranking by relevance is the right thing.
 *   - When [query] is empty AND [recentIds] is non-empty: a "Recent" section
 *     with up to 5 entries in the order they appear in [recentIds], followed
 *     by an "All" section containing the rest of the catalog (excluding the
 *     ids already in Recent so each command appears exactly once).
 *   - When [query] is empty AND [recentIds] is empty: a single un-labelled
 *     section that matches the legacy empty-query behaviour ([matchCommands]
 *     with an empty query returns the first MAX_VISIBLE commands).
 *
 * Commands referenced in [recentIds] but no longer present in [commands]
 * (e.g. a deep-link to a quote that's since been deleted) are silently
 * dropped — we never invent rows.
 */
internal fun buildSections(
    query: String,
    commands: List<AegisCommand>,
    recentIds: List<String>,
): List<PaletteSection> {
    if (query.trim().isNotEmpty()) {
        return listOf(PaletteSection(title = null, commands = matchCommands(query, commands)))
    }
    if (recentIds.isEmpty()) {
        return listOf(PaletteSection(title = null, commands = matchCommands(query, commands)))
    }
    val byId = commands.associateBy { it.id }
    val recent = recentIds.mapNotNull { byId[it] }.take(5)
    if (recent.isEmpty()) {
        return listOf(PaletteSection(title = null, commands = matchCommands(query, commands)))
    }
    val recentIdSet = recent.map { it.id }.toSet()
    val tail = commands.filter { it.id !in recentIdSet }.take(MAX_VISIBLE)
    return listOf(
        PaletteSection(title = "Recent", commands = recent),
        PaletteSection(title = "All", commands = tail),
    )
}

/**
 * Section header row — tiny uppercase mono label with horizontal padding that
 * lines up with [CommandRow]'s text column. Non-interactive; the LazyColumn
 * skips it in the highlight index because we render it via `item {}` rather
 * than threading it into the [displayed] list the keyboard nav walks.
 */
@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AegisSpacing.s5,
                end = AegisSpacing.s4,
                top = AegisSpacing.s2,
                bottom = AegisSpacing.s1,
            ),
    ) {
        Text(
            text = title.uppercase(),
            style = AegisTypography.small.copy(
                color = AegisColors.textTertiary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                letterSpacing = 1.sp,
            ),
        )
    }
}
