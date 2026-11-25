package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tracks pointer press and ensures a single smooth highlight pulse without double-flashing on release. */
@Composable
internal fun rememberSinglePressHighlight(
    interaction: MutableInteractionSource,
    minFlashMillis: Long = 100L,
): State<Boolean> {
    val active = remember(interaction) { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        var pressMark: TimeMark? = null
        var releaseJob: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    releaseJob?.cancel()
                    releaseJob = null
                    pressMark = TimeSource.Monotonic.markNow()
                    active.value = true
                }
                is PressInteraction.Release -> {
                    val elapsed = pressMark?.elapsedNow()?.inWholeMilliseconds ?: minFlashMillis
                    val remaining = (minFlashMillis - elapsed).coerceAtLeast(0L)
                    releaseJob?.cancel()
                    releaseJob = launch {
                        if (remaining > 0L) delay(remaining)
                        active.value = false
                    }
                }
                is PressInteraction.Cancel -> {
                    releaseJob?.cancel()
                    releaseJob = null
                    active.value = false
                }
            }
        }
    }
    return active
}

@Immutable
data class LiquidListColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val secondary: Color,
    val selected: Color,
    val separator: Color,
    val contextScrim: Color,
)

@Composable
fun liquidListColors(dark: Boolean = liquidGlassAppearance().dark): LiquidListColors = remember(dark) {
    if (dark) LiquidListColors(Color.Black, Color(0xff1c1c1e), Color(0xfff6f6f8), Color(0xffa1a1a8),
        Color(0xff3a3a3c), Color(0xff38383a), Color.Black.copy(alpha = .50f))
    else LiquidListColors(Color(0xfff2f2f7), Color.White, Color(0xff19191d), Color(0xff686870),
        Color(0xffe5e5ea), Color(0xffc6c6c8).copy(alpha = .75f), Color.Black.copy(alpha = .20f))
}

enum class LiquidListStyle { InsetGrouped, Grouped, Inset, Plain, Sidebar, Cards }

private val LocalListStyle = staticCompositionLocalOf { LiquidListStyle.InsetGrouped }
internal val LocalListItemShape = compositionLocalOf<RoundedCornerShape?> { null }
private val LocalSeparatorInset = staticCompositionLocalOf<MutableState<Dp>?> { null }
private val LocalListRowHighlightCallback = compositionLocalOf<((Boolean) -> Unit)?> { null }
private val LocalListHighlights = staticCompositionLocalOf<MutableMap<String, SnapshotStateMap<Int, Boolean>>?> { null }

@Composable
fun LiquidListRowStyle(style: LiquidListStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalListStyle provides style, content = content)
}
internal val LocalListColors = staticCompositionLocalOf<LiquidListColors?> { null }
internal val LocalSwipeGroup = staticCompositionLocalOf<LiquidSwipeGroup?> { null }

private fun LiquidListStyle.listBackground(colors: LiquidListColors): Color = when (this) {
    LiquidListStyle.Inset, LiquidListStyle.Plain -> colors.surface
    else -> colors.background
}

private fun LiquidListStyle.horizontalContentPadding(): Dp = when (this) {
    LiquidListStyle.Grouped, LiquidListStyle.Plain -> 0.dp
    LiquidListStyle.InsetGrouped, LiquidListStyle.Inset, LiquidListStyle.Sidebar, LiquidListStyle.Cards -> 16.dp
}

private fun LiquidListStyle.hasGroupedSectionSpacing(): Boolean = when (this) {
    LiquidListStyle.InsetGrouped, LiquidListStyle.Grouped, LiquidListStyle.Sidebar -> true
    LiquidListStyle.Inset, LiquidListStyle.Plain, LiquidListStyle.Cards -> false
}

@Composable
fun LiquidList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    colors: LiquidListColors = liquidListColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: LazyListScope.() -> Unit,
) = LiquidList(LiquidListStyle.InsetGrouped, modifier, state, colors, contentPadding, content)

