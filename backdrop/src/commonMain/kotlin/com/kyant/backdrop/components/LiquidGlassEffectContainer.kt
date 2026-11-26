package com.kyant.backdrop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.abs

@Stable
internal class GlassEffectContainerState {
    val slots = mutableStateListOf<ToolbarPairSlot>()
}

internal val LocalGlassEffectContainerState = staticCompositionLocalOf<GlassEffectContainerState?> { null }

/**
 * Coordinates proximity Liquid Glass fusion and shared touch illumination across N child glass
 * groups (`LiquidToolbarGroup` / `LiquidToolbarButton`) laid out inside [content], mirroring
 * SwiftUI's `GlassEffectContainer(spacing:)`.
 *
 * Unlike [LiquidToolbarPair] (which fixes 2 horizontal slots), any arbitrary Row/Column/Box
 * composition may be placed inside [LiquidGlassEffectContainer] and any groups within
 * [spacing] smoothly merge their SDF outlines and specular rims while preserving independent
 * press glow and scale physics.
 */
@Composable
fun LiquidGlassEffectContainer(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    spacing: Dp = LocalLiquidGlassTokens.current.fusionDistance,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    content: @Composable () -> Unit,
) {
    val state = remember { GlassEffectContainerState() }
    val density = LocalDensity.current
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val replacedState = rememberUpdatedState(LocalReplacedMenuSources.current)
    val navigation = LocalNavigationBar.current
    val hasShader = isRuntimeShaderSupported()
    val fill = if (hasShader) appearance.customTintOnly(surfaceColor) else appearance.overlay(surfaceColor)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tintPath = remember { Path() }

    fun activeRects(): List<Pair<ToolbarPairSlot, Rect>> {
        val replaced = replacedState.value
        return state.slots.mapNotNull { slot ->
            if (replaced.any { it.toolbar === slot.group }) return@mapNotNull null
            val rect = slot.visualBounds(origin, density) ?: return@mapNotNull null
            if (rect.width < 2f || rect.height < 2f) return@mapNotNull null
            slot to rect
        }
    }

    fun outline(): Shape {
        val rects = activeRects().map { it.second }
        val spacingPx = with(density) { spacing.toPx().coerceAtLeast(0f) }
        return when (rects.size) {
            0 -> GlassCapsuleShape(Rect.Zero, null, spacingPx)
            1 -> GlassCapsuleShape(rects[0], null, spacingPx)
            2 -> GlassCapsuleShape(rects[0], rects[1], spacingPx)
            else -> GlassCapsulesShape(rects, spacingPx)
        }
    }

    Box(modifier) {
        Spacer(
            Modifier.matchParentSize()
                .layout { measurable, constraints ->
                    // Extra bleed prevents interactive scale/shadows from clipping at container edges.
                    val bleed = 20.dp.roundToPx()
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val layer = measurable.measure(Constraints.fixed(w + 2 * bleed, h + 2 * bleed))
                    layout(w, h) { layer.place(-bleed, -bleed) }
                }
                .onGloballyPositioned { origin = it.localToRoot(Offset.Zero) }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = ::outline,
                    effects = {
                        if (navigation) liquidNavBarButtonLens()
                        else liquidSurfaceLens(appearance.controlBlurRadius, appearance, LiquidGlassRole.Control)
                    },
                    highlight = { rim },
                    shadow = { LiquidSurfaceShadow },
                    onDrawSurface = {
                        if (navigation) drawRect(appearance.navBarButtonFill(appearance.customTintOnly(surfaceColor)))
                        else if (fill != null) drawRect(fill)
                        val slots = activeRects()
                        for ((slot, r) in slots) {
                            val slotTint = slot.group?.surfaceCustomTint ?: continue
                            if (slotTint.alpha <= 0.001f) continue
                            if (abs(r.width - r.height) <= 1f) {
                                val featherRadius = (r.maxDimension * 0.56f).coerceAtLeast(1f)
                                drawRect(
                                    brush = Brush.radialGradient(
                                        0f to slotTint,
                                        0.82f to slotTint,
                                        1f to slotTint.copy(alpha = 0f),
                                        center = r.center,
                                        radius = featherRadius,
                                    ),
                                )
                            } else {
                                tintPath.rewind()
                                tintPath.addLiquidCapsule(r)
                                clipPath(tintPath) { drawRect(slotTint) }
                            }
                        }
                        val brighten = !navigation && !appearance.dark
                        for ((slot, r) in slots) {
                            val light = slot.group?.light ?: continue
                            if (light.pressProgress <= 0f) continue
                            light.drawLightInLobe(this, r, brightenBackdrop = brighten)
                        }
                    },
                ),
        )
        CompositionLocalProvider(
            LocalGlassEffectContainerState provides state,
            content = content,
        )
    }
}
