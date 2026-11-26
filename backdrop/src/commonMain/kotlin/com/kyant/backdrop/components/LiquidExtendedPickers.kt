package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * SwiftUI `Picker(.inline)` rendering selectable rows with trailing iOS checkmarks
 * and native full-bleed press highlights inside a grouped card.
 */
@Composable
fun LiquidInlinePicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = Color.Unspecified,
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val dark = liquidGlassAppearance().dark
    val resolvedTint = if (tint.isSpecified) tint else if (dark) Color(0xff0a84ff) else Color(0xff007aff)
    val haptics = rememberLiquidHaptics()
    LiquidFormSection(title = title, modifier = modifier.selectableGroup(), colors = colors) {
        options.forEach { option ->
            val isChosen = option == selected
            row(
                inset = false,
                onClick = {
                    if (!isChosen) haptics(LiquidHaptic.SelectionTick)
                    onSelect(option)
                },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .heightIn(min = 28.dp)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isChosen
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText(option, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedVisibility(
                        visible = isChosen,
                        enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.72f),
                        exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.72f),
                    ) {
                        LiquidIcon(LiquidSymbol.Check, resolvedTint, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Immutable
data class LiquidPaletteOption(
    val id: String,
    val label: String,
    val color: Color = Color.Unspecified,
    val symbol: LiquidSymbol? = null,
)

/**
 * SwiftUI `Picker(.palette)` compact horizontal selector displaying color swatches or icon pills
 * with an animated iOS selection ring and press spring.
 */
@Composable
fun LiquidPalettePicker(
    options: List<LiquidPaletteOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val dark = liquidGlassAppearance().dark
    val resolvedTint = if (tint.isSpecified) tint else if (dark) Color(0xff0a84ff) else Color(0xff007aff)
    val haptics = rememberLiquidHaptics()
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isChosen = option.id == selectedId
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val ringAlpha by animateFloatAsState(if (isChosen) 1f else 0f, tween(160), label = "Palette ring")
            val pressScale by animateFloatAsState(
                if (pressed) 0.90f else if (isChosen) 1.06f else 1f,
                GlassSpring.snappy(0.24f).spec,
                label = "Palette scale",
            )
            val fill = if (option.color.isSpecified) option.color else if (dark) Color(0xff2c2c2e) else Color(0xffe5e5ea)
            val rawRingColor = if (option.color.isSpecified) option.color else resolvedTint
            val ringColor = if (dark && rawRingColor.luminance() < 0.15f) Color(0xFF8E8E93)
            else if (!dark && rawRingColor.luminance() > 0.85f) Color(0xFFAEAEB2)
            else rawRingColor
            Box(
                Modifier.size(44.dp)
                    .padding(3.dp)
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                    .clip(CircleShape)
                    .border(2.2.dp, ringColor.copy(alpha = ringAlpha), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .selectable(selected = isChosen, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        if (!isChosen) haptics(LiquidHaptic.SelectionTick)
                        onSelect(option.id)
                    }
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center,
            ) {
                if (option.symbol != null) {
                    val iconTint = if (option.color.isSpecified) liquidContentColorFor(option.color)
                    else if (isChosen) resolvedTint else colors.ink
                    LiquidIcon(option.symbol, iconTint, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Immutable
data class LiquidTabOption(
    val id: String,
    val label: String,
    val symbol: LiquidSymbol? = null,
)

/**
 * iOS 27 `Picker(.tabs)` (`TabPickerStyle`): horizontal capsule picker matching native
 * resting drop shadow + interactive lifted Liquid Glass refractive lens on press/drag/glide,
 * sharing the exact two-layer optical architecture and spring physics of `LiquidSegmentedPicker`.
 */
@Composable
fun LiquidTabsPicker(
    options: List<LiquidTabOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    tint: Color = Color.Unspecified,
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val tokens = LocalLiquidGlassTokens.current
    val controls = tokens.controls
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    val resolvedTint = if (tint.isSpecified) tint else if (dark) Color(0xff0a84ff) else Color(0xff007aff)
    val activeInkColor = if (dark && !tint.isSpecified) Color.White else if (dark) Color(0xFF4DA3FF) else resolvedTint
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentSelect by rememberUpdatedState(onSelect)
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val count = options.size

    var trackWidthPx by remember { mutableIntStateOf(0) }
    var finger by remember(options, rtl) { mutableStateOf<Float?>(null) }
    var tracking by remember(options, rtl) { mutableStateOf(false) }
    var pull by remember(options, rtl) { mutableStateOf(Offset.Zero) }

    val destination = finger ?: selectedIndex.toFloat()
    val travel = updateTransition(destination, label = "TabsPicker edges")
    val startEdge by travel.animateFloat(
        transitionSpec = {
            val leading = targetState < initialState
            when {
                tracking -> (if (leading) controls.segmentedTracking else controls.segmentedTrackingTrailing).spec
                leading -> controls.segmentedSelection.spec
                else -> controls.segmentedTrailingEdge.spec
            }
        },
        label = "TabsPicker start edge",
    ) { it }
    val endEdge by travel.animateFloat(
        transitionSpec = {
            val leading = targetState > initialState
            when {
                tracking -> (if (leading) controls.segmentedTracking else controls.segmentedTrackingTrailing).spec
                leading -> controls.segmentedSelection.spec
                else -> controls.segmentedTrailingEdge.spec
            }
        },
        label = "TabsPicker end edge",
    ) { it }

    val yOffset by animateFloatAsState(
        pull.y,
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.centerRelease.spec,
        label = "TabsPicker vertical pull",
    )
    val aspect by animateFloatAsState(
        abs(pull.y) - abs(pull.x),
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.release.spec,
        label = "TabsPicker aspect",
    )

    val tapLift = remember { Animatable(0f) }
    var prevIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragCommitIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedIndex) {
        val tapped = selectedIndex != prevIndex && dragCommitIndex != selectedIndex
        prevIndex = selectedIndex
        dragCommitIndex = null
        if (tapped) {
            tapLift.animateTo(.94f, tween(60))
            delay(65)
            tapLift.animateTo(0f, controls.segmentedSelection.spec)
        } else {
            tapLift.snapTo(0f)
        }
    }

    val heldStrength by animateFloatAsState(
        if (finger != null) 1f else 0f,
        if (finger != null) tokens.press.engage.spec else tokens.press.centerRelease.spec,
        label = "TabsPicker held strength",
    )
    val edgeStrain = (endEdge - startEdge).coerceIn(-.70f, .70f)
    val activeGlass = maxOf(heldStrength, tapLift.value, abs(edgeStrain) * 1.65f).coerceIn(0f, 1f)

    val trackLayer = rememberLayerBackdrop()
    val fallbackSurface = rememberSolidColorBackdrop(colors.surface)
    val cardOrSceneBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop ?: fallbackSurface
    val lensBackdrop = rememberCombinedBackdrop(cardOrSceneBackdrop, trackLayer)
    val rim = rememberLiquidRimHighlight(dark, layeredEdge = false)

    val trackFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .10f)
    val restingThumbFill = if (dark) Color(0xFF3A3A3C) else Color.White

    val density = LocalDensity.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val haptics = rememberLiquidHaptics()
    val supportsRuntimeShader = remember { isRuntimeShaderSupported() }
    val trackHeight = 36.dp
    val innerPad = 2.dp
    val pillHeight = trackHeight - innerPad * 2

    // Outer Box stays unclipped so the lifted glass lens can expand cleanly beyond the track bounds.
    Box(
        modifier.fillMaxWidth().height(trackHeight)
            .onSizeChanged { trackWidthPx = it.width }
            .selectableGroup()
            .pointerInput(options, rtl, trackWidthPx) {
                if (trackWidthPx <= 0 || options.isEmpty()) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    fun logicalX(posX: Float) = if (rtl) trackWidthPx - posX else posX
                    val downIdx = segmentedRubberBandIndex(
                        logicalX(down.position.x),
                        trackWidthPx.toFloat(),
                        options.size,
                    ).roundToInt().coerceIn(0, options.lastIndex)
                    // Disambiguate vertical scrolling before claiming/consuming drag even on the active tab.
                    var claimed = false
                    var lastHoverIdx = downIdx
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val p = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = abs(p.position.x - down.position.x)
                            val dy = abs(p.position.y - down.position.y)
                            val heldLongEnough = p.uptimeMillis - down.uptimeMillis >= 115L
                            if (!claimed && !heldLongEnough && dy > slop && dy > dx * 1.15f) {
                                break
                            }
                            if (!claimed && ((dx > slop && dx >= dy) || heldLongEnough)) {
                                claimed = true
                                tracking = true
                                if (downIdx == currentSelectedIndex) finger = downIdx.toFloat()
                            }
                            if (claimed) {
                                val lx = logicalX(p.position.x)
                                val slotWidth = trackWidthPx.toFloat() / options.size.toFloat()
                                val rawIdx = (lx - slotWidth * 0.5f) / slotWidth
                                val clamped = rawIdx.coerceIn(0f, options.lastIndex.toFloat())
                                val banded = segmentedRubberBandIndex(lx, trackWidthPx.toFloat(), options.size)
                                val hoverIdx = banded.roundToInt().coerceIn(0, options.lastIndex)
                                if (hoverIdx != lastHoverIdx) {
                                    lastHoverIdx = hoverIdx
                                    haptics(LiquidHaptic.SelectionTick)
                                }
                                finger = banded
                                pull = Offset(
                                    tanh(rawIdx - clamped),
                                    tanh((p.position.y - down.position.y) / size.height.coerceAtLeast(1)),
                                )
                                p.consume()
                            }
                            if (!p.pressed) {
                                val (endIdx, wasDrag) = resolveSegmentedCommitIndex(
                                    claimed = claimed,
                                    downIdx = downIdx,
                                    fingerIdx = finger,
                                    count = options.size,
                                )
                                if (wasDrag && endIdx != currentSelectedIndex) {
                                    dragCommitIndex = endIdx
                                } else if (!wasDrag && endIdx != currentSelectedIndex) {
                                    haptics(LiquidHaptic.SelectionTick)
                                }
                                currentSelect(options[endIdx].id)
                                p.consume()
                                break
                            }
                        }
                    } finally {
                        tracking = false
                        finger = null
                        pull = Offset.Zero
                    }
                }
            },
    ) {
        val centerIdx = ((startEdge + endEdge) * .5f).coerceIn(-.18f, (count - 1).toFloat() + .18f)
        val innerPadPx = with(density) { innerPad.toPx() }
        val innerWidthPx = (trackWidthPx.toFloat() - innerPadPx * 2f).coerceAtLeast(0f)
        val slotPx = if (count > 0) innerWidthPx / count.toFloat() else 0f
        val restingThumbWidthPx = slotPx.coerceAtLeast(1f)
        val restingTravelPx = (innerWidthPx - restingThumbWidthPx).coerceAtLeast(0f)
        val normalizedPos = if (count <= 1) 0f else centerIdx / (count - 1).toFloat()
        val logicalOffset = normalizedPos * restingTravelPx
        val thumbOffsetX = if (rtl) innerPadPx + restingTravelPx - logicalOffset else innerPadPx + logicalOffset

        val (sx, sy) = segmentedLensScale(
            activeGlass = activeGlass,
            travelStrain = edgeStrain,
            widthGain = controls.segmentedLensWidthGain,
            heightGain = controls.segmentedLensHeightGain,
            travelStretch = controls.segmentedTravelStretch,
            pullAspect = aspect,
        )
        val lensW = (restingThumbWidthPx * sx).roundToInt().coerceAtLeast(1)
        val lensH = (with(density) { pillHeight.toPx() } * sy).roundToInt().coerceAtLeast(1)
        val lensCenterX = thumbOffsetX + restingThumbWidthPx * .5f
        val lensCenterY = with(density) { (trackHeight * .5f).toPx() } + yOffset * with(density) { 2.5.dp.toPx() }

        // Layer 1: Track + resting elevated pill (soft drop shadow, NO harsh border!) + subtle separators.
        Box(
            Modifier.fillMaxSize().layerBackdrop(trackLayer, enabled = activeGlass > .01f)
                .clip(LiquidCapsule).background(trackFill),
        ) {
            if (trackWidthPx > 0) {
                val restingAlpha = (1f - activeGlass * 1.85f).coerceIn(0f, 1f)
                Spacer(
                    Modifier
                        .offset { IntOffset(thumbOffsetX.roundToInt(), innerPadPx.roundToInt()) }
                        .width(with(density) { restingThumbWidthPx.toDp() })
                        .height(pillHeight)
                        .graphicsLayer { alpha = restingAlpha }
                        .drawBackdrop(
                            backdrop = emptyBackdrop(),
                            shape = { LiquidCapsule },
                            effects = {},
                            highlight = null,
                            shadow = {
                                Shadow(
                                    radius = 2.5.dp,
                                    offset = DpOffset(0.dp, 0.8.dp),
                                    color = Color.Black.copy(alpha = if (dark) .18f else .032f),
                                )
                            },
                            innerShadow = null,
                            onDrawSurface = { drawRect(restingThumbFill) },
                        ),
                )
            }
        }

        // Layer 2: Lifted Floating Liquid Glass Lens rendered OVER the track during press / drag / glide.
        if (trackWidthPx > 0 && activeGlass > .01f) {
            Spacer(
                Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(Constraints.fixed(lensW, lensH))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val lx = (lensCenterX - lensW * .5f).roundToInt()
                            val ly = (lensCenterY - lensH * .5f).roundToInt()
                            p.place(lx, ly)
                        }
                    }
                    .drawBackdrop(
                        backdrop = lensBackdrop,
                        shape = { LiquidCapsule },
                        effects = {
                            val g = activeGlass
                            blur(appearance.controlBlurRadius.toPx() * .30f * (1f - g * .5f))
                            lens(
                                refractionHeight = 8.5.dp.toPx() * g,
                                refractionAmount = 18.dp.toPx() * g,
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            rim.copy(
                                width = 1.0.dp,
                                alpha = ((if (!dark) .94f else 1f) * tokens.highlightIntensity * activeGlass).coerceIn(0f, 1f),
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = (10.dp * activeGlass).coerceAtLeast(.5.dp),
                                offset = DpOffset(0.dp, 2.5.dp * activeGlass),
                                color = Color.Black.copy(alpha = (if (dark) .28f else .08f) * activeGlass),
                            )
                        },
                        innerShadow = if (dark) {
                            {
                                InnerShadow(
                                    radius = 2.dp * activeGlass,
                                    alpha = .18f * activeGlass,
                                )
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            if (!supportsRuntimeShader) {
                                drawRect(restingThumbFill.copy(alpha = androidx.compose.ui.util.lerp(.96f, .45f, activeGlass)))
                            } else {
                                val wash = if (dark) Color.White.copy(alpha = .07f * activeGlass)
                                else Color.White.copy(alpha = .18f * activeGlass)
                                drawRect(wash)
                            }
                        },
                    ),
            )
        }

        // Layer 3: Crisp tab icons + labels with TabInkShader refraction & chromatic dispersion under active lens.
        val inkLayer = rememberGraphicsLayer()
        val lensInk = rememberGraphicsLayer()
        val inkShaderHolder = remember { arrayOfNulls<RuntimeShader>(1) }
        val tabTokens = tokens.tabs
        Row(
            Modifier.fillMaxSize().drawWithCache {
                val lensPath = Path()
                val inkMargin = kotlin.math.ceil((6.dp + 1.8.dp + 4.dp).toPx()).toInt()
                onDrawWithContent {
                    if (trackWidthPx <= 0 || activeGlass <= .01f) {
                        drawContent()
                        return@onDrawWithContent
                    }
                    inkLayer.record {
                        this@onDrawWithContent.drawContent()
                    }
                    val lensRect = Rect(
                        left = lensCenterX - lensW * .5f,
                        top = lensCenterY - lensH * .5f,
                        right = lensCenterX + lensW * .5f,
                        bottom = lensCenterY + lensH * .5f,
                    )
                    lensPath.reset()
                    lensPath.addLiquidCapsule(lensRect)
                    clipPath(lensPath, ClipOp.Difference) {
                        drawLayer(inkLayer)
                    }
                    clipPath(lensPath) {
                        val inkShader = if (supportsRuntimeShader) {
                            inkShaderHolder[0] ?: SharedRenderEffectShaderCache.obtainRuntimeShader("TabInkShader", TabInkShader).also { inkShaderHolder[0] = it }
                        } else null
                        if (inkShader != null && count > 0) {
                            val cropW = kotlin.math.ceil(restingThumbWidthPx * 1.80f + 2f * inkMargin).toInt().coerceAtLeast(1)
                            val cropH = kotlin.math.ceil(pillHeight.toPx() * 1.95f + 2f * inkMargin).toInt().coerceAtLeast(1)
                            val cropLeft = (lensCenterX - cropW * .5f).roundToInt().toFloat()
                            val cropTop = (lensCenterY - cropH * .5f).roundToInt().toFloat()
                            val firstSlot = innerPadPx + slotPx * .5f
                            inkShader.setFloatUniform(
                                "rect",
                                lensCenterX - cropLeft,
                                lensCenterY - cropTop,
                                lensW * .5f,
                                lensH * .5f,
                            )
                            inkShader.setFloatUniform("strength", activeGlass)
                            inkShader.setFloatUniform("density", density.density)
                            inkShader.setFloatUniform(
                                "grid",
                                firstSlot - cropLeft,
                                size.height * .5f - cropTop,
                                slotPx,
                                controls.segmentedLensMagnification * activeGlass,
                            )
                            inkShader.setFloatUniform("dispersionPx", tabTokens.dispersion.toPx())
                            inkShader.setFloatUniform("bevelWidth", 8.dp.toPx())
                            inkShader.setFloatUniform("refractionPx", 5.dp.toPx())
                            inkShader.setFloatUniform("darkSurface", if (dark) 1f else 0f)
                            inkShader.setFloatUniform("sceneOffset", 0f)
                            inkShader.setFloatUniform("vibrancy", 0f)
                            inkShader.setFloatUniform("backing", 0f)
                            inkShader.setColorUniform("ink", activeInkColor)
                            lensInk.renderEffect = RuntimeShaderEffect(inkShader, "content")
                            lensInk.record(size = IntSize(cropW, cropH)) {
                                translate(-cropLeft, -cropTop) { drawLayer(inkLayer) }
                            }
                            translate(cropLeft, cropTop) { drawLayer(lensInk) }
                        } else {
                            val mag = 1f + controls.segmentedLensMagnification * activeGlass
                            withTransform({
                                scale(mag, mag, Offset(lensCenterX, lensCenterY))
                            }) {
                                drawLayer(inkLayer)
                            }
                        }
                    }
                }
            }.padding(horizontal = innerPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { idx, option ->
                val active = option.id == selectedId
                val highlighted = if (finger != null) idx == centerIdx.roundToInt().coerceIn(0, options.lastIndex) else active
                val ink by animateColorAsState(
                    if (highlighted) activeInkColor else colors.ink.copy(alpha = .78f),
                    tween(140),
                    label = "TabsPicker ink",
                )
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = active
                            contentDescription = option.label
                            onClick { currentSelect(option.id); true }
                        }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (option.symbol != null) {
                        LiquidIcon(option.symbol, ink, Modifier.size(16.dp))
                    }
                    BasicText(
                        option.label,
                        style = TextStyle(ink, 14.sp, if (highlighted) FontWeight.SemiBold else FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

enum class LiquidSafeAreaEdge { Top, Bottom }

/**
 * iOS 27 `.safeAreaBar(edge:)` edge-attached frosted Liquid Glass bar with progressive blur
 * and soft vertical fade mask so scrolling content glides cleanly beneath.
 */
@Composable
fun LiquidSafeAreaBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    edge: LiquidSafeAreaEdge = LiquidSafeAreaEdge.Bottom,
    colors: LiquidListColors = liquidListColors(),
    blurRadius: Dp = 16.dp,
    respectSystemBars: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = liquidGlassAppearance().dark
    val maskBrush = remember(edge) {
        if (edge == LiquidSafeAreaEdge.Top) {
            Brush.verticalGradient(0f to Color.White, .78f to Color.White.copy(alpha = .94f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, .22f to Color.White.copy(alpha = .94f), 1f to Color.White)
        }
    }
    val scrimBrush = remember(edge, dark, colors.background) {
        val strong = colors.background.copy(alpha = if (dark) .88f else .86f)
        val mid = colors.background.copy(alpha = if (dark) .74f else .72f)
        if (edge == LiquidSafeAreaEdge.Top) {
            Brush.verticalGradient(0f to strong, .72f to mid, 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, .28f to mid, 1f to strong)
        }
    }
    val safeMod = when {
        !respectSystemBars -> Modifier
        edge == LiquidSafeAreaEdge.Top -> Modifier.liquidTopSafeArea()
        else -> Modifier.liquidBottomSafeArea()
    }
    Box(modifier.fillMaxWidth()) {
        Spacer(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(maskBrush, blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(blurRadius.toPx()) },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { drawRect(scrimBrush) },
                ),
        )
        Row(
            Modifier.fillMaxWidth()
                .then(safeMod)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