@Composable
fun LiquidList(
    style: LiquidListStyle,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    colors: LiquidListColors = liquidListColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = style.horizontalContentPadding(), vertical = 8.dp),
    content: LazyListScope.() -> Unit,
) {
    val group = remember { LiquidSwipeGroup() }
    val highlights = remember { mutableMapOf<String, SnapshotStateMap<Int, Boolean>>() }
    LaunchedEffect(state, group) { snapshotFlow { state.isScrollInProgress }.collect { if (it) group.active = null } }
    CompositionLocalProvider(
        LocalListColors provides colors,
        LocalSwipeGroup provides group,
        LocalListStyle provides style,
        LocalListHighlights provides highlights,
    ) {
        LazyColumn(modifier.background(style.listBackground(colors)), state = state, contentPadding = contentPadding, content = content)
    }
}

/** Item keys must be stable and unique across sections, including when items move between them. */
fun <T> LazyListScope.liquidListSection(
    sectionKey: String,
    entries: List<T>,
    key: (T) -> Any,
    title: String? = null,
    footer: String? = null,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    if (title != null) item(key = "liquid.section.header.$sectionKey", contentType = "liquid.header") {
        val colors = LocalListColors.current ?: liquidListColors()
        val style = LocalListStyle.current
        LiquidSectionHeaderRow(
            title = title,
            colors = colors,
            insetHorizontal = style == LiquidListStyle.Grouped || style == LiquidListStyle.Plain,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
        )
    }
    val visibleEntries = if (expanded) entries else emptyList()
    itemsIndexed(visibleEntries, key = { _, entry -> key(entry) }) { i, entry ->
        val colors = LocalListColors.current ?: liquidListColors()
        val style = LocalListStyle.current
        val allHighlights = LocalListHighlights.current
        val highlightedRows = remember(allHighlights, sectionKey) {
            allHighlights?.getOrPut(sectionKey) { mutableStateMapOf() } ?: mutableStateMapOf()
        }
        val first = i == 0
        val last = i == visibleEntries.lastIndex
        val top = when (style) {
            LiquidListStyle.Cards -> 24.dp
            LiquidListStyle.InsetGrouped -> if (first) 26.dp else 0.dp
            LiquidListStyle.Sidebar -> if (first) 18.dp else 0.dp
            else -> 0.dp
        }
        val bottom = when (style) {
            LiquidListStyle.Cards -> 24.dp
            LiquidListStyle.InsetGrouped -> if (last) 26.dp else 0.dp
            LiquidListStyle.Sidebar -> if (last) 18.dp else 0.dp
            else -> 0.dp
        }
        val shape = RoundedCornerShape(top, top, bottom, bottom)
        val inset = remember { mutableStateOf(59.dp) }
        val hideSeparator = highlightedRows[i] == true || highlightedRows[i + 1] == true
        val separatorAlpha by animateFloatAsState(if (hideSeparator) 0f else 1f, tween(if (hideSeparator) 40 else 160), label = "List separator alpha")
        DisposableEffect(i) {
            onDispose {
                if (highlightedRows.containsKey(i)) highlightedRows.remove(i)
            }
        }
        val onHighlight = remember(i, highlightedRows) {
            { active: Boolean ->
                if (active) {
                    if (highlightedRows[i] != true) highlightedRows[i] = true
                } else if (highlightedRows.containsKey(i)) {
                    highlightedRows.remove(i)
                }
            }
        }
        Box(Modifier.animateItem().fillMaxWidth().padding(bottom = if (style == LiquidListStyle.Cards) 8.dp else 0.dp)
            .clip(shape).drawWithContent {
            drawContent()
            if (style == LiquidListStyle.Grouped) {
                val border = 0.55.dp.toPx()
                if (first) drawLine(colors.separator.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(0f, border / 2f), androidx.compose.ui.geometry.Offset(size.width, border / 2f), border)
                if (last) drawLine(colors.separator.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(0f, size.height - border / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height - border / 2f), border)
            }
            if (!last && style != LiquidListStyle.Cards && separatorAlpha > .01f) {
                val start = if (style == LiquidListStyle.Plain) 16.dp.toPx() else minOf(size.width, inset.value.toPx())
                val end = (size.width - if (style == LiquidListStyle.Plain || style == LiquidListStyle.Grouped) 0f else 20.dp.toPx()).coerceAtLeast(start)
                val stroke = if (style == LiquidListStyle.Plain) 1f else 0.6.dp.toPx().coerceAtLeast(1f)
                val y = size.height - stroke / 2
                drawLine(colors.separator.copy(alpha = colors.separator.alpha * separatorAlpha),
                    androidx.compose.ui.geometry.Offset(if (layoutDirection == LayoutDirection.Ltr) start else size.width - end, y),
                    androidx.compose.ui.geometry.Offset(if (layoutDirection == LayoutDirection.Ltr) end else size.width - start, y), stroke)
            }
        }) {
            CompositionLocalProvider(
                LocalSeparatorInset provides inset,
                LocalListItemShape provides shape,
                LocalListRowHighlightCallback provides onHighlight,
            ) { row(entry) }
        }
    }
    if (footer != null) item(key = "liquid.section.footer.$sectionKey", contentType = "liquid.footer") {
        val colors = LocalListColors.current ?: liquidListColors()
        val style = LocalListStyle.current
        val padH = if (style == LiquidListStyle.Grouped || style == LiquidListStyle.Plain) 16.dp else 12.dp
        BasicText(footer, Modifier.padding(horizontal = padH, vertical = 7.dp), style = TextStyle(colors.secondary, 13.sp))
    }
    if (visibleEntries.isNotEmpty() || title != null) item(key = "liquid.section.space.$sectionKey", contentType = "liquid.space") {
        if (LocalListStyle.current.hasGroupedSectionSpacing()) Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LiquidSectionHeaderRow(
    title: String,
    colors: LiquidListColors,
    insetHorizontal: Boolean,
    expanded: Boolean,
    onToggleExpanded: (() -> Unit)?,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(LocalLiquidGlassTokens.current.navigation.disclosureRotateMillis),
        label = "ListSectionChevron",
    )
    val mod = Modifier
        .fillMaxWidth()
        .then(
            if (onToggleExpanded != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = onToggleExpanded,
                )
            } else {
                Modifier
            },
        )
        .padding(
            start = if (insetHorizontal) 16.dp else 12.dp,
            end = if (insetHorizontal) 16.dp else 12.dp,
            top = 8.dp,
            bottom = 6.dp,
        )
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = TextStyle(colors.secondary, 14.sp, FontWeight.SemiBold),
        )
        if (onToggleExpanded != null) {
            LiquidIcon(
                LiquidSymbol.ChevronDown,
                colors.secondary,
                Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
            )
        }
    }
}

