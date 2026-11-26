package io.liquidkit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.morph.LiquidMorphAlignment
import com.kyant.backdrop.morph.LiquidMorphConfig
import com.kyant.backdrop.morph.LiquidMorphContainer

@Immutable
data class LiquidKitSharedHeroItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val symbol: LiquidKitSymbol,
    val tint: Color,
    val summary: String,
)

@Immutable
data class LiquidKitSharedPlayerTrack(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val tint: Color,
    val isPlaying: Boolean = true,
    val progress: Float = 0.42f,
)

/**
 * Hero card <-> detail shared-element transition powered by Compose [SharedTransitionLayout].
 *
 * A sibling [LiquidKitLayerBackdrop] captures the ambient gradient substrate underneath
 * [SharedTransitionLayout] so live glass surfaces keep valid refraction even while flying in
 * the transition overlay.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LiquidKitSharedHeroTransition(
    items: List<LiquidKitSharedHeroItem>,
    selectedId: String?,
    onSelectId: (String?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    spring: LiquidKitSpring = LiquidKitSpring.swiftUI(response = 0.36f, dampingFraction = 0.84f),
    zoomDetailContent: (@Composable (LiquidKitSharedHeroItem) -> Unit)? = null,
) {
    val safeColumns = columns.coerceIn(1, 3)
    val heroSubstrateBackdrop = rememberLiquidKitBackdrop()
    val effectiveBackdrop = rememberCombinedLiquidKitBackdrop(backdrop, heroSubstrateBackdrop)
    val boundsTransform = BoundsTransform { _, _ -> spring.rectSpec }
    val selectedItem = items.firstOrNull { it.id == selectedId }
    val isDark = liquidKitAppearance().dark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
    ) {
        // Drawn behind SharedTransitionLayout so overlay flight never invalidates the refracted backdrop.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .liquidKitBackdrop(heroSubstrateBackdrop),
        ) {
            val baseA = if (isDark) Color(0xFF101828) else Color(0xFFEAF2FF)
            val baseB = if (isDark) Color(0xFF1A142D) else Color(0xFFF6ECFF)
            drawRect(Brush.linearGradient(listOf(baseA, baseB)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0A84FF).copy(alpha = if (isDark) 0.36f else 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.22f),
                    radius = size.maxDimension * 0.46f,
                ),
                radius = size.maxDimension * 0.46f,
                center = Offset(size.width * 0.22f, size.height * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF30D158).copy(alpha = if (isDark) 0.28f else 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.maxDimension * 0.44f,
                ),
                radius = size.maxDimension * 0.44f,
                center = Offset(size.width * 0.82f, size.height * 0.78f),
            )
        }

        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            AnimatedContent(
                targetState = selectedItem?.id,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "LiquidKitSharedHero",
            ) { currentId ->
                val detail = items.firstOrNull { it.id == currentId }
                if (detail == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.chunked(safeColumns).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowItems.forEach { item ->
                                    val cardShape = RoundedCornerShape(22.dp)
                                    LiquidKitGlassSurface(
                                        backdrop = effectiveBackdrop,
                                        shape = cardShape,
                                        tint = item.tint.copy(alpha = 0.14f),
                                        interactive = true,
                                        onClick = { onSelectId(item.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .sharedBounds(
                                                sharedContentState = rememberSharedContentState("hero-card-${item.id}"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                                boundsTransform = boundsTransform,
                                                clipInOverlayDuringTransition = OverlayClip(cardShape),
                                            ),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .sharedElement(
                                                            sharedContentState = rememberSharedContentState("hero-icon-${item.id}"),
                                                            animatedVisibilityScope = this@AnimatedContent,
                                                            boundsTransform = boundsTransform,
                                                        )
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(item.tint.copy(alpha = 0.24f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    LiquidKitIcon(
                                                        symbol = item.symbol,
                                                        tint = item.tint,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(99.dp))
                                                        .background(item.tint.copy(alpha = 0.16f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                                ) {
                                                    BasicText(
                                                        text = item.badge,
                                                        style = TextStyle(
                                                            color = item.tint,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                    )
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                BasicText(
                                                    text = item.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = TextStyle(
                                                        color = colors.ink,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    modifier = Modifier.sharedBounds(
                                                        sharedContentState = rememberSharedContentState("hero-title-${item.id}"),
                                                        animatedVisibilityScope = this@AnimatedContent,
                                                        boundsTransform = boundsTransform,
                                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                                    ),
                                                )
                                                BasicText(
                                                    text = item.subtitle,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = TextStyle(
                                                        color = colors.secondary,
                                                        fontSize = 12.sp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size < safeColumns) {
                                    repeat(safeColumns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val detailShape = RoundedCornerShape(28.dp)
                    val idx = items.indexOfFirst { it.id == detail.id }.coerceAtLeast(0)
                    val prevItem = items.getOrNull((idx - 1 + items.size) % items.size)
                    val nextItem = items.getOrNull((idx + 1) % items.size)
                    LiquidKitGlassSurface(
                        backdrop = effectiveBackdrop,
                        shape = detailShape,
                        tint = detail.tint.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 320.dp)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState("hero-card-${detail.id}"),
                                animatedVisibilityScope = this@AnimatedContent,
                                boundsTransform = boundsTransform,
                                clipInOverlayDuringTransition = OverlayClip(detailShape),
                            ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .sharedElement(
                                                sharedContentState = rememberSharedContentState("hero-icon-${detail.id}"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                                boundsTransform = boundsTransform,
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(detail.tint.copy(alpha = 0.28f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LiquidKitIcon(
                                            symbol = detail.symbol,
                                            tint = detail.tint,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        BasicText(
                                            text = detail.title,
                                            style = TextStyle(
                                                color = colors.ink,
                                                fontSize = 21.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            modifier = Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState("hero-title-${detail.id}"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                                boundsTransform = boundsTransform,
                                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                            ),
                                        )
                                        BasicText(
                                            text = detail.subtitle,
                                            style = TextStyle(
                                                color = colors.secondary,
                                                fontSize = 13.sp,
                                            ),
                                        )
                                    }
                                }
                                LiquidKitGlassSurface(
                                    backdrop = effectiveBackdrop,
                                    shape = CircleShape,
                                    interactive = true,
                                    onClick = { onSelectId(null) },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        LiquidKitIcon(
                                            symbol = LiquidKitSymbol.Close,
                                            tint = colors.ink,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(detail.badge, "sharedBounds", "OverlayClip", "LayerBackdrop").forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(detail.tint.copy(alpha = 0.16f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        BasicText(
                                            text = tag,
                                            style = TextStyle(
                                                color = detail.tint,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        )
                                    }
                                }
                            }

                            if (zoomDetailContent != null) {
                                zoomDetailContent(detail)
                            } else {
                                BasicText(
                                    text = detail.summary,
                                    style = TextStyle(
                                        color = colors.ink.copy(alpha = 0.90f),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    ),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (prevItem != null && items.size > 1) {
                                    LiquidKitActionButton(
                                        label = "上一张",
                                        onClick = { onSelectId(prevItem.id) },
                                        leadingSymbol = LiquidKitSymbol.Back,
                                        style = LiquidKitActionStyle.Glass,
                                        controlSize = LiquidKitControlSize.Small,
                                    )
                                }
                                if (nextItem != null && items.size > 1) {
                                    LiquidKitActionButton(
                                        label = "下一张",
                                        onClick = { onSelectId(nextItem.id) },
                                        leadingSymbol = LiquidKitSymbol.Forward,
                                        style = LiquidKitActionStyle.GlassProminent,
                                        tint = detail.tint,
                                        controlSize = LiquidKitControlSize.Small,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Mini-Player capsule <-> Expanded Glass Player deck with shared container, artwork,
 * title, and playback action transitions.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LiquidKitSharedPlayerMorph(
    track: LiquidKitSharedPlayerTrack,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onNextTrack: (() -> Unit)? = null,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
) {
    val spring = LiquidKitSpring.swiftUI(response = 0.34f, dampingFraction = 0.84f)
    val boundsTransform = BoundsTransform { _, _ -> spring.rectSpec }

    SharedTransitionLayout(modifier = modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(170))
            },
            label = "LiquidKitSharedPlayerMorph",
        ) { isExpanded ->
            if (!isExpanded) {
                val capsuleShape = RoundedCornerShape(28.dp)
                LiquidKitGlassSurface(
                    backdrop = backdrop,
                    shape = capsuleShape,
                    tint = track.tint.copy(alpha = 0.14f),
                    interactive = true,
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("player-surface"),
                            animatedVisibilityScope = this@AnimatedContent,
                            boundsTransform = boundsTransform,
                            clipInOverlayDuringTransition = OverlayClip(capsuleShape),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlayerArtworkTile(
                            tint = track.tint,
                            isPlaying = track.isPlaying,
                            modifier = Modifier
                                .size(38.dp)
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState("player-art"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = boundsTransform,
                                ),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            BasicText(
                                text = track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState("player-title"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = boundsTransform,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                ),
                            )
                            BasicText(
                                text = "${track.artist} · ${track.bpm} FPS Target",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState("player-play"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = boundsTransform,
                                )
                                .clip(CircleShape)
                                .background(track.tint.copy(alpha = 0.24f))
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidKitIcon(
                                symbol = if (track.isPlaying) LiquidKitSymbol.Filter else LiquidKitSymbol.Play,
                                tint = track.tint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            } else {
                val deckShape = RoundedCornerShape(30.dp)
                LiquidKitGlassSurface(
                    backdrop = backdrop,
                    shape = deckShape,
                    tint = track.tint.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("player-surface"),
                            animatedVisibilityScope = this@AnimatedContent,
                            boundsTransform = boundsTransform,
                            clipInOverlayDuringTransition = OverlayClip(deckShape),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlayerArtworkTile(
                                tint = track.tint,
                                isPlaying = track.isPlaying,
                                modifier = Modifier
                                    .size(64.dp)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState("player-art"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    ),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                BasicText(
                                    text = track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(
                                        color = colors.ink,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState("player-title"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                    ),
                                )
                                BasicText(
                                    text = "${track.artist} · ${track.bpm} Hz Engine",
                                    style = TextStyle(color = colors.secondary, fontSize = 13.sp),
                                )
                            }
                            LiquidKitGlassSurface(
                                backdrop = backdrop,
                                shape = CircleShape,
                                interactive = true,
                                onClick = { onExpandedChange(false) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    LiquidKitIcon(
                                        symbol = LiquidKitSymbol.ChevronDown,
                                        tint = colors.ink,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        // Harmonic progress / waveform preview bar
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                        ) {
                            val barCount = 24
                            val step = size.width / barCount
                            val activeCutoff = (track.progress.coerceIn(0f, 1f) * barCount).toInt()
                            for (i in 0 until barCount) {
                                val hRatio = 0.28f + 0.65f * kotlin.math.abs(kotlin.math.sin((i + 1) * 0.72f))
                                val barHeight = size.height * hRatio
                                val color = if (i <= activeCutoff) track.tint else colors.secondary.copy(alpha = 0.28f)
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(i * step + 1.5f, (size.height - barHeight) * 0.5f),
                                    size = Size((step - 3f).coerceAtLeast(2f), barHeight),
                                    cornerRadius = CornerRadius(4f, 4f),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                text = "sharedBounds + sharedElement",
                                style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .sharedBounds(
                                            sharedContentState = rememberSharedContentState("player-play"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = boundsTransform,
                                        )
                                        .clip(CircleShape)
                                        .background(track.tint.copy(alpha = 0.28f))
                                        .clickable { onTogglePlay() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LiquidKitIcon(
                                        symbol = if (track.isPlaying) LiquidKitSymbol.Filter else LiquidKitSymbol.Play,
                                        tint = track.tint,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                if (onNextTrack != null) {
                                    LiquidKitActionButton(
                                        label = "切歌",
                                        onClick = onNextTrack,
                                        leadingSymbol = LiquidKitSymbol.Forward,
                                        style = LiquidKitActionStyle.Glass,
                                        controlSize = LiquidKitControlSize.Small,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerArtworkTile(
    tint: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(tint, tint.copy(alpha = 0.55f), Color(0xFF5E5CE6)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidKitIcon(
            symbol = if (isPlaying) LiquidKitSymbol.Sparkles else LiquidKitSymbol.Drop,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Compares Coordinate Bounds Morph ([SharedTransitionLayout] `sharedBounds`) against Optical
 * SDF Liquid Morph ([LiquidMorphContainer]) over a live [LiquidKitLayerBackdrop] refractive grid.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LiquidKitSharedGlassMorph(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    useOpticalSdf: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
) {
    val substrateBackdrop = rememberLiquidKitBackdrop()
    val effectiveBackdrop = rememberCombinedLiquidKitBackdrop(backdrop, substrateBackdrop)
    val isDark = liquidKitAppearance().dark
    val boundsSpring = LiquidKitSpring.swiftUI(response = 0.34f, dampingFraction = 0.80f)
    val boundsTransform = BoundsTransform { _, _ -> boundsSpring.rectSpec }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .liquidKitBackdrop(substrateBackdrop),
        ) {
            drawRect(if (isDark) Color(0xFF111827) else Color(0xFFF0F6FF))
            val stripeStep = 26.dp.toPx()
            val lineColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0A84FF).copy(alpha = 0.10f)
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2f)
                x += stripeStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
                y += stripeStep
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF00C7BE).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.28f, size.height * 0.50f),
                    radius = size.minDimension * 0.58f,
                ),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.28f, size.height * 0.50f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFF375F).copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(size.width * 0.74f, size.height * 0.48f),
                    radius = size.minDimension * 0.50f,
                ),
                radius = size.minDimension * 0.50f,
                center = Offset(size.width * 0.74f, size.height * 0.48f),
            )
        }

        if (useOpticalSdf) {
            LiquidMorphContainer(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                backdrop = effectiveBackdrop,
                collapsedSize = DpSize(156.dp, 48.dp),
                expandedSize = DpSize(304.dp, 128.dp),
                collapsedCornerRadius = 24.dp,
                expandedCornerRadius = 28.dp,
                config = LiquidMorphConfig(
                    alignment = LiquidMorphAlignment.TopTrailing,
                    openResponse = 0.34f,
                    openDamping = 0.80f,
                    closeResponse = 0.26f,
                    closeDamping = 0.86f,
                ),
                collapsedContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LiquidKitIcon(
                            symbol = LiquidKitSymbol.Drop,
                            tint = Color(0xFF00C7BE),
                            modifier = Modifier.size(18.dp),
                        )
                        BasicText(
                            text = "SDF 光学形变",
                            style = TextStyle(
                                color = colors.ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                },
                expandedContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                text = "LiquidMorphContainer · AGSL",
                                style = TextStyle(
                                    color = colors.ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            LiquidKitIcon(
                                symbol = LiquidKitSymbol.Close,
                                tint = colors.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("折射棱镜", "色散边缘", "收起").forEachIndexed { idx, title ->
                                LiquidKitActionButton(
                                    label = title,
                                    onClick = { onExpandedChange(idx != 2) },
                                    style = if (idx == 2) LiquidKitActionStyle.GlassProminent else LiquidKitActionStyle.Glass,
                                    tint = if (idx == 2) Color(0xFF00C7BE) else colors.ink,
                                    controlSize = LiquidKitControlSize.Small,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                },
            )
        } else {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(160)) },
                    label = "LiquidKitSharedBoundsMorph",
                ) { isExpanded ->
                    if (!isExpanded) {
                        val pillShape = RoundedCornerShape(24.dp)
                        LiquidKitGlassSurface(
                            backdrop = effectiveBackdrop,
                            shape = pillShape,
                            tint = Color(0xFF0A84FF).copy(alpha = 0.14f),
                            interactive = true,
                            onClick = { onExpandedChange(true) },
                            modifier = Modifier
                                .size(156.dp, 48.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState("morph-bounds-box"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = boundsTransform,
                                    clipInOverlayDuringTransition = OverlayClip(pillShape),
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LiquidKitIcon(
                                    symbol = LiquidKitSymbol.SquareOnSquare,
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(18.dp),
                                )
                                BasicText(
                                    text = "Bounds 共享",
                                    style = TextStyle(
                                        color = colors.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                    } else {
                        val panelShape = RoundedCornerShape(28.dp)
                        LiquidKitGlassSurface(
                            backdrop = effectiveBackdrop,
                            shape = panelShape,
                            tint = Color(0xFF0A84FF).copy(alpha = 0.16f),
                            modifier = Modifier
                                .size(304.dp, 128.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState("morph-bounds-box"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = boundsTransform,
                                    clipInOverlayDuringTransition = OverlayClip(panelShape),
                                ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BasicText(
                                        text = "Compose sharedBounds",
                                        style = TextStyle(
                                            color = colors.ink,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    LiquidKitIcon(
                                        symbol = LiquidKitSymbol.Close,
                                        tint = colors.secondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onExpandedChange(false) },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf("几何插值", "圆角裁剪", "收起").forEachIndexed { idx, title ->
                                        LiquidKitActionButton(
                                            label = title,
                                            onClick = { onExpandedChange(idx != 2) },
                                            style = if (idx == 2) LiquidKitActionStyle.GlassProminent else LiquidKitActionStyle.Glass,
                                            tint = if (idx == 2) Color(0xFF0A84FF) else colors.ink,
                                            controlSize = LiquidKitControlSize.Small,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
