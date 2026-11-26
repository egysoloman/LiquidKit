package com.kyant.backdrop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil
import kotlin.math.roundToInt

data class LiquidTab<Key>(val key: Key, val label: String, val symbol: LiquidSymbol)

/**
 * A floating Liquid Glass tab bar mirroring iOS 26/27 `TabView`, supporting:
 * - continuous dragged lens refraction across tabs (`LiquidBottomTabsLayout`)
 * - optional detached circular Liquid Glass search orb (`searchAction`, matching `Tab(role: .search)`)
 * - smooth spring morphing into a compact active orb on scroll (`minimized`, matching `.tabBarMinimizeBehavior(.onScrollDown)`)
 */
@Composable
fun <Key> LiquidTabBar(
    tabs: List<LiquidTab<Key>>,
    selected: Key,
    onSelect: (Key) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
) = LiquidTabBar(
    tabs = tabs,
    selected = selected,
    onSelect = onSelect,
    backdrop = backdrop,
    tint = tint,
    modifier = modifier,
    minimized = false,
    onExpandFromMinimized = null,
    searchAction = null,
)

@Composable
fun <Key> LiquidTabBar(
    tabs: List<LiquidTab<Key>>,
    selected: Key,
    onSelect: (Key) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    minimized: Boolean = false,
    onExpandFromMinimized: (() -> Unit)? = null,
    searchAction: (() -> Unit)? = null,
    searchLabel: String = "搜索",
    searchSelected: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val appearance = liquidGlassAppearance()
    val haptics = rememberLiquidHaptics()
    val activeTab = tabs.firstOrNull { it.key == selected } ?: tabs.first()
    val hasSearch = searchAction != null
    val minimizeProgress by animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = GlassSpring.swiftUI(response = 0.36f, dampingFraction = 0.84f).spec,
        label = "LiquidTabBar minimize",
    )
    val p = minimizeProgress.coerceIn(0f, 1f)
    val tabsFadeProgress = ((p - 0.15f) / 0.55f).coerceIn(0f, 1f)
    val showTabs = p < 0.85f
    val showMinimizedOrb = p > 0.15f

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compactHorizontal = maxWidth >= 560.dp
        val expandedHeight = if (compactHorizontal) 48.dp else 56.dp
        val minimizedOrbSize = if (compactHorizontal) 48.dp else 52.dp
        val slotCount = tabs.size + if (hasSearch) 1 else 0
        val availableBarWidth = (maxWidth - 16.dp).coerceAtLeast(1.dp)
        val slot = if (compactHorizontal) {
            minOf(116.dp, availableBarWidth / slotCount)
        } else {
            minOf(89.dp, (availableBarWidth - 8.dp).coerceAtLeast(1.dp) / slotCount)
        }
        val expandedBarWidth = slot * slotCount + 8.dp
        val currentHeight = lerp(expandedHeight, minimizedOrbSize, p)
        val currentBarWidth = lerp(expandedBarWidth, minimizedOrbSize, p)
        val index = if (searchSelected && hasSearch) tabs.size else tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)

        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                val morphClip = if (p > 0.01f && p < 0.99f) Modifier.clip(LiquidCapsule) else Modifier
                Box(Modifier.size(currentBarWidth, currentHeight).then(morphClip), contentAlignment = Alignment.Center) {
                    if (showTabs) {
                        LiquidBottomTabsLayout(
                            selectedTabIndex = { index },
                            onTabSelected = { idx ->
                                if (!minimized) {
                                    if (searchAction != null && idx == tabs.size) {
                                        haptics(LiquidHaptic.SelectionTick)
                                        searchAction()
                                    } else {
                                        onSelect(tabs[idx].key)
                                    }
                                }
                            },
                            backdrop = backdrop,
                            tabsCount = slotCount,
                            modifier = Modifier
                                .requiredWidth(expandedBarWidth)
                                .height(currentHeight)
                                .graphicsLayer { alpha = 1f - tabsFadeProgress }
                                .selectableGroup(),
                            tint = tint,
                            height = currentHeight,
                        ) {
                            for (tab in tabs) {
                                val chosen = !searchSelected && tab.key == selected
                                LiquidBottomTab(
                                    onClick = { if (!minimized) onSelect(tab.key) },
                                    modifier = Modifier.semantics { this.selected = chosen },
                                ) {
                                    if (compactHorizontal) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            LiquidIcon(tab.symbol, appearance.contentColor, Modifier.size(18.dp))
                                            BasicText(
                                                tab.label,
                                                modifier = Modifier.weight(1f, fill = false),
                                                style = TextStyle(appearance.contentColor, 13.sp, FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    } else {
                                        LiquidIcon(tab.symbol, appearance.contentColor, Modifier.size(23.dp))
                                        BasicText(
                                            tab.label,
                                            style = TextStyle(appearance.contentColor, 10.sp, FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (searchAction != null) {
                                LiquidBottomTab(
                                    onClick = {
                                        if (!minimized) {
                                            haptics(LiquidHaptic.SelectionTick)
                                            searchAction()
                                        }
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = searchLabel
                                        this.selected = searchSelected
                                    },
                                ) {
                                    if (compactHorizontal) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            LiquidIcon(LiquidSymbol.Search, appearance.contentColor, Modifier.size(18.dp))
                                            BasicText(
                                                searchLabel,
                                                modifier = Modifier.weight(1f, fill = false),
                                                style = TextStyle(appearance.contentColor, 13.sp, FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    } else {
                                        LiquidIcon(LiquidSymbol.Search, appearance.contentColor, Modifier.size(23.dp))
                                        BasicText(
                                            searchLabel,
                                            style = TextStyle(appearance.contentColor, 10.sp, FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showMinimizedOrb) {
                        val iconColor = if (tint.isSpecified) tint else appearance.contentColor
                        LiquidTabOrbSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabsFadeProgress }
                                .semantics { contentDescription = activeTab.label },
                            shape = if (p > 0.9f) CircleShape else LiquidCapsule,
                            interactive = minimized,
                            onClick = if (minimized) {
                                {
                                    haptics(LiquidHaptic.SelectionTick)
                                    onExpandFromMinimized?.invoke() ?: onSelect(activeTab.key)
                                }
                            } else {
                                null
                            },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LiquidIcon(activeTab.symbol, iconColor, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (searchAction != null && p > 0.02f) {
                    val activeSearchColor = if (searchSelected && tint.isSpecified) tint else appearance.contentColor
                    val searchOrbTint = if (searchSelected && tint.isSpecified) {
                        tint.copy(alpha = if (appearance.dark) .20f else .14f)
                    } else {
                        Color.Unspecified
                    }
                    LiquidTabOrbSurface(
                        backdrop = backdrop,
                        modifier = Modifier
                            .size(currentHeight)
                            .graphicsLayer {
                                alpha = tabsFadeProgress
                                val s = lerp(0.84f, 1f, p)
                                scaleX = s
                                scaleY = s
                            }
                            .semantics {
                                contentDescription = searchLabel
                                this.selected = searchSelected
                            },
                        shape = CircleShape,
                        tint = searchOrbTint,
                        interactive = true,
                        onClick = {
                            haptics(LiquidHaptic.SelectionTick)
                            searchAction()
                        },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LiquidIcon(
                                LiquidSymbol.Search,
                                activeSearchColor,
                                Modifier.size(22.dp),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val bar = placeables[0]
            val search = placeables.getOrNull(1)
            val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else bar.width
            val layoutHeight = maxOf(bar.height, search?.height ?: 0)
            val expandedBarWidthPx = expandedBarWidth.roundToPx()
            val centeredBarX = (layoutWidth - bar.width) / 2f
            val minimizedBarX = if (hasSearch) 0f else (layoutWidth - bar.width) / 2f
            val barX = lerp(centeredBarX, minimizedBarX, p).roundToInt()
            layout(layoutWidth, layoutHeight) {
                if (search != null) {
                    val expandedRightSlotX = (layoutWidth + expandedBarWidthPx) / 2f - search.width
                    val minimizedSearchX = (layoutWidth - search.width).toFloat()
                    val searchX = lerp(expandedRightSlotX, minimizedSearchX, p).roundToInt()
                    search.placeRelative(searchX, (layoutHeight - search.height) / 2)
                }
                bar.placeRelative(barX, (layoutHeight - bar.height) / 2)
            }
        }
    }
}

/** Shares the exact `TabMaterialShader` glass response so the search & minimized orbs match the adjacent tab bar. */
@Composable
private fun LiquidTabOrbSurface(
    backdrop: Backdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isRuntimeShaderSupported()) {
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            variant = LiquidGlassVariant.Regular,
            tint = tint,
            interactive = interactive,
            onClick = onClick,
            content = content,
        )
        return
    }
    val tokens = LocalLiquidGlassTokens.current
    val tab = tokens.tabs
    val appearance = liquidGlassAppearance()
    val backing = tab.backingFraction(appearance)
    val plateRim = rememberLiquidRimHighlight(appearance.dark, layeredEdge = true, reflectionInSurface = true)
    val scope = rememberCoroutineScope()
    val highlight = if (interactive) {
        remember(scope, tokens) {
            InteractiveHighlight(scope, tokens.press, intensity = tokens.highlightIntensity)
        }
    } else {
        null
    }
    val interactionMod = when {
        highlight != null && onClick != null ->
            highlight.gestureModifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        highlight != null -> highlight.gestureModifier
        onClick != null -> Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    val motionMod = if (highlight != null) Modifier.graphicsLayer { highlight.motion.apply(this) } else Modifier
    val hasTint = tint.isSpecified && tint.alpha > 0f
    Box(
        modifier
            .then(interactionMod)
            .then(motionMod)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    val spread = (if (appearance.dark) tab.darkDiffusionRadius else tab.diffusionRadius).toPx()
                    padding = ceil(spread * 2.5f + 3.dp.toPx())
                    runtimeShaderEffect("Tab backing material", TabMaterialShader, "content") {
                        setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
                        setFloatUniform("inset", padding)
                        setFloatUniform("density", density)
                        setFloatUniform("spread", spread)
                        setFloatUniform("softMix", tab.diffusionMix)
                        setFloatUniform("reflection", tab.rimReflection * tokens.highlightIntensity)
                        setFloatUniform("hairline", tab.topHairlineOpacity * tokens.highlightIntensity.coerceIn(0f, 1f))
                        setFloatUniform("dark", if (appearance.dark) 1f else 0f)
                        setFloatUniform("backing", backing)
                        setFloatUniform("active", 0f)
                    }
                },
                highlight = { plateRim.copy(alpha = (tab.restingPlateRimStrength * tokens.highlightIntensity).coerceIn(0f, 1f)) },
                shadow = { LiquidSurfaceShadow },
                onDrawSurface = {
                    if (hasTint) drawRect(tint)
                    highlight?.drawLight(this, brightenBackdrop = !appearance.dark)
                },
            ),
        propagateMinConstraints = true,
        content = content,
    )
}