/**
 * Non-lazy SwiftUI `List { ... }.listStyle(...)` host supporting all native iOS list styles
 * (`InsetGrouped`, `Grouped`, `Inset`, `Plain`, `Sidebar`, `Cards`) safely inside scrollable containers.
 */
@Composable
fun LiquidListContainer(
    style: LiquidListStyle = LiquidListStyle.InsetGrouped,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalListColors provides colors,
        LocalListStyle provides style,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .background(style.listBackground(colors))
                .padding(horizontal = style.horizontalContentPadding(), vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (style.hasGroupedSectionSpacing()) 14.dp else 8.dp),
            content = content,
        )
    }
}

/**
 * Collapsible SwiftUI `Section(isExpanded:)` matching the active [LiquidListStyle] inside [LiquidListContainer].
 */
@Composable
fun LiquidListSection(
    title: String? = null,
    footer: String? = null,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: LiquidFormScope.() -> Unit,
) {
    val colors = LocalListColors.current ?: liquidListColors()
    val style = LocalListStyle.current
    val collector = remember { mutableStateOf<LiquidFormCollector?>(null) }
    val rows = LiquidFormCollector().apply(content).rows
    val controls = LocalLiquidGlassTokens.current.controls
    val highlightedRows = remember { mutableStateMapOf<Int, Boolean>() }
    val fullBleed = style == LiquidListStyle.Grouped || style == LiquidListStyle.Plain

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!title.isNullOrEmpty()) {
            LiquidSectionHeaderRow(
                title = title,
                colors = colors,
                insetHorizontal = fullBleed,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
        ) {
            val containerCorner = when (style) {
                LiquidListStyle.InsetGrouped -> 26.dp
                LiquidListStyle.Sidebar -> 18.dp
                LiquidListStyle.Grouped, LiquidListStyle.Inset, LiquidListStyle.Plain, LiquidListStyle.Cards -> 0.dp
            }
            val rowBackground = if (style == LiquidListStyle.Cards) Color.Transparent else colors.surface
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (containerCorner > 0.dp) Modifier.clip(RoundedCornerShape(containerCorner)) else Modifier)
                    .background(rowBackground),
                verticalArrangement = Arrangement.spacedBy(if (style == LiquidListStyle.Cards) 8.dp else 0.dp),
            ) {
                rows.forEachIndexed { index, spec ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressHighlighted by rememberSinglePressHighlight(interaction)
                    val highlighted = spec.selected || pressHighlighted
                    SideEffect { highlightedRows[index] = highlighted }
                    DisposableEffect(index) { onDispose { highlightedRows.remove(index) } }
                    val rowFill by animateColorAsState(
                        if (highlighted) colors.selected else colors.surface,
                        tween(if (pressHighlighted) controls.listPressMillis else controls.listReleaseMillis),
                        label = "List section row highlight",
                    )
                    val cardShape = if (style == LiquidListStyle.Cards) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)
                    val clickModifier = if (spec.onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = spec.enabled,
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = spec.onClick,
                        )
                    } else {
                        Modifier
                    }
                    val rowPadding = if (spec.inset) {
                        Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                    } else {
                        Modifier
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (style == LiquidListStyle.Cards) Modifier.clip(cardShape) else Modifier)
                            .background(rowFill)
                            .then(clickModifier)
                            .drawWithContent {
                                drawContent()
                                if (style == LiquidListStyle.Grouped) {
                                    val border = 0.55.dp.toPx()
                                    if (index == 0) drawLine(colors.separator.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(0f, border / 2f), androidx.compose.ui.geometry.Offset(size.width, border / 2f), border)
                                    if (index == rows.lastIndex) drawLine(colors.separator.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(0f, size.height - border / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height - border / 2f), border)
                                }
                            }
                            .then(rowPadding),
                        content = spec.content,
                    )
                    if (index < rows.lastIndex && style != LiquidListStyle.Cards) {
                        val hideDivider = highlightedRows[index] == true || highlightedRows[index + 1] == true
                        val dividerAlpha by animateFloatAsState(
                            if (hideDivider) 0f else .55f,
                            tween(if (hideDivider) 35 else 160),
                            label = "List section divider alpha",
                        )
                        val startPad = if (style == LiquidListStyle.Plain) 16.dp else spec.separatorStart
                        val endPad = if (style == LiquidListStyle.Plain || style == LiquidListStyle.Grouped) 0.dp else 16.dp
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = startPad, end = endPad)
                                .height(.55.dp)
                                .background(colors.separator.copy(alpha = dividerAlpha)),
                        )
                    }
                }
            }
        }
        if (!footer.isNullOrEmpty()) {
            BasicText(
                footer,
                Modifier.padding(horizontal = if (fullBleed) 16.dp else 12.dp, vertical = 2.dp),
                style = TextStyle(colors.secondary, 13.sp),
            )
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    collector
}

/** A disclosure opens a branch in place. Activating the label opens the item itself. */
@Composable
fun LiquidOutlineCell(
    title: String, subtitle: String, onClick: () -> Unit, tint: Color, leading: @Composable () -> Unit,
    modifier: Modifier = Modifier, level: Int = 0, expanded: Boolean = false, toggleLabel: String = "",
    onToggle: (() -> Unit)? = null, selected: Boolean = false, trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalListColors.current ?: liquidListColors()
    val indent = 18.dp * level.coerceIn(0, 5)
    val thumb = 44.dp
    val gap = 13.dp
    val disclosureWidth = 44.dp
    LocalSeparatorInset.current?.let { inset -> SideEffect { inset.value = indent + disclosureWidth + thumb + gap } }
    val interaction = remember { MutableInteractionSource() }
    val pressHighlighted by rememberSinglePressHighlight(interaction)
    val controls = LocalLiquidGlassTokens.current.controls
    val highlighted = selected || pressHighlighted
    val notifyHighlight = LocalListRowHighlightCallback.current
    SideEffect { notifyHighlight?.invoke(highlighted) }
    val activeFill = if (colors.background == Color.Black && !selected) Color(0xff2c2c2e) else colors.selected
    val fill by animateColorAsState(
        if (highlighted) activeFill else colors.surface,
        tween(if (pressHighlighted) controls.listPressMillis else controls.listReleaseMillis),
        label = "Outline selection",
    )
    Row(modifier.fillMaxWidth().background(fill).padding(start = indent), verticalAlignment = Alignment.CenterVertically) {
        if (onToggle != null) {
            val toggleInteraction = remember { MutableInteractionSource() }
            val togglePressed by toggleInteraction.collectIsPressedAsState()
            Box(Modifier.size(disclosureWidth).clip(RoundedCornerShape(50))
                .clickable(interactionSource = toggleInteraction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle)
                .graphicsLayer { alpha = if (togglePressed) .45f else 1f }
                .semantics { contentDescription = toggleLabel }, contentAlignment = Alignment.Center) {
                LiquidDisclosureIcon(expanded, tint, Modifier.size(16.dp))
            }
        } else Spacer(Modifier.width(disclosureWidth))
        Row(Modifier.weight(1f).clickable(interactionSource = interaction, indication = null) {
                onClick()
            }
            .semantics { this.selected = selected }.heightIn(min = 60.dp).padding(end = 18.dp, top = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(thumb), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = TextStyle(colors.ink, 16.sp), maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                BasicText(subtitle, style = TextStyle(colors.secondary, 13.sp), maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun LiquidListCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalListColors.current ?: liquidListColors()
    val interaction = remember { MutableInteractionSource() }
    val pressHighlighted by rememberSinglePressHighlight(interaction)
    val swipeGroup = LocalSwipeGroup.current
    val action = if (onClick == null) Modifier else Modifier.clickable(interactionSource = interaction, indication = null,
        enabled = enabled) {
        if (swipeGroup?.active != null) {
            swipeGroup.active = null
        } else {
            onClick()
        }
    }
    val ink = if (enabled) colors.ink else colors.secondary.copy(alpha = .55f)
    val style = LocalListStyle.current
    val start = if (style == LiquidListStyle.Cards) 16.dp else 18.dp
    val slot = when (style) { LiquidListStyle.Cards -> 60.dp; LiquidListStyle.Plain -> 40.dp; else -> 28.dp }
    val gap = if (style == LiquidListStyle.Cards) 8.dp else 13.dp
    LocalSeparatorInset.current?.let { inset -> SideEffect { inset.value = start + if (leading != null) slot + gap else 0.dp } }
    val controls = LocalLiquidGlassTokens.current.controls
    val resting = colors.surface
    val highlighted = selected || pressHighlighted
    val notifyHighlight = LocalListRowHighlightCallback.current
    SideEffect { notifyHighlight?.invoke(highlighted) }
    val activeFill = if (style == LiquidListStyle.Plain && colors.background == Color.Black && !selected) Color(0xff2c2c2e) else colors.selected
    val fill by animateColorAsState(
        if (highlighted) activeFill else resting,
        tween(if (pressHighlighted) controls.listPressMillis else controls.listReleaseMillis),
        label = "Row selection",
    )
    val minimum = when {
        style == LiquidListStyle.Cards -> 88.dp
        subtitle != null -> 66.dp
        else -> 51.dp
    }
    CompositionLocalProvider(LocalLiquidContentColor provides ink) {
        Row(modifier.fillMaxWidth().background(fill)
            .then(action).semantics { this.selected = selected }.heightIn(min = minimum)
            .padding(start = start, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (leading != null) Box(Modifier.width(slot), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = TextStyle(ink, 17.sp, if (style == LiquidListStyle.Cards) FontWeight.SemiBold else FontWeight.Normal),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle != null) BasicText(subtitle, style = TextStyle(colors.secondary.copy(alpha = if (enabled) 1f else .55f), 14.sp))
            }
            trailing?.invoke()
        }
    }
}
